package br.com.tribia.service.classificacao;

import br.com.tribia.client.llm.LlmException;
import br.com.tribia.dto.ClassificacaoNotaDto;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.Classificacao;
import br.com.tribia.model.ClassificacaoCache;
import br.com.tribia.model.Item;
import br.com.tribia.model.Nota;
import br.com.tribia.model.OrigemClassificacao;
import br.com.tribia.repository.ClassificacaoCacheRepository;
import br.com.tribia.repository.ClassificacaoRepository;
import br.com.tribia.repository.ItemRepository;
import br.com.tribia.repository.NotaRepository;
import br.com.tribia.service.apuracao.ClassificacaoXml;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.util.ChaveClassificacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Classifica os itens de uma nota, nesta ordem:
 * 1. grupo IBS/CBS do XML (só CST + cClassTrib);
 * 2. cache global por NCM + descrição normalizada;
 * 3. IA para o que sobrar (uma chamada por nota, produtos repetidos agrupados);
 * 4. sem IA: respostas gravadas (profile demo) e, por fim, a regra oficial do NCM com confiança baixa.
 * Itens já classificados não são tocados: reclassificar é papel da revisão. Todo código passa pela tabela oficial.
 *
 * Três fases, para a chamada à IA (segundos) não segurar uma transação aberta: XML e cache; IA; gravação.
 */
@Service
public class ClassificacaoService {

    private static final Logger log = LoggerFactory.getLogger(ClassificacaoService.class);

    private final NotaRepository notaRepository;
    private final ItemRepository itemRepository;
    private final ClassificacaoRepository classificacaoRepository;
    private final ClassificacaoCacheRepository cacheRepository;
    private final TabelaCClassTrib tabela;
    private final ClassificadorIa ia;
    private final RespostasGravadasIa respostasGravadas;
    private final ClassificadorPorRegra porRegra;
    private final boolean fallbackRegra;
    private final TransactionTemplate tx;

    public ClassificacaoService(NotaRepository notaRepository, ItemRepository itemRepository,
                                ClassificacaoRepository classificacaoRepository, ClassificacaoCacheRepository cacheRepository,
                                TabelaCClassTrib tabela, ClassificadorIa ia, RespostasGravadasIa respostasGravadas,
                                ClassificadorPorRegra porRegra,
                                @Value("${tribia.classificacao.fallback-regra:true}") boolean fallbackRegra,
                                PlatformTransactionManager transacoes) {
        this.notaRepository = notaRepository;
        this.itemRepository = itemRepository;
        this.classificacaoRepository = classificacaoRepository;
        this.cacheRepository = cacheRepository;
        this.tabela = tabela;
        this.ia = ia;
        this.respostasGravadas = respostasGravadas;
        this.porRegra = porRegra;
        this.fallbackRegra = fallbackRegra;
        this.tx = new TransactionTemplate(transacoes);
    }

    /** Um produto (NCM + descrição) pendente e os itens da nota que o têm. */
    private record Produto(String ncm, String descricao, String unidade, BigDecimal valorUnitario, List<Long> itens) {
    }

    /** Sugestão para um produto, venha de onde vier (IA, resposta gravada ou regra). */
    private record Sugestao(String cst, String cClassTrib, String justificativa, BigDecimal confianca,
                            OrigemClassificacao origem) {
    }

    public ClassificacaoNotaDto classificar(Long notaId) {
        return classificar(notaId, true);
    }

    /**
     * @param usarIa false para só aplicar XML e cache (ex.: seed da inicialização, que nunca chama a IA)
     */
    public ClassificacaoNotaDto classificar(Long notaId, boolean usarIa) {
        // fase 1: XML e cache
        List<Produto> pendentes = tx.execute(s -> aplicarXmlECache(notaId));

        // fase 2: IA (fora de transação) e planos B
        List<String> avisos = new ArrayList<>();
        Map<Produto, Sugestao> sugestoes = usarIa && !pendentes.isEmpty() ? sugerir(pendentes, avisos) : Map.of();

        // fase 3: grava e resume
        return tx.execute(s -> {
            sugestoes.forEach(this::gravarSugestao);
            return resumo(notaId, avisos);
        });
    }

    private List<Produto> aplicarXmlECache(Long notaId) {
        Nota nota = notaRepository.buscarComItens(notaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nota " + notaId + " não encontrada"));
        Map<Long, Classificacao> existentes = classificacoesDe(nota);
        Map<String, Produto> pendentes = new LinkedHashMap<>();
        for (Item item : nota.getItens()) {
            if (existentes.containsKey(item.getId())) {
                continue;
            }
            Optional<Classificacao> nova = doXml(item).or(() -> doCache(item));
            if (nova.isPresent()) {
                classificacaoRepository.save(nova.get());
            } else {
                pendentes.computeIfAbsent(ChaveClassificacao.de(item.getNcm(), item.getDescricao()),
                        k -> new Produto(item.getNcm(), item.getDescricao(), item.getUnidade(), item.getValorUnitario(),
                                new ArrayList<>())).itens().add(item.getId());
            }
        }
        return List.copyOf(pendentes.values());
    }

    /** IA; se ela falhar: respostas gravadas (demo) e, para o que faltar, a regra oficial do NCM. */
    private Map<Produto, Sugestao> sugerir(List<Produto> produtos, List<String> avisos) {
        Map<Produto, Sugestao> sugestoes = new LinkedHashMap<>();
        List<ClassificadorIa.ProdutoParaClassificar> pedido = new ArrayList<>();
        for (int i = 0; i < produtos.size(); i++) {
            Produto p = produtos.get(i);
            pedido.add(new ClassificadorIa.ProdutoParaClassificar(i + 1, p.ncm(), p.descricao(), p.unidade(), p.valorUnitario()));
        }
        try {
            ClassificadorIa.ResultadoIa r = ia.classificar(pedido);
            avisos.addAll(r.avisos());
            r.sugestoes().forEach((n, s) -> sugestoes.put(produtos.get(n - 1),
                    new Sugestao(s.cst(), s.cClassTrib(), s.justificativa(), s.confianca(), OrigemClassificacao.IA)));
            return sugestoes;
        } catch (LlmException e) {
            log.warn("IA não classificou {} produto(s): {}", produtos.size(), e.getMessage());
            avisos.add(e.getMessage());
        }

        int gravadas = 0;
        int porRegraOficial = 0;
        for (Produto p : produtos) {
            Optional<Sugestao> gravada = respostasGravadas.habilitadas()
                    ? respostasGravadas.buscar(p.ncm(), p.descricao())
                    .filter(g -> tabela.validoParaNfe(g.cst(), g.cClassTrib()))
                    .map(g -> new Sugestao(g.cst(), g.cClassTrib(), g.justificativa(), g.confianca(), OrigemClassificacao.IA))
                    : Optional.empty();
            if (gravada.isPresent()) {
                sugestoes.put(p, gravada.get());
                gravadas++;
            } else if (fallbackRegra) {
                var s = porRegra.sugerir(p.ncm());
                sugestoes.put(p, new Sugestao(s.cst(), s.cClassTrib(), s.justificativa(), s.confianca(), OrigemClassificacao.REGRA));
                porRegraOficial++;
            }
        }
        if (gravadas > 0) {
            avisos.add("Modo demonstração: usadas as respostas da IA gravadas antes para " + gravadas + " produto(s).");
        }
        if (porRegraOficial > 0) {
            avisos.add(porRegraOficial + " produto(s) receberam sugestão automática pela regra oficial do NCM, com "
                    + "confiança baixa: confira na revisão.");
        }
        if (sugestoes.size() < produtos.size()) {
            avisos.add((produtos.size() - sugestoes.size()) + " produto(s) ficam pendentes.");
        }
        return sugestoes;
    }

    /** IA, gravada ou regra: nunca nasce aceita. A IA alimenta o cache; a regra não (é só um palpite). */
    private void gravarSugestao(Produto p, Sugestao s) {
        for (Long itemId : p.itens()) {
            if (classificacaoRepository.findByItemId(itemId).isPresent()) {
                continue; // classificado por outra requisição enquanto a IA respondia
            }
            classificacaoRepository.save(nova(itemRepository.getReferenceById(itemId), s.cst(), s.cClassTrib(),
                    s.justificativa(), s.confianca(), s.origem(), false));
        }
        if (s.origem() == OrigemClassificacao.IA) {
            gravarNoCache(p.ncm(), p.descricao(), s.cst(), s.cClassTrib(), s.justificativa(), s.confianca(), "IA", false);
        }
    }

    private Optional<Classificacao> doXml(Item item) {
        return ClassificacaoXml.de(item).flatMap(x -> {
            if (!tabela.validoParaNfe(x.cst(), x.cClassTrib())) {
                log.warn("Item {} da nota {}: CST/cClassTrib {}/{} do XML não está na tabela oficial; ignorado",
                        item.getNItem(), item.getNota().getId(), x.cst(), x.cClassTrib());
                return Optional.empty();
            }
            return Optional.of(nova(item, x.cst(), x.cClassTrib(),
                    "Classificação informada pelo emitente no grupo IBS/CBS da nota.",
                    BigDecimal.ONE, OrigemClassificacao.XML, true));
        });
    }

    private Optional<Classificacao> doCache(Item item) {
        String chave = ChaveClassificacao.de(item.getNcm(), item.getDescricao());
        return cacheRepository.findByChave(chave)
                .filter(c -> tabela.validoParaNfe(c.getCst(), c.getCClassTrib()))
                .map(c -> nova(item, c.getCst(), c.getCClassTrib(), c.getJustificativa(), c.getConfianca(),
                        OrigemClassificacao.CACHE, c.isValidada()));
    }

    private Classificacao nova(Item item, String cst, String cClassTrib, String justificativa, BigDecimal confianca,
                               OrigemClassificacao origem, boolean aceita) {
        Classificacao c = new Classificacao(item);
        c.definir(cst, cClassTrib, tabela.buscar(cClassTrib).orElseThrow().regime(), justificativa, confianca,
                origem, aceita);
        return c;
    }

    /** Grava (ou atualiza) uma entrada do cache global. Usado pelo seed, pela IA e pela revisão. */
    @Transactional
    public void gravarNoCache(String ncm, String descricao, String cst, String cClassTrib, String justificativa,
                              BigDecimal confianca, String fonte, boolean validada) {
        if (!tabela.validoParaNfe(cst, cClassTrib)) {
            throw new IllegalArgumentException("CST/cClassTrib fora da tabela oficial: " + cst + "/" + cClassTrib);
        }
        String chave = ChaveClassificacao.de(ncm, descricao);
        ClassificacaoCache e = cacheRepository.findByChave(chave).orElseGet(() -> new ClassificacaoCache(chave, ncm));
        e.definir(cst, cClassTrib, justificativa, confianca, fonte, validada);
        cacheRepository.save(e);
    }

    /**
     * A pessoa confirmou uma sugestão: a entrada do cache para o produto (se tiver o mesmo código) passa a ser
     * validada, e as próximas notas com esse produto já chegam aceitas.
     */
    @Transactional
    public void validarNoCache(String ncm, String descricao, String cst, String cClassTrib) {
        cacheRepository.findByChave(ChaveClassificacao.de(ncm, descricao))
                .filter(e -> e.getCst().equals(cst) && e.getCClassTrib().equals(cClassTrib))
                .ifPresent(e -> e.definir(cst, cClassTrib, e.getJustificativa(), e.getConfianca(), e.getFonte(), true));
    }

    private Map<Long, Classificacao> classificacoesDe(Nota nota) {
        return classificacaoRepository.findByItemIdIn(nota.getItens().stream().map(Item::getId).toList()).stream()
                .collect(Collectors.toMap(c -> c.getItem().getId(), Function.identity()));
    }

    private ClassificacaoNotaDto resumo(Long notaId, List<String> avisos) {
        Nota nota = notaRepository.buscarComItens(notaId).orElseThrow();
        Map<Long, Classificacao> classificacoes = classificacoesDe(nota);
        Map<OrigemClassificacao, Long> porOrigem = new EnumMap<>(OrigemClassificacao.class);
        classificacoes.values().forEach(c -> porOrigem.merge(c.getOrigem(), 1L, Long::sum));
        List<Integer> pendentes = new ArrayList<>();
        for (Item i : nota.getItens()) {
            if (!classificacoes.containsKey(i.getId())) {
                pendentes.add(i.getNItem());
            }
        }
        return new ClassificacaoNotaDto(nota.getId(), nota.getItens().size(), classificacoes.size(), porOrigem,
                pendentes, List.copyOf(avisos), null);
    }
}

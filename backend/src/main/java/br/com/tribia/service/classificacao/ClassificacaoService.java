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
import br.com.tribia.repository.NotaRepository;
import br.com.tribia.service.apuracao.ClassificacaoXml;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.util.ChaveClassificacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 3. IA para o que sobrar (uma chamada por nota, produtos repetidos agrupados).
 * Itens já classificados não são tocados: reclassificar é papel da revisão (correção manual).
 * Todo código passa pela tabela oficial: o que não estiver nela é ignorado ou rejeitado.
 */
@Service
public class ClassificacaoService {

    private static final Logger log = LoggerFactory.getLogger(ClassificacaoService.class);

    private final NotaRepository notaRepository;
    private final ClassificacaoRepository classificacaoRepository;
    private final ClassificacaoCacheRepository cacheRepository;
    private final TabelaCClassTrib tabela;
    private final ClassificadorIa ia;
    private final RespostasGravadasIa respostasGravadas;

    public ClassificacaoService(NotaRepository notaRepository, ClassificacaoRepository classificacaoRepository,
                                ClassificacaoCacheRepository cacheRepository, TabelaCClassTrib tabela,
                                ClassificadorIa ia, RespostasGravadasIa respostasGravadas) {
        this.notaRepository = notaRepository;
        this.classificacaoRepository = classificacaoRepository;
        this.cacheRepository = cacheRepository;
        this.tabela = tabela;
        this.ia = ia;
        this.respostasGravadas = respostasGravadas;
    }

    /**
     * Classifica os itens ainda sem classificação: XML, depois cache e, para o que sobrar, IA.
     *
     * @param usarIa false para só aplicar XML e cache (ex.: seed da inicialização, que nunca chama a IA)
     */
    @Transactional
    public ClassificacaoNotaDto classificar(Long notaId, boolean usarIa) {
        Nota nota = notaRepository.buscarComItens(notaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nota " + notaId + " não encontrada"));
        Map<Long, Classificacao> existentes = classificacaoRepository
                .findByItemIdIn(nota.getItens().stream().map(Item::getId).toList()).stream()
                .collect(Collectors.toMap(c -> c.getItem().getId(), Function.identity()));

        List<Item> pendentes = new ArrayList<>();
        for (Item item : nota.getItens()) {
            if (existentes.containsKey(item.getId())) {
                continue;
            }
            Optional<Classificacao> nova = doXml(item).or(() -> doCache(item));
            if (nova.isPresent()) {
                existentes.put(item.getId(), classificacaoRepository.save(nova.get()));
            } else {
                pendentes.add(item);
            }
        }

        List<String> avisos = new ArrayList<>();
        if (usarIa && !pendentes.isEmpty()) {
            // A chamada à IA fica dentro da transação (poucos segundos). Aceitável no MVP, com H2 em memória.
            classificarComIa(pendentes, existentes, avisos);
        }
        return resumo(nota, existentes, avisos);
    }

    @Transactional
    public ClassificacaoNotaDto classificar(Long notaId) {
        return classificar(notaId, true);
    }

    /**
     * Agrupa os itens pelo produto (NCM + descrição normalizada) para a IA classificar cada produto uma vez só
     * e grava o resultado no cache global. A IA nunca aceita sozinha: a classificação nasce com aceita=false.
     */
    private void classificarComIa(List<Item> pendentes, Map<Long, Classificacao> existentes, List<String> avisos) {
        Map<String, List<Item>> porProduto = new LinkedHashMap<>();
        for (Item i : pendentes) {
            porProduto.computeIfAbsent(ChaveClassificacao.de(i.getNcm(), i.getDescricao()), k -> new ArrayList<>()).add(i);
        }
        List<ClassificadorIa.ProdutoParaClassificar> pedido = new ArrayList<>();
        Map<Integer, List<Item>> grupos = new LinkedHashMap<>();
        int id = 1;
        for (List<Item> grupo : porProduto.values()) {
            Item rep = grupo.get(0);
            pedido.add(new ClassificadorIa.ProdutoParaClassificar(id, rep.getNcm(), rep.getDescricao(), rep.getUnidade(),
                    rep.getValorUnitario()));
            grupos.put(id++, grupo);
        }

        ClassificadorIa.ResultadoIa r;
        try {
            r = ia.classificar(pedido);
        } catch (LlmException e) {
            log.warn("IA não classificou {} produto(s): {}", pedido.size(), e.getMessage());
            if (!respostasGravadas.habilitadas()) {
                avisos.add(e.getMessage() + " Os itens ficam pendentes.");
                return;
            }
            r = respostasGravadas(pedido, e, avisos);
        }
        avisos.addAll(r.avisos());
        r.sugestoes().forEach((idProduto, s) -> {
            List<Item> grupo = grupos.get(idProduto);
            for (Item item : grupo) {
                Classificacao c = nova(item, s.cst(), s.cClassTrib(), s.justificativa(), s.confianca(),
                        OrigemClassificacao.IA, false);
                existentes.put(item.getId(), classificacaoRepository.save(c));
            }
            Item rep = grupo.get(0);
            gravarNoCache(rep.getNcm(), rep.getDescricao(), s.cst(), s.cClassTrib(), s.justificativa(), s.confianca(),
                    "IA", false);
        });
    }

    /** Plano B do profile demo: respostas que a IA real deu antes para estes produtos (ver RespostasGravadasIa). */
    private ClassificadorIa.ResultadoIa respostasGravadas(List<ClassificadorIa.ProdutoParaClassificar> pedido,
                                                          LlmException falha, List<String> avisos) {
        Map<Integer, ClassificadorIa.SugestaoIa> sugestoes = new LinkedHashMap<>();
        List<String> semResposta = new ArrayList<>();
        for (ClassificadorIa.ProdutoParaClassificar p : pedido) {
            respostasGravadas.buscar(p.ncm(), p.descricao())
                    .filter(g -> tabela.validoParaNfe(g.cst(), g.cClassTrib()))
                    .ifPresentOrElse(
                            g -> sugestoes.put(p.nItem(), new ClassificadorIa.SugestaoIa(p.nItem(), g.cst(), g.cClassTrib(),
                                    g.justificativa(), g.confianca())),
                            () -> semResposta.add(p.descricao()));
        }
        avisos.add(falha.getMessage() + " Modo demonstração: usadas as respostas da IA gravadas antes para "
                + sugestoes.size() + " produto(s).");
        List<String> outros = new ArrayList<>();
        if (!semResposta.isEmpty()) {
            outros.add("Sem resposta gravada para: " + String.join(", ", semResposta) + ". Esses itens ficam pendentes.");
        }
        return new ClassificadorIa.ResultadoIa(sugestoes, outros);
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

    /** Grava (ou atualiza) uma entrada do cache global. Usado pelo seed e, depois, pela IA e pela revisão. */
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

    private static ClassificacaoNotaDto resumo(Nota nota, Map<Long, Classificacao> classificacoes, List<String> avisos) {
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

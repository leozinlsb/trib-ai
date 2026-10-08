package br.com.tribia.service.classificacao;

import br.com.tribia.dto.CalculoDto;
import br.com.tribia.dto.ClassificacaoDto;
import br.com.tribia.dto.ItemDto;
import br.com.tribia.dto.OpcaoClassificacaoDto;
import br.com.tribia.dto.RevisaoDto;
import br.com.tribia.dto.RevisaoDto.ItemRevisaoDto;
import br.com.tribia.dto.RevisaoResultadoDto;
import br.com.tribia.dto.RevisarItemRequest;
import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.Calculo;
import br.com.tribia.model.Classificacao;
import br.com.tribia.model.Item;
import br.com.tribia.model.Nota;
import br.com.tribia.model.TipoNota;
import br.com.tribia.repository.CalculoRepository;
import br.com.tribia.repository.ClassificacaoRepository;
import br.com.tribia.repository.ItemRepository;
import br.com.tribia.security.AcessoService;
import br.com.tribia.service.ClienteService;
import br.com.tribia.service.calculo.CalculoService;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaCClassTrib.CClassTrib;
import br.com.tribia.service.tabelas.TabelaNcmAplicavel;
import br.com.tribia.util.ChaveClassificacao;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Revisão humana das classificações (adendo, etapa 6): lista o que precisa de atenção e aplica aceite, correção
 * ou "uso e consumo". Toda mudança recalcula as notas afetadas, para o painel refletir na hora.
 */
@Service
public class RevisaoService {

    private final ClienteService clienteService;
    private final ItemRepository itemRepository;
    private final ClassificacaoRepository classificacaoRepository;
    private final CalculoRepository calculoRepository;
    private final ClassificacaoService classificacaoService;
    private final CalculoService calculoService;
    private final CriterioRevisao criterio;
    private final TabelaCClassTrib tabela;
    private final TabelaNcmAplicavel regrasNcm;
    private final AcessoService acesso;

    public RevisaoService(ClienteService clienteService, ItemRepository itemRepository,
                          ClassificacaoRepository classificacaoRepository, CalculoRepository calculoRepository,
                          ClassificacaoService classificacaoService, CalculoService calculoService,
                          CriterioRevisao criterio, TabelaCClassTrib tabela, TabelaNcmAplicavel regrasNcm,
                          AcessoService acesso) {
        this.clienteService = clienteService;
        this.itemRepository = itemRepository;
        this.classificacaoRepository = classificacaoRepository;
        this.calculoRepository = calculoRepository;
        this.classificacaoService = classificacaoService;
        this.calculoService = calculoService;
        this.criterio = criterio;
        this.tabela = tabela;
        this.regrasNcm = regrasNcm;
        this.acesso = acesso;
    }

    /** Itens do cliente que precisam de revisão: primeiro os sem classificação, depois os de menor confiança. */
    @Transactional(readOnly = true)
    public RevisaoDto listar(Long clienteId, String de, String ate) {
        clienteService.buscar(clienteId);
        List<Item> itens = itemRepository.doClienteNoPeriodo(clienteId, de, ate);
        Map<Long, Classificacao> classificacoes = classificacoes(itens);

        List<ItemRevisaoDto> pendentes = new ArrayList<>();
        for (Item i : itens) {
            Classificacao c = classificacoes.get(i.getId());
            var motivos = criterio.motivos(c);
            if (motivos.isEmpty()) {
                continue;
            }
            Nota n = i.getNota();
            pendentes.add(new ItemRevisaoDto(i.getId(), n.getId(), n.getNumero(), n.getTipo(), n.getCompetencia(),
                    n.getContraparteNome(), i.getNItem(), i.getCodigo(), i.getDescricao(), i.getNcm(), i.getValorTotal(),
                    i.isCreditavel(), ClassificacaoDto.de(c, tabela), motivos, opcoes(i.getNcm(), true)));
        }
        pendentes.sort(Comparator
                .comparing((ItemRevisaoDto r) -> r.classificacao() != null)
                .thenComparing(r -> r.classificacao() == null ? BigDecimal.ZERO : r.classificacao().confianca())
                .thenComparing(ItemRevisaoDto::valorTotal, Comparator.reverseOrder()));
        return new RevisaoDto(clienteId, criterio.confiancaMinima(), pendentes.size(), pendentes);
    }

    /**
     * Opções de classificação para NF-e. Com NCM, as que a lista oficial associa a ele vêm primeiro e marcadas.
     *
     * @param soSugeridas true devolve apenas as associadas ao NCM
     */
    public List<OpcaoClassificacaoDto> opcoes(String ncm, boolean soSugeridas) {
        acesso.atual();
        Set<String> sugeridas = regrasNcm.regrasPara(ncm).stream().map(TabelaNcmAplicavel.Regra::cClassTrib)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return tabela.opcoesNfe().stream()
                .filter(c -> !soSugeridas || sugeridas.contains(c.codigo()))
                .map(c -> OpcaoClassificacaoDto.de(c, regrasNcm.exigeNcmNaLista(c.codigo()), sugeridas.contains(c.codigo())))
                .sorted(Comparator.comparing((OpcaoClassificacaoDto o) -> !o.sugeridaPeloNcm()))
                .toList();
    }

    @Transactional
    public RevisaoResultadoDto revisar(Long itemId, RevisarItemRequest req) {
        acesso.itemAcessivel(itemId);
        if (req == null || req.vazio()) {
            throw ApiException.requisicaoInvalida("Informe ao menos um de: aceitar, cClassTrib, creditavel.");
        }
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item " + itemId + " não encontrado"));
        Nota nota = item.getNota();
        List<String> avisos = new ArrayList<>();
        Set<Long> notasAfetadas = new LinkedHashSet<>();
        notasAfetadas.add(nota.getId());
        boolean aplicarAosIguais = !Boolean.FALSE.equals(req.aplicarAosIguais());
        List<Item> alvos = List.of(item);

        if (req.cClassTrib() != null && !req.cClassTrib().isBlank()) {
            alvos = corrigir(item, req, aplicarAosIguais, avisos);
        } else if (Boolean.TRUE.equals(req.aceitar())) {
            alvos = aceitar(item, aplicarAosIguais);
        }

        if (req.creditavel() != null) {
            if (nota.getTipo() != TipoNota.ENTRADA) {
                throw invalida("Só itens de notas de entrada geram crédito: 'creditavel' não se aplica a uma venda.");
            }
            item.setCreditavel(req.creditavel());
        }

        alvos.forEach(a -> notasAfetadas.add(a.getNota().getId()));
        for (Long notaId : notasAfetadas) {
            // avisa na própria resposta quando o crédito de uma compra ficou limitado pela divergência com a nota (R2)
            calculoService.recalcular(notaId).avisos().stream()
                    .filter(v -> v.startsWith(CalculoService.AVISO_CREDITO_DIVERGENTE))
                    .filter(v -> !avisos.contains(v))
                    .forEach(avisos::add);
        }

        Classificacao c = classificacaoRepository.findByItemId(itemId).orElse(null);
        Calculo calc = calculoRepository.findByItemIdIn(List.of(itemId)).stream().findFirst().orElse(null);
        return new RevisaoResultadoDto(ItemDto.de(item, ClassificacaoDto.de(c, tabela), CalculoDto.de(calc)),
                alvos.size(), List.copyOf(notasAfetadas), avisos);
    }

    private List<Item> corrigir(Item item, RevisarItemRequest req, boolean aplicarAosIguais, List<String> avisos) {
        String codigo = req.cClassTrib().trim();
        CClassTrib oficial = tabela.buscar(codigo)
                .filter(c -> tabela.opcaoNfe(c.codigo()))
                .orElseThrow(() -> invalida("cClassTrib " + codigo + " não é uma opção válida para NF-e na tabela oficial."));
        String cst = req.cst() == null || req.cst().isBlank() ? oficial.cst() : req.cst().trim();
        if (!cst.equals(oficial.cst())) {
            throw invalida("O cClassTrib " + codigo + " pertence ao CST " + oficial.cst() + ", não ao " + cst + ".");
        }
        if (regrasNcm.exigeNcmNaLista(codigo) && !regrasNcm.cobre(item.getNcm(), codigo)) {
            avisos.add("Atenção: o cClassTrib " + codigo + " é um benefício de anexo e o NCM " + item.getNcm()
                    + " não consta da lista oficial dele. A correção foi gravada como você pediu.");
        }
        String justificativa = req.justificativa() == null || req.justificativa().isBlank()
                ? "Corrigida manualmente na revisão." : req.justificativa().trim();

        List<Item> alvos = aplicarAosIguais ? iguaisNaoRevisados(item, c -> true) : List.of(item);
        Map<Long, Classificacao> existentes = classificacoes(alvos);
        for (Item alvo : alvos) {
            Classificacao c = existentes.getOrDefault(alvo.getId(), new Classificacao(alvo));
            c.corrigirNaRevisao(cst, codigo, oficial.regime(), justificativa);
            classificacaoRepository.save(c);
        }
        classificacaoService.gravarNoCacheDoItem(item.getId(), cst, codigo, justificativa);
        return alvos;
    }

    private List<Item> aceitar(Item item, boolean aplicarAosIguais) {
        Classificacao atual = classificacaoRepository.findByItemId(item.getId())
                .orElseThrow(() -> invalida("O item ainda não tem classificação para aceitar: informe o cClassTrib."));
        List<Item> alvos = aplicarAosIguais
                ? iguaisNaoRevisados(item, c -> c != null && c.getCClassTrib().equals(atual.getCClassTrib())
                && c.getCst().equals(atual.getCst()))
                : List.of(item);
        Map<Long, Classificacao> existentes = classificacoes(alvos);
        for (Item alvo : alvos) {
            existentes.get(alvo.getId()).aceitarNaRevisao();
        }
        classificacaoService.validarNoCacheDoItem(item.getId(), atual.getCst(), atual.getCClassTrib());
        return alvos;
    }

    /**
     * O próprio item mais os itens do mesmo cliente com o mesmo produto (NCM + descrição normalizada) que ainda
     * não foram revisados por uma pessoa e passam no filtro.
     */
    private List<Item> iguaisNaoRevisados(Item item, java.util.function.Predicate<Classificacao> filtro) {
        String chave = ChaveClassificacao.de(item.getNcm(), item.getDescricao());
        List<Item> candidatos = itemRepository.doClienteNoPeriodo(item.getNota().getCliente().getId(), null, null).stream()
                .filter(i -> i.getId().equals(item.getId()) || chave.equals(ChaveClassificacao.de(i.getNcm(), i.getDescricao())))
                .toList();
        Map<Long, Classificacao> cls = classificacoes(candidatos);
        return candidatos.stream()
                .filter(i -> i.getId().equals(item.getId())
                        || ((cls.get(i.getId()) == null || !cls.get(i.getId()).isRevisada()) && filtro.test(cls.get(i.getId()))))
                .toList();
    }

    private Map<Long, Classificacao> classificacoes(List<Item> itens) {
        return classificacaoRepository.findByItemIdIn(itens.stream().map(Item::getId).toList()).stream()
                .collect(Collectors.toMap(c -> c.getItem().getId(), Function.identity()));
    }

    private static ApiException invalida(String mensagem) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Revisão inválida", mensagem);
    }
}

package br.com.tribia.apipublica.servico;

import br.com.tribia.apipublica.dto.ApiPublicaDtos.PaginaPublica;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.Apuracao;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.CalculoItem;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.ClassificacaoItem;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.Comparativo;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.ComparativoEmpresa;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.Documento;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.EnvioNota;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.Erro;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.Indicadores;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.ItemNota;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.Mes;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.NotaPublica;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.ResumoNota;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.SituacaoClassificacao;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.StatusNota;
import br.com.tribia.apipublica.model.EnvioNotaApi;
import br.com.tribia.apipublica.repository.ChaveApiRepository;
import br.com.tribia.apipublica.repository.EnvioNotaApiRepository;
import br.com.tribia.apipublica.seguranca.ChavesApi;
import br.com.tribia.apipublica.seguranca.IntegradorAutenticado;
import br.com.tribia.apipublica.web.ApiPublicaException;
import br.com.tribia.dto.ClassificacaoDto;
import br.com.tribia.dto.ClassificacaoNotaDto;
import br.com.tribia.dto.ComparativoDto;
import br.com.tribia.dto.DashboardDto;
import br.com.tribia.dto.ItemDto;
import br.com.tribia.dto.NotaDetalheDto;
import br.com.tribia.dto.ResumoNotaDto;
import br.com.tribia.exception.ApiException;
import br.com.tribia.model.Nota;
import br.com.tribia.model.OrigemClassificacao;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.security.EscopoIntegracao;
import br.com.tribia.service.DashboardService;
import br.com.tribia.service.NotaService;
import br.com.tribia.service.classificacao.ClassificacaoService;
import br.com.tribia.service.classificacao.CriterioRevisao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * API pública (fase 1): envio de NF-e, consulta da nota classificada e calculada, listagem e comparativo da empresa.
 * Só expõe o que a plataforma já faz, pelos mesmos serviços (NotaService, ClassificacaoService, CalculoService,
 * DashboardService), sempre dentro de um {@link EscopoIntegracao} preso à empresa da chave.
 *
 * <ul>
 *   <li><b>Envio:</b> importa e aplica XML + cache na requisição (sem IA), conta os itens que sobraram para a IA e
 *       confere a cota diária de itens da chave; classificação por IA e cálculo seguem em segundo plano.</li>
 *   <li><b>Cota de IA:</b> se os itens não cabem no que resta da cota do dia, a nota é importada e calculada com o que o
 *       XML e o cache resolveram, e os demais ficam para a revisão na plataforma (aviso na resposta). Cota esgotada
 *       antes do envio: 429, nada é gravado.</li>
 *   <li><b>Idempotência:</b> (chave, Idempotency-Key) com hash do corpo, como nas análises.</li>
 *   <li><b>Natureza:</b> todo valor de 2027 sai marcado como projeção pendente de validação fiscal.</li>
 * </ul>
 */
@Service
public class ApiPublicaNotasService {

    private static final Logger log = LoggerFactory.getLogger(ApiPublicaNotasService.class);
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_.:-]{1,100}$");
    private static final Pattern UUID_TEXTO = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern COMPETENCIA = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final TypeReference<List<String>> LISTA_TEXTOS = new TypeReference<>() {
    };

    /** Avisos fixos de toda resposta com valores de 2027 (o site mostra os mesmos na tela). */
    static final List<String> AVISOS_PROJECAO = List.of(
            "Valores de 2027 são projeção pendente de validação fiscal: alíquota da CBS estimada e base sem ICMS, PIS e "
                    + "Cofins (hipótese a confirmar com especialista). Não são apuração definitiva.",
            "Classificações automáticas (IA, cache, regra) são sugestões até uma pessoa revisar na plataforma.",
            "O comparativo cobre PIS/Cofins hoje x CBS/IBS/IS em 2027; o ICMS não muda em 2027 e fica fora.");

    private final EnvioNotaApiRepository envios;
    private final ChaveApiRepository chaves;
    private final ClienteRepository clientes;
    private final NotaService notas;
    private final ClassificacaoService classificacao;
    private final DashboardService painel;
    private final CriterioRevisao criterio;
    private final ProcessadorEnvioNota processador;
    private final TaskExecutor executor;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final ConsumoIaApi consumo;

    public ApiPublicaNotasService(EnvioNotaApiRepository envios, ChaveApiRepository chaves, ClienteRepository clientes,
                                  NotaService notas, ClassificacaoService classificacao, DashboardService painel,
                                  CriterioRevisao criterio, ProcessadorEnvioNota processador,
                                  @Qualifier("notasApiExecutor") TaskExecutor executor, ObjectMapper json,
                                  PlatformTransactionManager transacoes, ConsumoIaApi consumo) {
        this.envios = envios;
        this.chaves = chaves;
        this.clientes = clientes;
        this.notas = notas;
        this.classificacao = classificacao;
        this.painel = painel;
        this.criterio = criterio;
        this.processador = processador;
        this.executor = executor;
        this.json = json;
        this.tx = new TransactionTemplate(transacoes);
        this.consumo = consumo;
    }

    public record Envio(NotaPublica nota, boolean repetida) {
    }

    // ---------------- envio ----------------

    public Envio enviar(IntegradorAutenticado quem, String idempotencyKey, EnvioNota pedido) {
        String chaveIdem = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (chaveIdem != null && !IDEMPOTENCY_KEY.matcher(chaveIdem).matches()) {
            throw new ApiPublicaException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALIDA", "Idempotency-Key inválida",
                    "Idempotency-Key deve ter de 1 a 100 caracteres [A-Za-z0-9_.:-] (um UUID serve).");
        }
        String referencia = pedido.referenciaExterna() == null || pedido.referenciaExterna().isBlank() ? null
                : pedido.referenciaExterna().trim();
        String xml = pedido.xml().trim();
        String hash = ChavesApi.sha256((referencia == null ? "" : referencia) + "\n" + xml);

        EnvioNotaApi criado;
        // trava compartilhada com a classificação avulsa: as duas gastam a mesma cota de itens para a IA
        synchronized (consumo.trava(quem.chaveId())) {
            if (chaveIdem != null) {
                Optional<EnvioNotaApi> existente = tx.execute(s -> envios.buscarPorIdempotencia(quem.chaveId(), chaveIdem));
                if (existente.isPresent()) {
                    return new Envio(repeticao(quem, existente.get(), hash), true);
                }
            }
            LocalDate hoje = LocalDate.now(BRASILIA);
            long itensHoje = verificarConsumo(quem, hoje);
            try {
                criado = tx.execute(s -> EscopoIntegracao.executar(quem.clienteId(),
                        () -> importar(quem, chaveIdem, hash, referencia, xml, itensHoje)));
            } catch (DataIntegrityViolationException e) {
                Optional<EnvioNotaApi> vencedor = chaveIdem == null ? Optional.empty()
                        : tx.execute(s -> envios.buscarPorIdempotencia(quem.chaveId(), chaveIdem));
                if (vencedor.isPresent()) {
                    return new Envio(repeticao(quem, vencedor.get(), hash), true);
                }
                throw e;
            } catch (ApiException e) {
                throw rejeicao(e);
            }
        }
        log.info("API pública: nota recebida no envio {} (chave {}, itens para a IA: {})", criado.getPublicoId(),
                quem.prefixo(), criado.getItensIa());
        try {
            Long id = criado.getId();
            executor.execute(() -> processador.processar(id));
        } catch (TaskRejectedException e) {
            tx.executeWithoutResult(s -> envios.findById(criado.getId()).ifPresent(x -> x.falhar(
                    "Fila do servidor cheia: a nota foi importada, mas não processada. Processe-a na plataforma.",
                    Instant.now())));
            throw new ApiPublicaException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICO_OCUPADO", "Serviço ocupado",
                    "Muitas notas em processamento no servidor. A nota do envio " + criado.getPublicoId()
                            + " foi importada, mas ficou como FALHOU; processe-a na plataforma.",
                    Map.of("notaId", criado.getPublicoId()), Map.of("Retry-After", "30"));
        }
        return new Envio(consultar(quem, criado.getPublicoId()), false);
    }

    /** Na transação e no escopo da empresa: importa, aplica XML + cache e reserva os itens que irão para a IA. */
    private EnvioNotaApi importar(IntegradorAutenticado quem, String chaveIdem, String hash, String referencia,
                                  String xml, long itensHoje) {
        Nota nota = notas.importar(quem.clienteId(), xml.getBytes(StandardCharsets.UTF_8));
        ClassificacaoNotaDto semIa = classificacao.classificar(nota.getId(), false);
        int paraIa = semIa.pendentes().size();
        boolean usarIa = paraIa > 0 && itensHoje + paraIa <= quem.cotaDiariaItensIa();
        return envios.saveAndFlush(new EnvioNotaApi(UUID.randomUUID().toString(),
                chaves.getReferenceById(quem.chaveId()), clientes.getReferenceById(quem.clienteId()), nota, chaveIdem,
                hash, referencia, usarIa ? paraIa : 0, usarIa, Instant.now()));
    }

    /** @return itens já enviados à IA hoje por esta chave */
    private long verificarConsumo(IntegradorAutenticado quem, LocalDate hoje) {
        long itensHoje = consumo.itensHoje(quem.chaveId());
        if (itensHoje >= quem.cotaDiariaItensIa()) {
            long segundos = java.time.Duration.between(Instant.now(), inicioDoDia(hoje.plusDays(1))).toSeconds();
            throw new ApiPublicaException(HttpStatus.TOO_MANY_REQUESTS, "COTA_DIARIA_ITENS_IA_EXCEDIDA",
                    "Cota diária de itens para a IA excedida",
                    "Esta chave já enviou " + itensHoje + " itens para a IA hoje (cota: " + quem.cotaDiariaItensIa()
                            + "). A cota renova à meia-noite (horário de Brasília).",
                    Map.of(), Map.of("Retry-After", String.valueOf(Math.max(1, segundos))));
        }
        long emProcessamento = tx.execute(s -> envios.contarEmProcessamento(quem.chaveId()));
        if (emProcessamento >= quem.maxAnalisesSimultaneas()) {
            throw new ApiPublicaException(HttpStatus.TOO_MANY_REQUESTS, "LIMITE_NOTAS_SIMULTANEAS",
                    "Muitas notas em processamento",
                    "Esta chave já tem " + emProcessamento + " notas em processamento (limite: "
                            + quem.maxAnalisesSimultaneas() + "). Aguarde alguma terminar.",
                    Map.of(), Map.of("Retry-After", "10"));
        }
        return itensHoje;
    }

    /** Recusas do importador da plataforma, com códigos estáveis para o integrador. */
    private static ApiPublicaException rejeicao(ApiException e) {
        if (e.getStatus() == HttpStatus.CONFLICT && "Nota rejeitada".equals(e.getTitulo())) {
            return new ApiPublicaException(HttpStatus.CONFLICT, "NOTA_JA_IMPORTADA", "Nota já importada", e.getMessage());
        }
        if (e.getStatus() == HttpStatus.UNPROCESSABLE_ENTITY) {
            return new ApiPublicaException(HttpStatus.UNPROCESSABLE_ENTITY, "NOTA_INVALIDA", "Nota rejeitada",
                    e.getMessage());
        }
        if (e.getStatus() == HttpStatus.CONFLICT) {
            return new ApiPublicaException(HttpStatus.FORBIDDEN, "EMPRESA_DESATIVADA", "Empresa desativada",
                    "A empresa desta chave está desativada no TribIA.");
        }
        return new ApiPublicaException(e.getStatus(), "REQUISICAO_INVALIDA", e.getTitulo(), e.getMessage());
    }

    private NotaPublica repeticao(IntegradorAutenticado quem, EnvioNotaApi existente, String hash) {
        if (!existente.getHashPayload().equals(hash)) {
            throw new ApiPublicaException(HttpStatus.CONFLICT, "IDEMPOTENCIA_CONFLITO", "Idempotency-Key já usada",
                    "Esta Idempotency-Key já foi usada com outro conteúdo. Use uma chave nova para um envio novo.",
                    Map.of("notaId", existente.getPublicoId()), Map.of());
        }
        return consultar(quem, existente.getPublicoId());
    }

    // ---------------- consulta ----------------

    public NotaPublica consultar(IntegradorAutenticado quem, String id) {
        String publico = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (!UUID_TEXTO.matcher(publico).matches()) {
            throw naoEncontrada();
        }
        record Lido(EnvioNotaApi envio, Documento documento) {
        }
        Lido lido = tx.execute(s -> envios.buscarDaEmpresa(publico, quem.clienteId())
                .map(e -> new Lido(e, documento(e.getNota())))
                .orElse(null));
        if (lido == null) {
            throw naoEncontrada();
        }
        EnvioNotaApi e = lido.envio();
        StatusNota status = StatusNota.valueOf(e.getStatus().name());
        if (e.getStatus() != EnvioNotaApi.Status.CONCLUIDA) {
            Erro erro = e.getStatus() == EnvioNotaApi.Status.FALHOU ? new Erro("PROCESSAMENTO_FALHOU", e.getMensagem()) : null;
            return new NotaPublica(e.getPublicoId(), e.getReferenciaExterna(), status,
                    e.getStatus() == EnvioNotaApi.Status.FALHOU, e.getCriadoEm(), e.getFinalizadoEm(),
                    ApiPublicaNotasDtos.NATUREZA_PROJECAO, lido.documento(), List.of(), null, 0, 0, erro,
                    AVISOS_PROJECAO);
        }
        Long notaId = tx.execute(s -> envios.findById(e.getId()).orElseThrow().getNota().getId());
        record Leitura(NotaDetalheDto detalhe, ResumoNotaDto resumo) {
        }
        Leitura l;
        try {
            l = EscopoIntegracao.executar(quem.clienteId(),
                    () -> new Leitura(notas.detalhar(notaId), painel.resumoDaNota(notaId)));
        } catch (ApiException ex) {
            // segunda barreira: o AcessoService recusou a nota para a empresa da chave
            if (ex.getStatus() == HttpStatus.NOT_FOUND) {
                throw naoEncontrada();
            }
            throw ex;
        }
        List<ItemNota> itens = l.detalhe().itens().stream().map(this::item).toList();
        int porIa = (int) l.detalhe().itens().stream()
                .filter(i -> i.classificacao() != null && i.classificacao().origem() == OrigemClassificacao.IA).count();
        List<String> avisos = new ArrayList<>(AVISOS_PROJECAO);
        ler(e.getAvisosJson()).stream().filter(a -> !avisos.contains(a)).forEach(avisos::add);
        return new NotaPublica(e.getPublicoId(), e.getReferenciaExterna(), status, true, e.getCriadoEm(),
                e.getFinalizadoEm(), ApiPublicaNotasDtos.NATUREZA_PROJECAO, lido.documento(), itens,
                comparativo(l.resumo().comparativo()), l.resumo().pendentesRevisao(), porIa, null, avisos);
    }

    public PaginaPublica<ResumoNota> listar(IntegradorAutenticado quem, String referenciaExterna, Integer pagina,
                                            Integer tamanho) {
        int p = pagina == null ? 0 : pagina;
        int t = tamanho == null ? 20 : tamanho;
        if (p < 0 || p > 10_000 || t < 1 || t > 100) {
            throw new ApiPublicaException(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "Parâmetro inválido",
                    "pagina deve estar entre 0 e 10000 e tamanho entre 1 e 100.");
        }
        String ref = referenciaExterna == null || referenciaExterna.isBlank() ? null : referenciaExterna.trim();
        return tx.execute(s -> {
            Page<EnvioNotaApi> r = envios.listarDaEmpresa(quem.clienteId(), ref,
                    PageRequest.of(p, t, Sort.by(Sort.Direction.DESC, "criadoEm").and(Sort.by(Sort.Direction.DESC, "id"))));
            return new PaginaPublica<>(r.getContent().stream().map(e -> {
                Nota n = e.getNota();
                return new ResumoNota(e.getPublicoId(), e.getReferenciaExterna(), StatusNota.valueOf(e.getStatus().name()),
                        e.getCriadoEm(), n.getChave(), n.getNumero(), n.getCompetencia(), n.getOperacao().name(),
                        n.getValorTotal());
            }).toList(), r.getTotalElements(), p, t);
        });
    }

    // ---------------- comparativo ----------------

    public ComparativoEmpresa comparativo(IntegradorAutenticado quem, String de, String ate) {
        for (String c : new String[]{de, ate}) {
            if (c != null && !c.isBlank() && !COMPETENCIA.matcher(c.trim()).matches()) {
                throw new ApiPublicaException(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "Parâmetro inválido",
                        "de e ate devem estar no formato AAAA-MM.");
            }
        }
        String d = de == null || de.isBlank() ? null : de.trim();
        String a = ate == null || ate.isBlank() ? null : ate.trim();
        DashboardDto p;
        try {
            p = EscopoIntegracao.executar(quem.clienteId(), () -> painel.dashboard(quem.clienteId(), d, a));
        } catch (ApiException e) {
            if (e.getStatus().is4xxClientError()) {
                throw new ApiPublicaException(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "Parâmetro inválido",
                        e.getMessage());
            }
            throw e;
        }
        var i = p.indicadores();
        List<String> avisos = new ArrayList<>(AVISOS_PROJECAO);
        p.avisos().stream().filter(x -> !avisos.contains(x)).forEach(avisos::add);
        return new ComparativoEmpresa(ApiPublicaNotasDtos.NATUREZA_PROJECAO, p.periodo().de(), p.periodo().ate(),
                new Indicadores(i.faturamento(), i.compras(), i.liquidoHoje(), i.liquido2027(), i.variacaoPct(),
                        i.credito2027(), i.saldoCredor(), i.pendentesRevisao()),
                comparativo(p.comparativo()),
                p.porMes().stream().map(m -> new Mes(m.competencia(), m.faturamento(), m.liquidoHoje(), m.liquido2027()))
                        .toList(),
                avisos);
    }

    // ---------------- mapeamento ----------------

    private static Documento documento(Nota n) {
        String contraparte = n.getContraparteCnpj();
        return new Documento(n.getChave(), n.getNumero(), n.getSerie(), n.getDataEmissao(), n.getCompetencia(),
                n.getOperacao().name(), n.getOperacao().natureza().name(), contraparte, n.getContraparteNome(),
                n.getValorTotal());
    }

    private ItemNota item(ItemDto i) {
        ClassificacaoDto c = i.classificacao();
        ClassificacaoItem classif = c == null
                ? new ClassificacaoItem(SituacaoClassificacao.SEM_CLASSIFICACAO, null, null, null, null, null, null, null, null)
                : new ClassificacaoItem(situacao(c), c.cst(), c.cClassTrib(), c.nomeCClassTrib(),
                c.regime() == null ? null : c.regime().name(), c.descricaoRegime(), c.origem().name(), c.confianca(),
                c.justificativa());
        var k = i.calculo();
        CalculoItem calc = k == null ? null : new CalculoItem(k.impostoHoje(), k.imposto2027(), k.vCbs(), k.vIbsUf(),
                k.vIbsMun(), k.vIs(), k.pCbs(), k.sujeitoIs(), k.origemValores() == null ? null : k.origemValores().name());
        return new ItemNota(i.nItem(), i.codigo(), i.descricao(), i.ncm(), i.cfop(), i.quantidade(), i.valorTotal(),
                classif, calc);
    }

    /** Mesma regra do CriterioRevisao da plataforma. */
    private SituacaoClassificacao situacao(ClassificacaoDto c) {
        if (c.revisada()) {
            return SituacaoClassificacao.CONFIRMADA;
        }
        boolean confiancaBaixa = c.confianca() == null || c.confianca().compareTo(criterio.confiancaMinima()) < 0;
        return !c.aceita() || confiancaBaixa ? SituacaoClassificacao.PENDENTE_REVISAO : SituacaoClassificacao.CONFIRMADA;
    }

    private static Comparativo comparativo(ComparativoDto c) {
        if (c == null) {
            return null;
        }
        return new Comparativo(apuracao(c.hoje()), apuracao(c.ano2027()), c.variacaoPct());
    }

    private static Apuracao apuracao(ComparativoDto.ApuracaoDto a) {
        return a == null ? null : new Apuracao(a.debito(), a.credito(), a.liquido());
    }

    private List<String> ler(String avisosJson) {
        if (avisosJson == null || avisosJson.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(avisosJson, LISTA_TEXTOS);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static Instant inicioDoDia(LocalDate d) {
        return d.atStartOfDay(BRASILIA).toInstant();
    }

    private static ApiPublicaException naoEncontrada() {
        return new ApiPublicaException(HttpStatus.NOT_FOUND, "NOTA_NAO_ENCONTRADA", "Nota não encontrada",
                "Nenhuma nota com este id para esta chave de API.");
    }
}

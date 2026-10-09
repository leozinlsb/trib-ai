package br.com.tribia.apipublica.servico;

import br.com.tribia.apipublica.dto.ApiPublicaDtos.AnalisePublica;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.ChaveInfo;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.Consumo;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.EmpresaInfo;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.Limites;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.Mercadoria;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.PaginaPublica;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.ResumoAnalise;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.SolicitacaoAnalise;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.UsoChave;
import br.com.tribia.apipublica.model.ChaveApi;
import br.com.tribia.apipublica.model.SolicitacaoApi;
import br.com.tribia.apipublica.repository.ChaveApiRepository;
import br.com.tribia.apipublica.repository.SolicitacaoApiRepository;
import br.com.tribia.apipublica.seguranca.ChavesApi;
import br.com.tribia.apipublica.seguranca.IntegradorAutenticado;
import br.com.tribia.apipublica.web.ApiPublicaException;
import br.com.tribia.dto.fiscal.MercadoriaEntradaDto;
import br.com.tribia.exception.ApiException;
import br.com.tribia.model.AnaliseFiscal;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.StatusAnalise;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.service.fiscal.AnaliseFiscalService;
import br.com.tribia.service.fiscal.ProcessadorAnaliseFiscal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Orquestra a API pública sobre o motor de Inteligência Fiscal da plataforma ({@link AnaliseFiscalService} e
 * {@link ProcessadorAnaliseFiscal}): não há outro caminho até a IA, a JEV ou a validação da NCM.
 *
 * <ul>
 *   <li><b>Empresa:</b> sempre a da chave ({@link IntegradorAutenticado#clienteId()}); o corpo não tem campo de empresa.</li>
 *   <li><b>Idempotência:</b> (chave, Idempotency-Key) → mesma solicitação se o corpo normalizado tiver o mesmo hash;
 *       corpo diferente → 409. Repetir não chama a IA de novo nem consome cota.</li>
 *   <li><b>Concorrência:</b> a criação é serializada por chave (trava em memória) e a restrição única no banco é a
 *       última barreira; a análise e a solicitação nascem na mesma transação, e só depois do commit o processamento
 *       entra na fila (uma requisição perdedora não deixa análise órfã nem chamada à IA).</li>
 *   <li><b>Consumo:</b> cota diária e análises simultâneas contadas no banco (sobrevivem a reinício).</li>
 * </ul>
 */
@Service
public class ApiPublicaService {

    private static final Logger log = LoggerFactory.getLogger(ApiPublicaService.class);
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_.:-]{1,100}$");
    private static final Pattern UUID_TEXTO = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final SolicitacaoApiRepository solicitacoes;
    private final ChaveApiRepository chaves;
    private final ClienteRepository clientes;
    private final AnaliseFiscalService analises;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Clock relogio;
    private final Map<Long, Object> travas = new ConcurrentHashMap<>();

    public ApiPublicaService(SolicitacaoApiRepository solicitacoes, ChaveApiRepository chaves,
                             ClienteRepository clientes, AnaliseFiscalService analises, ObjectMapper json,
                             PlatformTransactionManager transacoes) {
        this.solicitacoes = solicitacoes;
        this.chaves = chaves;
        this.clientes = clientes;
        this.analises = analises;
        this.json = json;
        this.tx = new TransactionTemplate(transacoes);
        this.relogio = Clock.systemUTC();
    }

    /** Resultado da criação: a análise e se ela veio de uma repetição idempotente. */
    public record Criacao(AnalisePublica analise, boolean repetida) {
    }

    public Criacao criar(IntegradorAutenticado quem, String idempotencyKey, SolicitacaoAnalise pedido) {
        String chaveIdem = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (chaveIdem != null && !IDEMPOTENCY_KEY.matcher(chaveIdem).matches()) {
            throw new ApiPublicaException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALIDA", "Idempotency-Key inválida",
                    "Idempotency-Key deve ter de 1 a 100 caracteres [A-Za-z0-9_.:-] (um UUID serve).");
        }
        SolicitacaoAnalise normalizado = normalizar(pedido);
        String hash = ChavesApi.sha256(escrever(normalizado));

        SolicitacaoApi criada;
        synchronized (travas.computeIfAbsent(quem.chaveId(), k -> new Object())) {
            if (chaveIdem != null) {
                var existente = tx.execute(s -> solicitacoes.buscarPorIdempotencia(quem.chaveId(), chaveIdem));
                if (existente.isPresent()) {
                    return new Criacao(repeticao(existente.get(), hash), true);
                }
            }
            verificarConsumo(quem);
            try {
                criada = tx.execute(s -> gravar(quem, chaveIdem, hash, normalizado));
            } catch (DataIntegrityViolationException e) {
                // outra instância (ou trava contornada) gravou a mesma Idempotency-Key no meio do caminho
                var vencedora = chaveIdem == null ? java.util.Optional.<SolicitacaoApi>empty()
                        : tx.execute(s -> solicitacoes.buscarPorIdempotencia(quem.chaveId(), chaveIdem));
                if (vencedora.isPresent()) {
                    return new Criacao(repeticao(vencedora.get(), hash), true);
                }
                throw e;
            }
        }
        log.info("API pública: análise {} criada (chave {}, solicitação {})", criada.getAnalise().getId(),
                quem.prefixo(), criada.getPublicoId());
        try {
            analises.despachar(criada.getAnalise().getId());
        } catch (ApiException e) {
            throw new ApiPublicaException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICO_OCUPADO", "Serviço ocupado",
                    "Muitas análises em andamento no servidor. A solicitação " + criada.getPublicoId()
                            + " foi registrada como FALHOU; envie de novo com outra Idempotency-Key em instantes.",
                    Map.of("analiseId", criada.getPublicoId()), Map.of("Retry-After", "30"));
        }
        return new Criacao(consultar(quem, criada.getPublicoId()), false);
    }

    public AnalisePublica consultar(IntegradorAutenticado quem, String id) {
        String publico = id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
        if (!UUID_TEXTO.matcher(publico).matches()) {
            throw naoEncontrada();
        }
        SolicitacaoApi s = tx.execute(t -> solicitacoes.buscarDaEmpresa(publico, quem.clienteId()))
                .orElseThrow(ApiPublicaService::naoEncontrada);
        return MapeadorAnalisePublica.analise(s, analises.detalheAutorizado(s.getAnalise()));
    }

    public PaginaPublica<ResumoAnalise> listar(IntegradorAutenticado quem, String referenciaExterna, Integer pagina,
                                               Integer tamanho) {
        int p = pagina == null ? 0 : pagina;
        int t = tamanho == null ? 20 : tamanho;
        if (p < 0 || p > 10_000 || t < 1 || t > 100) {
            throw new ApiPublicaException(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "Parâmetro inválido",
                    "pagina deve estar entre 0 e 10000 e tamanho entre 1 e 100.");
        }
        String ref = referenciaExterna == null || referenciaExterna.isBlank() ? null : referenciaExterna.trim();
        Page<SolicitacaoApi> r = tx.execute(s -> solicitacoes.listarDaEmpresa(quem.clienteId(), ref,
                PageRequest.of(p, t, Sort.by(Sort.Direction.DESC, "criadaEm").and(Sort.by(Sort.Direction.DESC, "id")))));
        return new PaginaPublica<>(r.getContent().stream().map(MapeadorAnalisePublica::resumo).toList(),
                r.getTotalElements(), p, t);
    }

    public UsoChave uso(IntegradorAutenticado quem) {
        LocalDate hoje = LocalDate.now(relogio.withZone(BRASILIA));
        return tx.execute(s -> {
            ChaveApi c = chaves.findById(quem.chaveId()).orElseThrow();
            Cliente cliente = c.getCliente();
            long criadas = solicitacoes.contarCriadasDesde(quem.chaveId(), inicioDoDia(hoje));
            long andamento = solicitacoes.contarEmAndamento(quem.chaveId(), StatusAnalise.EM_ANDAMENTO);
            return new UsoChave(
                    new ChaveInfo(c.getPrefixo(), c.getNomeIntegrador(),
                            c.getEscopos().stream().map(Enum::name).sorted().toList(), c.getExpiraEm()),
                    new EmpresaInfo(cliente.getCnpj(), cliente.getRazaoSocial()),
                    new Limites(quem.requisicoesPorMinuto(), quem.cotaDiariaAnalises(), quem.maxAnalisesSimultaneas()),
                    new Consumo(hoje.toString(), criadas, Math.max(0, quem.cotaDiariaAnalises() - criadas), andamento));
        });
    }

    // ---------------- apoio ----------------

    private SolicitacaoApi gravar(IntegradorAutenticado quem, String chaveIdem, String hash, SolicitacaoAnalise pedido) {
        Cliente cliente = clientes.findById(quem.clienteId()).orElseThrow(ApiPublicaService::naoEncontrada);
        if (!cliente.isAtivo()) {
            throw new ApiPublicaException(HttpStatus.FORBIDDEN, "EMPRESA_DESATIVADA", "Empresa desativada",
                    "A empresa desta chave está desativada no TribIA.");
        }
        Mercadoria m = pedido.mercadoria();
        AnaliseFiscal analise = analises.registrarNova(cliente,
                new MercadoriaEntradaDto(m.nome(), m.descricao(), m.composicao(), m.finalidade(), m.caracteristicas(),
                        m.ncmInformada()),
                List.of(), ProcessadorAnaliseFiscal.AnexosLidos.NENHUM);
        ChaveApi chave = chaves.getReferenceById(quem.chaveId());
        return solicitacoes.saveAndFlush(new SolicitacaoApi(UUID.randomUUID().toString(), chave, cliente, analise,
                chaveIdem, hash, pedido.referenciaExterna(), Instant.now(relogio)));
    }

    private AnalisePublica repeticao(SolicitacaoApi existente, String hash) {
        if (!existente.getHashPayload().equals(hash)) {
            throw new ApiPublicaException(HttpStatus.CONFLICT, "IDEMPOTENCIA_CONFLITO", "Idempotency-Key já usada",
                    "Esta Idempotency-Key já foi usada com outro conteúdo. Use uma chave nova para um pedido novo.",
                    Map.of("analiseId", existente.getPublicoId()), Map.of());
        }
        return MapeadorAnalisePublica.analise(existente, analises.detalheAutorizado(existente.getAnalise()));
    }

    private void verificarConsumo(IntegradorAutenticado quem) {
        LocalDate hoje = LocalDate.now(relogio.withZone(BRASILIA));
        long criadasHoje = tx.execute(s -> solicitacoes.contarCriadasDesde(quem.chaveId(), inicioDoDia(hoje)));
        if (criadasHoje >= quem.cotaDiariaAnalises()) {
            long segundos = java.time.Duration.between(Instant.now(relogio), inicioDoDia(hoje.plusDays(1))).toSeconds();
            throw new ApiPublicaException(HttpStatus.TOO_MANY_REQUESTS, "COTA_DIARIA_EXCEDIDA", "Cota diária excedida",
                    "Esta chave já criou " + criadasHoje + " análises hoje (cota: " + quem.cotaDiariaAnalises()
                            + "). A cota renova à meia-noite (horário de Brasília).",
                    Map.of(), Map.of("Retry-After", String.valueOf(Math.max(1, segundos))));
        }
        long emAndamento = tx.execute(s -> solicitacoes.contarEmAndamento(quem.chaveId(), StatusAnalise.EM_ANDAMENTO));
        if (emAndamento >= quem.maxAnalisesSimultaneas()) {
            throw new ApiPublicaException(HttpStatus.TOO_MANY_REQUESTS, "LIMITE_ANALISES_SIMULTANEAS",
                    "Muitas análises em andamento",
                    "Esta chave já tem " + emAndamento + " análises em processamento (limite: "
                            + quem.maxAnalisesSimultaneas() + "). Aguarde alguma terminar.",
                    Map.of(), Map.of("Retry-After", "10"));
        }
    }

    /** Corpo canônico para o hash da idempotência: espaços nas pontas, vazio = ausente, NCM só com dígitos. */
    static SolicitacaoAnalise normalizar(SolicitacaoAnalise p) {
        Mercadoria m = p.mercadoria();
        String ncm = vazio(m.ncmInformada()) == null ? null : m.ncmInformada().replaceAll("\\D", "");
        return new SolicitacaoAnalise(vazio(p.referenciaExterna()),
                new Mercadoria(vazio(m.nome()), vazio(m.descricao()), vazio(m.composicao()), vazio(m.finalidade()),
                        vazio(m.caracteristicas()), ncm));
    }

    private static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Instant inicioDoDia(LocalDate d) {
        return d.atStartOfDay(BRASILIA).toInstant();
    }

    private static ApiPublicaException naoEncontrada() {
        return new ApiPublicaException(HttpStatus.NOT_FOUND, "ANALISE_NAO_ENCONTRADA", "Análise não encontrada",
                "Nenhuma análise com este id para esta chave de API.");
    }

    private String escrever(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

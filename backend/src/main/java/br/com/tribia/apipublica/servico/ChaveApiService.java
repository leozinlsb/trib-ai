package br.com.tribia.apipublica.servico;

import br.com.tribia.apipublica.ApiPublicaProperties;
import br.com.tribia.apipublica.dto.ChaveApiDtos.ChaveCriada;
import br.com.tribia.apipublica.dto.ChaveApiDtos.ChaveResumo;
import br.com.tribia.apipublica.dto.ChaveApiDtos.CriarChaveForm;
import br.com.tribia.apipublica.model.ChaveApi;
import br.com.tribia.apipublica.model.EscopoApi;
import br.com.tribia.apipublica.repository.ChaveApiRepository;
import br.com.tribia.apipublica.repository.SolicitacaoApiRepository;
import br.com.tribia.apipublica.seguranca.ChavesApi;
import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.Cliente;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.security.AcessoService;
import br.com.tribia.security.UsuarioLogado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Emissão, listagem e revogação de chaves da API pública. Só ADMIN (conferido aqui, não só na rota). A chave completa
 * sai uma única vez, na resposta da criação; o log registra só o prefixo.
 */
@Service
public class ChaveApiService {

    private static final Logger log = LoggerFactory.getLogger(ChaveApiService.class);

    private final ChaveApiRepository chaves;
    private final ClienteRepository clientes;
    private final AcessoService acesso;
    private final ApiPublicaProperties props;
    private final SolicitacaoApiRepository solicitacoes;
    private final ConsumoIaApi consumo;

    public ChaveApiService(ChaveApiRepository chaves, ClienteRepository clientes, AcessoService acesso,
                           ApiPublicaProperties props, SolicitacaoApiRepository solicitacoes, ConsumoIaApi consumo) {
        this.chaves = chaves;
        this.clientes = clientes;
        this.acesso = acesso;
        this.props = props;
        this.solicitacoes = solicitacoes;
        this.consumo = consumo;
    }

    @Transactional
    public ChaveCriada criar(CriarChaveForm form) {
        UsuarioLogado admin = acesso.exigirAdmin();
        Cliente cliente = clientes.findById(form.clienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente " + form.clienteId() + " não encontrado"));
        if (!cliente.isAtivo()) {
            throw ApiException.requisicaoInvalida("A empresa está desativada: reative-a antes de emitir uma chave.");
        }
        Set<EscopoApi> escopos;
        try {
            escopos = form.escopos() == null || form.escopos().isEmpty() ? EscopoApi.PADRAO
                    : EscopoApi.separar(String.join(",", form.escopos()));
        } catch (IllegalArgumentException e) {
            throw ApiException.requisicaoInvalida("Escopo desconhecido. Use ANALISES_CRIAR, ANALISES_LER, NOTAS_ENVIAR, NOTAS_LER, CLASSIFICAR e/ou CALCULAR.");
        }
        if (escopos.isEmpty()) {
            throw ApiException.requisicaoInvalida("Informe ao menos um escopo.");
        }
        int maximo = props.validadeMaximaDias();
        Integer dias = form.validadeDias() != null ? form.validadeDias() : maximo == 0 ? null : maximo;
        if (dias != null && maximo > 0 && dias > maximo) {
            throw ApiException.requisicaoInvalida("Validade máxima: " + maximo + " dias.");
        }
        Instant agora = Instant.now();
        ChavesApi.ChaveGerada g = ChavesApi.gerar();
        while (chaves.existsByPrefixo(g.prefixo())) { // 48 bits: colisão improvável, mas não impossível
            g = ChavesApi.gerar();
        }
        ChaveApi c = chaves.save(new ChaveApi(g.prefixo(), g.hash(), form.nomeIntegrador().trim(), cliente, escopos,
                agora, admin.email(), dias == null ? null : agora.plus(Duration.ofDays(dias)),
                form.requisicoesPorMinuto(), form.cotaDiariaAnalises(), form.maxAnalisesSimultaneas(),
                form.cotaDiariaItensIa()));
        log.info("API pública: chave {} emitida para a empresa {} por {}", c.getPrefixo(), cliente.getId(), admin.email());
        return new ChaveCriada(g.chaveCompleta(),
                "Guarde esta chave agora: ela não será mostrada de novo. Não a coloque em código-fonte, Git ou logs.",
                resumo(c, agora));
    }

    @Transactional(readOnly = true)
    public List<ChaveResumo> listar(Long clienteId) {
        acesso.exigirAdmin();
        Instant agora = Instant.now();
        return chaves.listar(clienteId).stream().map(c -> resumo(c, agora)).toList();
    }

    /** Revogar é definitivo e idempotente (revogar de novo mantém a data e o autor originais). */
    @Transactional
    public ChaveResumo revogar(Long id) {
        UsuarioLogado admin = acesso.exigirAdmin();
        ChaveApi c = chaves.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chave de API " + id + " não encontrada"));
        if (!c.revogada()) {
            c.revogar(Instant.now(), admin.email());
            log.info("API pública: chave {} revogada por {}", c.getPrefixo(), admin.email());
        }
        return resumo(c, Instant.now());
    }

    private ChaveResumo resumo(ChaveApi c, Instant agora) {
        String situacao = c.revogada() ? "REVOGADA" : c.expirada(agora) ? "EXPIRADA" : "ATIVA";
        return new ChaveResumo(c.getId(), c.getPrefixo(), c.getNomeIntegrador(), c.getCliente().getId(),
                c.getCliente().getRazaoSocial(), c.getEscopos().stream().map(Enum::name).sorted().toList(), situacao,
                c.getCriadaEm(), c.getCriadaPor(), c.getExpiraEm(), c.getRevogadaEm(), c.getRevogadaPor(),
                c.getUltimoUsoEm(), c.getRequisicoesPorMinuto(), c.getCotaDiariaAnalises(),
                c.getMaxAnalisesSimultaneas(), c.getCotaDiariaItensIa(),
                solicitacoes.contarCriadasDesde(c.getId(), ConsumoIaApi.inicioDoDia(ConsumoIaApi.hoje())),
                consumo.itensHoje(c.getId()));
    }
}

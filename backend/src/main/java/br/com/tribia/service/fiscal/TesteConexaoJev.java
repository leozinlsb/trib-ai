package br.com.tribia.service.fiscal;

import br.com.tribia.config.JevConfig;
import br.com.tribia.config.JevProperties;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Pontuacao;
import br.com.tribia.exception.ApiException;
import br.com.tribia.security.AcessoService;
import br.com.tribia.security.UsuarioLogado;
import br.com.tribia.service.fiscal.AvaliadorJev.Candidata;
import br.com.tribia.service.fiscal.AvaliadorJev.JevIndisponivelException;
import br.com.tribia.service.fiscal.AvaliadorJev.MercadoriaParaJev;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Teste de conexão da JEV AI para o administrador, com uma mercadoria SINTÉTICA (nenhum dado de empresa).
 * O status não chama nada; o teste só chama a API real com confirmação explícita de custo, porque o /v1/systemone é
 * cobrado por token. Funciona mesmo com tribia.jev.modo=DESLIGADO, para validar a chave antes de ativar nas análises.
 * A chave nunca aparece na resposta nem no log.
 */
@Service
public class TesteConexaoJev {

    private static final Logger log = LoggerFactory.getLogger(TesteConexaoJev.class);

    static final MercadoriaParaJev MERCADORIA_SINTETICA = new MercadoriaParaJev(
            "Sabonete em barra (teste de conexão)", "Sabonete de toucador em barra de 90 g, para higiene pessoal.",
            "glicerina e óleos vegetais saponificados", "higiene pessoal", null, List.of("sabão em barra", "uso pessoal"));
    /** uma candidata compatível e uma claramente incompatível: a resposta deve separá-las */
    static final List<Candidata> CANDIDATAS_SINTETICAS = List.of(
            new Candidata("34011190", "Sabões de toucador em barras - outros"),
            new Candidata("85171300", "Telefones inteligentes (smartphones)"));

    public record Status(JevProperties.Modo modo, String url, String modelo, boolean chaveConfigurada,
                         boolean ativaNasAnalises, String observacao) {
    }

    public record Resultado(boolean conectou, List<String> modelosDisponiveis, boolean modeloConfiguradoDisponivel,
                            String modeloQueRespondeu, Map<String, Pontuacao> pontuacoes, Boolean separouCandidatas,
                            Integer tokensEntrada, Integer tokensSaida, long milissegundos, List<String> avisos) {
    }

    private final JevProperties props;
    private final AcessoService acesso;
    private final Function<JevProperties, JevHttp> fabrica;

    @Autowired
    public TesteConexaoJev(JevProperties props, AcessoService acesso, RestClient.Builder builder) {
        this(props, acesso, p -> JevConfig.criar(builder, p));
    }

    TesteConexaoJev(JevProperties props, AcessoService acesso, Function<JevProperties, JevHttp> fabrica) {
        this.props = props;
        this.acesso = acesso;
        this.fabrica = fabrica;
    }

    public Status status() {
        acesso.exigirAdmin();
        boolean ativa = props.modo() == JevProperties.Modo.HTTP && props.chaveConfigurada();
        String obs = switch (props.modo()) {
            case DESLIGADO -> props.chaveConfigurada()
                    ? "Chave configurada, mas a JEV está desligada nas análises (tribia.jev.modo=DESLIGADO)."
                    : "JEV desligada e sem chave: defina JEV_API_KEY no ambiente do servidor.";
            case HTTP -> props.chaveConfigurada() ? "JEV ativa nas análises (cada análise faz uma chamada cobrada)."
                    : "Modo HTTP sem chave: as análises seguem sem pontuação.";
            case SIMULADO -> "Modo SIMULADO: pontuações fictícias, só para desenvolvimento.";
        };
        return new Status(props.modo(), props.url(), props.modelo(), props.chaveConfigurada(), ativa, obs);
    }

    /** @param confirmarCusto precisa ser true: a chamada ao /v1/systemone é cobrada pela TypeSafe */
    public Resultado testar(boolean confirmarCusto) {
        UsuarioLogado quem = acesso.exigirAdmin();
        if (!confirmarCusto) {
            throw ApiException.requisicaoInvalida("O teste chama a API real da JEV AI, cobrada por token. "
                    + "Envie confirmarCusto=true para autorizar uma chamada com mercadoria sintética.");
        }
        if (!props.chaveConfigurada()) {
            throw new ApiException(HttpStatus.CONFLICT, "JEV AI sem chave",
                    "Defina JEV_API_KEY (ou TYPESAFE_API_KEY) no ambiente do servidor e reinicie a API.");
        }
        JevHttp jev = fabrica.apply(props);
        log.info("Teste de conexão da JEV AI solicitado por {} (modelo {})", quem.email(), props.modelo());
        List<String> avisos = new ArrayList<>();
        try {
            List<String> modelos = jev.modelos();
            boolean disponivel = modelos.contains(props.modelo());
            if (!disponivel) {
                avisos.add("O modelo configurado (" + props.modelo() + ") não aparece na lista da conta: "
                        + String.join(", ", modelos) + ".");
            }
            JevHttp.Diagnostico d = jev.avaliarComDiagnostico(MERCADORIA_SINTETICA, CANDIDATAS_SINTETICAS);
            Map<String, Pontuacao> p = new LinkedHashMap<>(d.pontuacoes());
            Boolean separou = null;
            if (p.containsKey("34011190") && p.containsKey("85171300")) {
                BigDecimal certa = p.get("34011190").valor();
                BigDecimal errada = p.get("85171300").valor();
                separou = certa.compareTo(errada) > 0;
                if (!separou) {
                    avisos.add("A JEV não deu nota maior à candidata compatível: revise o formato da pergunta antes de ativar.");
                }
            } else {
                avisos.add("A resposta não trouxe pontuação para as duas candidatas: verifique o formato.");
            }
            log.info("Teste de conexão da JEV AI: modelo {}, {} ms, {} tokens de entrada", d.modelo(), d.milissegundos(),
                    d.tokensEntrada());
            return new Resultado(true, modelos, disponivel, d.modelo(), p, separou, d.tokensEntrada(), d.tokensSaida(),
                    d.milissegundos(), avisos);
        } catch (JevIndisponivelException e) {
            // mensagens escritas para a tela, sem chave
            throw new ApiException(HttpStatus.BAD_GATEWAY, "JEV AI não respondeu", e.getMessage());
        }
    }
}

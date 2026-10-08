package br.com.tribia.config;

import br.com.tribia.service.fiscal.AvaliadorJev;
import br.com.tribia.service.fiscal.JevHttp;
import br.com.tribia.service.fiscal.JevSimulado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Qual {@link AvaliadorJev} a Inteligência Fiscal usa (tribia.jev.modo). DESLIGADO (padrão) não cria bean: o
 * processador usa JevIndisponivel e a análise registra que a pontuação não está disponível.
 */
@Configuration
public class JevConfig {

    private static final Logger log = LoggerFactory.getLogger(JevConfig.class);

    @Bean
    @ConditionalOnProperty(name = "tribia.jev.modo", havingValue = "HTTP")
    AvaliadorJev jevHttp(RestClient.Builder builder, JevProperties props) {
        if (!props.chaveConfigurada()) {
            log.warn("JEV AI em modo HTTP sem chave (JEV_API_KEY): as análises seguem sem pontuação.");
        } else {
            log.info("JEV AI ativa: {} modelo {}", props.url(), props.modelo());
        }
        return criar(builder, props);
    }

    /** Cliente HTTP da JEV com os timeouts configurados (também usado pelo teste de conexão do administrador). */
    public static JevHttp criar(RestClient.Builder builder, JevProperties props) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(props.timeoutConexao()).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(props.timeoutResposta());
        return new JevHttp(builder.clone().baseUrl(props.url()).requestFactory(fabrica).build(), props);
    }

    @Bean
    @ConditionalOnProperty(name = "tribia.jev.modo", havingValue = "SIMULADO")
    AvaliadorJev jevSimulado(Environment env) {
        if (env.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("tribia.jev.modo=SIMULADO não é permitido no profile prod: "
                    + "as pontuações seriam fictícias. Use DESLIGADO ou HTTP.");
        }
        log.warn("JEV AI SIMULADA: pontuações fictícias, só para desenvolvimento.");
        return new JevSimulado();
    }
}

package br.com.tribia.service.fiscal;

import br.com.tribia.config.JevConfig;
import br.com.tribia.config.JevProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato com a API REAL da JEV AI (TypeSafe). Gasta créditos: só roda com autorização, chave no ambiente e a flag:
 *   $env:JEV_API_KEY="..."; .\mvnw.cmd test "-Dtest=JevContratoTest" "-Djev.contrato=true"
 * Uma listagem de modelos e uma avaliação com mercadoria sintética (2 perguntas sim/não, poucas centenas de tokens).
 */
@EnabledIfSystemProperty(named = "jev.contrato", matches = "true")
@EnabledIfEnvironmentVariable(named = "JEV_API_KEY", matches = ".+")
class JevContratoTest {

    @Test
    void apiRealAceitaOFormatoESeparaAsCandidatas() {
        var props = new JevProperties(JevProperties.Modo.HTTP, "https://api.typesafe.ai", System.getenv("JEV_API_KEY"),
                "jev-1.13.0", Duration.ofSeconds(5), Duration.ofSeconds(30), 2, Duration.ofMillis(500), Duration.ofSeconds(4), 10);
        JevHttp jev = JevConfig.criar(RestClient.builder(), props);

        assertThat(jev.modelos()).contains("jev-1.13.0");
        var d = jev.avaliarComDiagnostico(TesteConexaoJev.MERCADORIA_SINTETICA, TesteConexaoJev.CANDIDATAS_SINTETICAS);

        System.out.printf("JEV real: modelo %s, %d ms, tokens %s/%s, pontuações %s%n", d.modelo(), d.milissegundos(),
                d.tokensEntrada(), d.tokensSaida(), d.pontuacoes());
        assertThat(d.modelo()).startsWith("jev-");
        assertThat(d.pontuacoes()).containsKeys("34011190", "85171300");
        assertThat(d.pontuacoes().get("34011190").valor()).isGreaterThan(d.pontuacoes().get("85171300").valor());
    }
}

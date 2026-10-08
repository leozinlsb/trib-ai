package br.com.tribia.client.llm;

import br.com.tribia.config.LlmProperties;
import br.com.tribia.service.classificacao.ClassificadorIa;
import br.com.tribia.service.classificacao.ClassificadorIa.ProdutoParaClassificar;
import br.com.tribia.service.classificacao.ClassificadorIa.ResultadoIa;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaNcmAplicavel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato com o Gemini REAL. Só roda com a variável de ambiente GEMINI_API_KEY definida (gasta um pouco de cota).
 * Não verifica o "gabarito tributário", só que a IA responde no formato certo e dentro das opções oficiais.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
public class GeminiContratoTest {

    public static ClassificadorIa classificador() {
        var props = new LlmProperties("https://generativelanguage.googleapis.com/v1beta",
                List.of("gemini-3.5-flash", "gemini-3.5-flash-lite"), System.getenv("GEMINI_API_KEY"),
                Duration.ofSeconds(5), Duration.ofSeconds(90), 40);
        var fabrica = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(props.timeoutConexao()).build());
        fabrica.setReadTimeout(props.timeoutResposta());
        RestClient http = RestClient.builder().baseUrl(props.url()).requestFactory(fabrica).build();
        return new ClassificadorIa(new GeminiClient(http, props), props, new TabelaCClassTrib(), new TabelaNcmAplicavel(),
                new ObjectMapper());
    }

    static ProdutoParaClassificar produto(int n, String ncm, String descricao) {
        return new ProdutoParaClassificar(n, ncm, descricao, "UN", new BigDecimal("10.00"));
    }

    @Test
    void classificaOsProdutosDaNotaDeTesteSoComOpcoesOficiais() {
        var tabela = new TabelaCClassTrib();
        ResultadoIa r = classificador().classificar(List.of(
                produto(1, "10063021", "ARROZ TIPO 1 5KG"),
                produto(2, "07133319", "FEIJAO CARIOCA 1KG"),
                produto(3, "04012010", "LEITE UHT INTEGRAL 1L"),
                produto(4, "30049069", "DIPIRONA SODICA 500MG 10 COMPRIMIDOS"),
                produto(5, "22021000", "REFRIGERANTE COLA 2L"),
                produto(6, "34022000", "DETERGENTE LIQUIDO NEUTRO 500ML"),
                produto(7, "48182000", "PAPEL TOALHA 2 ROLOS"),
                produto(8, "18063210", "CHOCOLATE AO LEITE 90G")));

        r.sugestoes().values().forEach(s -> System.out.printf("  %d -> %s/%s (%.2f) %s%n", s.nItem(), s.cst(), s.cClassTrib(),
                s.confianca(), s.justificativa()));
        System.out.println("  avisos: " + r.avisos());

        assertThat(r.sugestoes()).hasSize(8);
        assertThat(r.avisos()).isEmpty();
        r.sugestoes().values().forEach(s -> {
            assertThat(tabela.validoParaNfe(s.cst(), s.cClassTrib())).as("item %d", s.nItem()).isTrue();
            assertThat(s.confianca()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
            assertThat(s.justificativa()).isNotBlank();
        });
        assertThat(r.sugestoes().get(1).cClassTrib()).as("arroz = Cesta Básica Nacional").isEqualTo("200003");
    }

    @Test
    void produtoSemNcmEDescricaoVagaNaoQuebra() {
        ResultadoIa r = classificador().classificar(List.of(produto(1, null, "SERVICO DIVERSO")));

        assertThat(r.sugestoes().size() + r.avisos().size()).isEqualTo(1);
    }
}

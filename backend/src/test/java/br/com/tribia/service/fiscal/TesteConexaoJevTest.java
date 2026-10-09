package br.com.tribia.service.fiscal;

import br.com.tribia.config.JevProperties;
import br.com.tribia.exception.ApiException;
import br.com.tribia.model.Papel;
import br.com.tribia.security.AcessoService;
import br.com.tribia.security.UsuarioLogado;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Teste de conexão do administrador, com a API da JEV simulada (nenhuma chamada real). */
class TesteConexaoJevTest {

    static final String BASE = "http://jev.teste";
    MockRestServiceServer servidor;
    final AcessoService acesso = mock(AcessoService.class);

    TesteConexaoJev teste(String chave, JevProperties.Modo modo) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        servidor = MockRestServiceServer.bindTo(builder).build();
        var props = new JevProperties(modo, BASE, chave, "jev-1.13.0", Duration.ofSeconds(1), Duration.ofSeconds(1),
                0, Duration.ofMillis(1), Duration.ofMillis(1), 10);
        when(acesso.exigirAdmin()).thenReturn(new UsuarioLogado(1L, "Admin", "admin@tribia.local", null, Papel.ADMIN, null));
        return new TesteConexaoJev(props, acesso, p -> new JevHttp(builder.build(), p, d -> { }));
    }

    @Test
    void statusNaoChamaAApiENaoMostraAChave() throws Exception {
        TesteConexaoJev t = teste("chave-secreta", JevProperties.Modo.DESLIGADO);
        servidor.expect(never(), requestTo(BASE + "/v1/models"));
        var s = t.status();
        servidor.verify();
        assertThat(s.chaveConfigurada()).isTrue();
        assertThat(s.ativaNasAnalises()).isFalse();
        assertThat(s.observacao()).contains("desligada");
        assertThat(new ObjectMapper().writeValueAsString(s)).doesNotContain("chave-secreta");
    }

    @Test
    void semConfirmacaoDeCustoNaoChama() {
        TesteConexaoJev t = teste("k", JevProperties.Modo.DESLIGADO);
        servidor.expect(never(), requestTo(BASE + "/v1/models"));
        assertThatThrownBy(() -> t.testar(false)).isInstanceOf(ApiException.class).hasMessageContaining("confirmarCusto");
        servidor.verify();
    }

    @Test
    void semChaveResponde409SemChamar() {
        TesteConexaoJev t = teste("", JevProperties.Modo.HTTP);
        servidor.expect(never(), requestTo(BASE + "/v1/models"));
        assertThatThrownBy(() -> t.testar(true)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        servidor.verify();
    }

    @Test
    void testeCompletoListaModelosAvaliaSinteticoEConfereQueSeparouAsCandidatas() throws Exception {
        TesteConexaoJev t = teste("chave-secreta", JevProperties.Modo.DESLIGADO);
        servidor.expect(requestTo(BASE + "/v1/models")).andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer chave-secreta"))
                .andRespond(withSuccess("{\"models\":[{\"name\":\"jev-1.13.0\",\"description\":\"x\",\"release_date\":\"2026-09-15\"},"
                        + "{\"name\":\"jev-latest\",\"description\":\"x\",\"release_date\":\"2026-09-15\"}]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/v1/systemone"))
                .andRespond(withSuccess("{\"model\":\"jev-1.13.0\",\"answers\":{"
                        + "\"ncm_34011190\":{\"type\":\"noul\",\"noul\":0.94},\"ncm_85171300\":{\"type\":\"noul\",\"noul\":0.01}},"
                        + "\"usage\":{\"input_tokens\":310,\"output_tokens\":12}}", MediaType.APPLICATION_JSON));

        var r = t.testar(true);

        servidor.verify();
        assertThat(r.conectou()).isTrue();
        assertThat(r.modeloConfiguradoDisponivel()).isTrue();
        assertThat(r.modeloQueRespondeu()).isEqualTo("jev-1.13.0");
        assertThat(r.separouCandidatas()).isTrue();
        assertThat(r.tokensEntrada()).isEqualTo(310);
        assertThat(r.avisos()).isEmpty();
        assertThat(new ObjectMapper().writeValueAsString(r)).doesNotContain("chave-secreta");
    }

    @Test
    void modeloFixadoAusenteNaContaViraAviso() {
        TesteConexaoJev t = teste("k", JevProperties.Modo.HTTP);
        servidor.expect(requestTo(BASE + "/v1/models"))
                .andRespond(withSuccess("{\"models\":[{\"name\":\"jev-2.0.0\"}]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/v1/systemone"))
                .andRespond(withSuccess("{\"model\":\"jev-2.0.0\",\"answers\":{"
                        + "\"ncm_34011190\":{\"type\":\"noul\",\"noul\":0.2},\"ncm_85171300\":{\"type\":\"noul\",\"noul\":0.6}}}",
                        MediaType.APPLICATION_JSON));
        var r = t.testar(true);
        assertThat(r.modeloConfiguradoDisponivel()).isFalse();
        assertThat(r.separouCandidatas()).isFalse();
        assertThat(r.avisos()).anyMatch(a -> a.contains("jev-1.13.0")).anyMatch(a -> a.contains("revise o formato"));
    }

    @Test
    void chaveRecusadaViraErro502SemExporAChave() {
        TesteConexaoJev t = teste("chave-secreta", JevProperties.Modo.HTTP);
        servidor.expect(requestTo(BASE + "/v1/models")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> t.testar(true)).isInstanceOf(ApiException.class)
                .hasMessageContaining("recusou a chave").hasMessageNotContaining("chave-secreta")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}

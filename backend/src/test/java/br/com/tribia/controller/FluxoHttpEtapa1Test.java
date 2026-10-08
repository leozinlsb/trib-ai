package br.com.tribia.controller;

import br.com.tribia.Fixtures;
import br.com.tribia.client.calculadora.CalculadoraOficialClient;
import br.com.tribia.client.calculadora.CalculadoraSimplificadaClient;
import br.com.tribia.client.llm.LlmClient;
import br.com.tribia.model.Usuario;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

/** HTTP real, Tomcat em porta aleatória/loopback, cookies reais e H2 exclusivo; não é E2E visual. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "spring.config.import=", "tribia.seed.enabled=false",
        "tribia.admin.senha=HttpTeste1234", "tribia.llm.api-key=",
        "tribia.demo.habilitado=true", "tribia.calculo.modo=SIMPLIFICADA"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FluxoHttpEtapa1Test {
    static final String SENHA = "HttpTeste1234";
    @LocalServerPort int porta;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClienteRepository clientes;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @MockitoBean LlmClient llm;
    @MockitoBean CalculadoraOficialClient oficial;
    @MockitoSpyBean CalculadoraSimplificadaClient simplificada;
    final Map<Long, Long> notas = new LinkedHashMap<>();
    final Map<Long, Long> itens = new LinkedHashMap<>();

    @BeforeAll
    void prepararDuasEmpresasComUploadHttp() throws Exception {
        NavegadorHttp admin = logado("admin@tribia.local");
        for (long id : List.of(1L, 2L)) {
            usuarios.saveAndFlush(Usuario.daEmpresa("HTTP empresa " + id, email(id), encoder.encode(SENHA),
                    clientes.findById(id).orElseThrow()));
            var resposta = upload(admin, id, xml(id), true);
            assertThat(resposta.statusCode()).isEqualTo(201);
            long nota = corpo(resposta).path("importadas").get(0).path("id").asLong();
            notas.put(id, nota);
            var detalhe = admin.enviar("GET", "/api/notas/" + nota, null, null, false);
            assertThat(detalhe.statusCode()).isEqualTo(200);
            assertThat(corpo(detalhe).path("clienteId").asLong()).isEqualTo(id);
            itens.put(id, corpo(detalhe).path("itens").get(0).path("id").asLong());
        }
        verifyNoInteractions(llm, oficial, simplificada);
        clearInvocations(llm, oficial, simplificada);
    }

    @Test
    void loginCsrfCookieDeSessaoELogoutReais() throws Exception {
        var n = new NavegadorHttp();
        assertThat(n.enviar("GET", "/api/clientes", null, null, false).statusCode()).isEqualTo(401);
        assertThat(n.enviar("POST", "/api/auth/login", login(email(1), SENHA), "application/json", false)
                .statusCode()).isEqualTo(403);
        n.csrf();
        assertThat(n.enviar("POST", "/api/auth/login", login(email(1), "errada123"),
                "application/json", true).statusCode()).isEqualTo(401);
        var entrada = n.enviar("POST", "/api/auth/login", login(email(1), SENHA), "application/json", true);
        assertThat(entrada.statusCode()).isEqualTo(200);
        assertThat(corpo(entrada).path("clienteId").asLong()).isEqualTo(1L);
        assertThat(entrada.headers().allValues("set-cookie")).anySatisfy(cookie -> {
            assertThat(cookie).contains("JSESSIONID=").containsIgnoringCase("HttpOnly")
                    .containsIgnoringCase("SameSite=Lax");
        });
        assertThat(n.enviar("GET", "/api/auth/me", null, null, false).statusCode()).isEqualTo(200);
        var antes = estado();
        assertThat(n.enviar("POST", "/api/notas/" + notas.get(1L) + "/classificar", null, null, false)
                .statusCode()).isEqualTo(403);
        assertThat(estado()).isEqualTo(antes);
        verifyNoInteractions(llm, oficial, simplificada);
        n.csrf(); // mesmo bootstrap usado pelo frontend após expiração/rotação do token
        assertThat(n.enviar("POST", "/api/auth/logout", null, null, true).statusCode()).isEqualTo(204);
        assertThat(n.enviar("GET", "/api/clientes", null, null, false).statusCode()).isEqualTo(401);
        assertThat(n.enviar("GET", "/api/auth/me", null, null, false).statusCode()).isEqualTo(204);
    }

    @Test
    void listagemSelecaoNotasERejeicoesDeUploadReais() throws Exception {
        var admin = logado("admin@tribia.local");
        assertThat(corpo(admin.enviar("GET", "/api/clientes", null, null, false)).size()).isEqualTo(3);
        for (long id : List.of(1L, 2L)) {
            var n = logado(email(id));
            var lista = corpo(n.enviar("GET", "/api/clientes", null, null, false));
            assertThat(lista.size()).isEqualTo(1);
            assertThat(lista.get(0).path("id").asLong()).isEqualTo(id);
            assertThat(lista.get(0).path("ativo").isBoolean()).isTrue();
            assertThat(lista.get(0).path("ativo").asBoolean()).isTrue();
            for (String rota : leituras(id))
                assertThat(n.enviar("GET", rota, null, null, false).statusCode()).as(rota).isEqualTo(200);
            var listaNotas = corpo(n.enviar("GET", "/api/clientes/" + id + "/notas", null, null, false));
            assertThat(listaNotas.size()).isEqualTo(1);
            assertThat(listaNotas.get(0).path("id").asLong()).isEqualTo(notas.get(id));
            var antes = estado();
            assertThat(upload(n, id, xml(id), true).statusCode()).isEqualTo(409);
            assertThat(upload(n, id, "<nao-e-nfe/>", true).statusCode()).isEqualTo(422);
            assertThat(upload(n, id, xml(id), false).statusCode()).isEqualTo(403);
            assertThat(estado()).isEqualTo(antes);
            verifyNoInteractions(llm, oficial, simplificada);
        }
    }

    @Test
    void acessoCruzadoEAdministrativoNegadoSemEfeitosHttpReal() throws Exception {
        for (long dono : List.of(1L, 2L)) {
            var n = logado(email(3 - dono));
            for (String rota : leituras(dono)) negar(n, "GET", rota, null, 404);
            var antes = estado();
            assertThat(upload(n, dono, xml(dono), true).statusCode()).isEqualTo(404);
            assertThat(estado()).isEqualTo(antes);
            for (String rota : List.of("/api/notas/" + notas.get(dono) + "/classificar",
                    "/api/notas/" + notas.get(dono) + "/classificar?calcular=false",
                    "/api/notas/" + notas.get(dono) + "/calcular",
                    "/api/clientes/" + dono + "/calcular")) negar(n, "POST", rota, null, 404);
            negar(n, "PUT", "/api/notas/" + notas.get(dono) + "/pagamento?confirmado=false", null, 404);
            negar(n, "PUT", "/api/itens/" + itens.get(dono) + "/classificacao",
                    "{\"aceitar\":true,\"aplicarAosIguais\":true}", 404);
            negar(n, "GET", "/api/clientes/" + dono + "/usuarios", null, 403);
            negar(n, "DELETE", "/api/clientes/" + dono, null, 403);
            negar(n, "POST", "/api/demo/reiniciar", null, 403);
            negar(n, "GET", "/api/demo/status", null, 403);
            verifyNoInteractions(llm, oficial, simplificada);
        }
    }

    @Test
    void ativoInativoBloqueiaSessaoExistenteEPreservaNotasHttpReal() throws Exception {
        var admin = logado("admin@tribia.local");
        var empresa = logado(email(2));
        assertThat(admin.enviar("DELETE", "/api/clientes/2", null, null, true).statusCode()).isEqualTo(200);
        try {
            var lista = corpo(admin.enviar("GET", "/api/clientes", null, null, false));
            JsonNode inativa = null;
            for (JsonNode c : lista) if (c.path("id").asLong() == 2) inativa = c;
            assertThat(inativa).isNotNull();
            assertThat(inativa.path("ativo").isBoolean()).isTrue();
            assertThat(inativa.path("ativo").asBoolean()).isFalse();
            assertThat(inativa.path("notas").asInt()).isEqualTo(1);
            negar(empresa, "GET", "/api/clientes", null, 403);
            negar(empresa, "GET", "/api/notas/" + notas.get(2L), null, 403);
            negar(empresa, "POST", "/api/clientes/2/calcular", null, 403);
            var antes = estado();
            assertThat(upload(empresa, 2, xml(2), true).statusCode()).isEqualTo(403);
            assertThat(upload(admin, 2, xml(2), true).statusCode()).isEqualTo(409);
            assertThat(estado()).isEqualTo(antes);
            assertThat(admin.enviar("GET", "/api/notas/" + notas.get(2L), null, null, false)
                    .statusCode()).isEqualTo(200);
            verifyNoInteractions(llm, oficial, simplificada);
        } finally {
            assertThat(admin.enviar("POST", "/api/clientes/2/reativar", null, null, true)
                    .statusCode()).isEqualTo(200);
        }
        assertThat(empresa.enviar("GET", "/api/clientes/2/notas", null, null, false).statusCode()).isEqualTo(200);
        assertThat(corpo(empresa.enviar("GET", "/api/clientes/2/notas", null, null, false)).size()).isEqualTo(1);
    }

    @Test
    void servletH2RealNaoContornaAutorizacao() throws Exception {
        var anonimo = new NavegadorHttp();
        var empresa = logado(email(1));
        for (var n : List.of(anonimo, empresa)) {
            int esperado = n == anonimo ? 401 : 403;
            for (String rota : List.of("/h2-console/", "/h2-console/login.do", "/h2-console/query.do")) {
                negar(n, "GET", rota, null, esperado);
                negar(n, "POST", rota, null, esperado);
            }
        }
        // Diferentemente de MockMvc, aqui o servlet H2 existe e entrega a página para ADMIN.
        var admin = logado("admin@tribia.local");
        assertThat(admin.enviar("GET", "/h2-console/", null, null, false).statusCode()).isEqualTo(200);
    }

    private void negar(NavegadorHttp n, String metodo, String rota, String body, int status) throws Exception {
        var antes = estado();
        var resposta = n.enviar(metodo, rota, body, body == null ? null : "application/json", true);
        assertThat(resposta.statusCode()).as(metodo + " " + rota).isEqualTo(status);
        assertThat(estado()).as("Sem gravações após " + rota).isEqualTo(antes);
        verifyNoInteractions(llm, oficial, simplificada);
    }

    private List<String> leituras(long dono) {
        long nota = notas.get(dono);
        return List.of("/api/clientes/" + dono, "/api/clientes/" + dono + "/notas",
                "/api/clientes/" + dono + "/dashboard", "/api/clientes/" + dono + "/revisao",
                "/api/clientes/" + dono + "/relatorio", "/api/clientes/" + dono + "/relatorio.csv",
                "/api/notas/" + nota, "/api/notas/" + nota + "/resumo", "/api/notas/" + nota + "/export.csv");
    }

    private Map<String, List<Map<String, Object>>> estado() {
        Map<String, List<Map<String, Object>>> snapshot = new LinkedHashMap<>();
        for (String tabela : List.of("cliente", "usuario", "nota", "item", "classificacao", "calculo",
                "classificacao_cache", "registro_revisao"))
            snapshot.put(tabela, jdbc.queryForList("select * from " + tabela + " order by id"));
        return snapshot;
    }

    private String xml(long id) {
        return Fixtures.texto(Fixtures.NFE_ENTRADA_IBSCBS).replace("10433218000193",
                clientes.findById(id).orElseThrow().getCnpj());
    }

    private HttpResponse<String> upload(NavegadorHttp n, long id, String xml, boolean csrf) throws Exception {
        String boundary = "TribiaHttpEtapa1Boundary";
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"arquivos\"; "
                + "filename=\"nota.xml\"\r\nContent-Type: application/xml\r\n\r\n"
                + xml + "\r\n--" + boundary + "--\r\n";
        return n.enviar("POST", "/api/clientes/" + id + "/notas", body,
                "multipart/form-data; boundary=" + boundary, csrf);
    }

    private NavegadorHttp logado(String email) throws Exception {
        var n = new NavegadorHttp();
        n.csrf();
        assertThat(n.enviar("POST", "/api/auth/login", login(email, SENHA), "application/json", true)
                .statusCode()).isEqualTo(200);
        n.csrf();
        return n;
    }

    private String login(String email, String senha) throws Exception {
        return json.writeValueAsString(Map.of("email", email, "senha", senha));
    }

    private static String email(long id) { return "http-etapa1-" + id + "@test.local"; }
    private JsonNode corpo(HttpResponse<String> r) throws Exception { return json.readTree(r.body()); }

    /** Cookie jar exclusivo por perfil; tokens reais, sem csrf() ou principal injetado pelo teste. */
    private class NavegadorHttp {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

        void csrf() throws Exception {
            assertThat(enviar("GET", "/api/auth/csrf", null, null, false).statusCode()).isEqualTo(204);
        }

        HttpResponse<String> enviar(String metodo, String rota, String body, String tipo, boolean csrf)
                throws Exception {
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + rota))
                    .timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
            if (tipo != null) req.header("Content-Type", tipo);
            if (csrf && !metodo.equals("GET")) {
                var token = cookies.getCookieStore().getCookies().stream()
                        .filter(c -> c.getName().equals("XSRF-TOKEN") && !c.hasExpired()).findFirst();
                if (token.isPresent()) req.header("X-XSRF-TOKEN", token.get().getValue());
            }
            req.method(metodo, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            return http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }
}

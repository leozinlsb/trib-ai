package br.com.tribia.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Etapa 4: o que o profile prod (Dockerfile) fecha antes de hospedar a API. */
class PerfilProducaoTest {

    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", "tribia.admin.senha=senha-de-teste-forte-2026"})
    @ActiveProfiles("prod")
    @AutoConfigureMockMvc
    @WithUserDetails("admin@tribia.local")
    class ComProfileProd {

        @Autowired
        MockMvc mvc;

        @Autowired
        Environment env;

        @Test
        void consoleH2SwaggerEDemoNaoExistemMesmoParaOAdministrador() throws Exception {
            mvc.perform(get("/h2-console/")).andExpect(status().isNotFound());
            mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
            mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
            mvc.perform(get("/api/demo/status")).andExpect(status().isNotFound());
            mvc.perform(post("/api/demo/reiniciar").with(csrf())).andExpect(status().isNotFound());
        }

        @Test
        void cookieDeSessaoSoEmHttpsEAtrasDoProxy() {
            assertThat(env.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
            assertThat(env.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        }

        @Test
        void loginComFalhasSeguidasBloqueiaOEmail() throws Exception {
            String errado = "{\"email\": \"ninguem@tribia.local\", \"senha\": \"errada-123\"}";
            for (int i = 0; i < 5; i++) {
                mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(errado))
                        .andExpect(status().isUnauthorized());
            }
            mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(errado))
                    .andExpect(status().isTooManyRequests());
        }
    }

    @Test
    void semSenhaFortePerfilProdNaoSobe() {
        for (String senha : new String[]{null, "", "   ", "curta-123"}) {
            assertThatThrownBy(() -> new AdminSeeder(null, null, "Admin", "a@x", senha, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TRIBIA_ADMIN_SENHA");
        }
        assertThatCode(() -> new AdminSeeder(null, null, "Admin", "a@x", "senha-forte-2026", true)).doesNotThrowAnyException();
        assertThatCode(() -> new AdminSeeder(null, null, "Admin", "a@x", "", false)).doesNotThrowAnyException();
    }
}

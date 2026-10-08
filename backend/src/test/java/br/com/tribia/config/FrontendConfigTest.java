package br.com.tribia.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendConfigTest {

    @Test
    void apiConsoleEDocumentacaoNuncaCaemNoIndexHtml() {
        for (String c : new String[]{"api/clientes", "/api/demo/status", "h2-console/", "swagger-ui.html", "v3/api-docs"}) {
            assertThat(FrontendConfig.reservado(c)).as(c).isTrue();
        }
    }

    @Test
    void rotasDeTelaDoReactCaemNoIndexHtml() {
        for (String c : new String[]{"login", "dashboard/empresas/1/documentos/42", "dashboard"}) {
            assertThat(FrontendConfig.reservado(c)).as(c).isFalse();
            assertThat(FrontendConfig.temExtensao(c)).as(c).isFalse();
        }
    }

    @Test
    void arquivoAusenteDaErro404EmVezDeHtml() {
        assertThat(FrontendConfig.temExtensao("assets/velho-123.js")).isTrue();
        assertThat(FrontendConfig.temExtensao("favicon.svg")).isTrue();
    }
}

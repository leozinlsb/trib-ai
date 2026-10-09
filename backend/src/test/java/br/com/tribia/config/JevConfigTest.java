package br.com.tribia.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JevConfigTest {

    @Test
    void modoSimuladoNaoSobeNoProfileProd() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new JevConfig().jevSimulado(prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
    }

    @Test
    void modoSimuladoSobeForaDeProd() {
        assertThat(new JevConfig().jevSimulado(new MockEnvironment()).simulado()).isTrue();
    }
}

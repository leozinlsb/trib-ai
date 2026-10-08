package br.com.tribia.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChaveClassificacaoTest {

    @Test
    void normalizaMaiusculasAcentosEEspacos() {
        assertThat(ChaveClassificacao.de("07133319", "  Feijão  carioca 1kg "))
                .isEqualTo(ChaveClassificacao.de("07133319", "FEIJAO CARIOCA 1KG"))
                .isEqualTo("07133319|FEIJAO CARIOCA 1KG");
    }

    @Test
    void ncmDiferenteEhOutraChave() {
        assertThat(ChaveClassificacao.de("34022000", "DETERGENTE"))
                .isNotEqualTo(ChaveClassificacao.de("34025000", "DETERGENTE"));
    }

    @Test
    void toleraNulos() {
        assertThat(ChaveClassificacao.de(null, null)).isEqualTo("|");
    }
}

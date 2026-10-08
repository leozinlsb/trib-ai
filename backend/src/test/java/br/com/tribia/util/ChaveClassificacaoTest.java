package br.com.tribia.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void normalizacaoPrivadaPreservaEquivalenciaDoProduto() {
        assertThat(ChaveClassificacao.daEmpresa(1L, "07133319", "  Feijão  carioca 1kg "))
                .isEqualTo(ChaveClassificacao.daEmpresa(1L, "07133319", "FEIJAO CARIOCA 1KG"));
    }

    @Test
    void empresasCatalogoELegadoTemChavesDistintas() {
        String privada = ChaveClassificacao.daEmpresa(1L, "34022000", "DETERGENTE");
        assertThat(privada).isNotEqualTo(ChaveClassificacao.daEmpresa(2L, "34022000", "DETERGENTE"))
                .isNotEqualTo(ChaveClassificacao.doCatalogo("34022000", "DETERGENTE"))
                .isNotEqualTo(ChaveClassificacao.de("34022000", "DETERGENTE"));
    }

    @Test
    void chaveCabeNoSchemaLegadoSemTruncarProdutosLongos() {
        String descricao = "X".repeat(600);
        String chave = ChaveClassificacao.daEmpresa(Long.MAX_VALUE, "34022000", descricao);
        assertThat(chave.length()).isLessThanOrEqualTo(520);
        assertThat(chave).isNotEqualTo(ChaveClassificacao.daEmpresa(Long.MAX_VALUE, "34022000", descricao + "Y"));
        assertThat(ChaveClassificacao.doCatalogo("34022000", descricao).length()).isLessThanOrEqualTo(520);
    }

    @Test
    void cachePrivadoExigeEmpresaPersistida() {
        for (Long id : new Long[]{null, 0L, -1L})
            assertThatThrownBy(() -> ChaveClassificacao.daEmpresa(id, "34022000", "X"))
                    .isInstanceOf(IllegalArgumentException.class);
    }
}

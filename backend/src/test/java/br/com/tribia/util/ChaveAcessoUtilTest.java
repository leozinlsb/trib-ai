package br.com.tribia.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChaveAcessoUtilTest {

    @Test
    void montaChaveValidaCom44Digitos() {
        String chave = ChaveAcessoUtil.montar("35", "2608", "10433218000193", "55", 1, 1234, 1, 12345678);

        assertThat(chave).hasSize(44).startsWith("352608104332180001935500100000123411234567");
        assertThat(ChaveAcessoUtil.valida(chave)).isTrue();
    }

    @Test
    void rejeitaDigitoVerificadorErradoETamanhoErrado() {
        String chave = "35260810433218000193550010000012341123456789";
        assertThat(ChaveAcessoUtil.valida(chave)).isTrue();
        assertThat(ChaveAcessoUtil.valida(chave.substring(0, 43) + "0")).isFalse();
        assertThat(ChaveAcessoUtil.valida(chave.substring(1))).isFalse();
        assertThat(ChaveAcessoUtil.valida(null)).isFalse();
    }

    @Test
    void restoZeroOuUmViraDigitoZero() {
        // varre cNF até achar bases com resto 0 e 1, que pela regra têm DV 0
        boolean achouResto0 = false;
        boolean achouResto1 = false;
        for (int cNF = 0; cNF < 200 && !(achouResto0 && achouResto1); cNF++) {
            String base = "3526081043321800019355001000001234" + "1" + String.format("%08d", cNF);
            int soma = 0;
            int peso = 2;
            for (int i = 42; i >= 0; i--) {
                soma += (base.charAt(i) - '0') * peso;
                peso = peso == 9 ? 2 : peso + 1;
            }
            int resto = soma % 11;
            if (resto == 0 || resto == 1) {
                assertThat(ChaveAcessoUtil.digitoVerificador(base)).isZero();
                achouResto0 |= resto == 0;
                achouResto1 |= resto == 1;
            }
        }
        assertThat(achouResto0 && achouResto1).isTrue();
    }
}

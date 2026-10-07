package br.com.tribia.util;

/**
 * Chave de acesso da NF-e (44 dígitos):
 * cUF(2) AAMM(4) CNPJ(14) mod(2) serie(3) nNF(9) tpEmis(1) cNF(8) cDV(1).
 * O dígito verificador é módulo 11 com pesos 2..9 da direita para a esquerda.
 */
public final class ChaveAcessoUtil {

    private ChaveAcessoUtil() {
    }

    public static boolean valida(String chave) {
        return chave != null && chave.matches("\\d{44}")
                && chave.charAt(43) - '0' == digitoVerificador(chave.substring(0, 43));
    }

    /** Monta a chave completa, já com o dígito verificador. */
    public static String montar(String cUF, String aamm, String cnpj, String modelo,
                                int serie, long numero, int tpEmis, int codigoNumerico) {
        String base = cUF + aamm + cnpj + modelo
                + String.format("%03d%09d%d%08d", serie, numero, tpEmis, codigoNumerico);
        if (!base.matches("\\d{43}")) {
            throw new IllegalArgumentException("Partes da chave de acesso inválidas: " + base);
        }
        return base + digitoVerificador(base);
    }

    public static int digitoVerificador(String base43) {
        int soma = 0;
        int peso = 2;
        for (int i = base43.length() - 1; i >= 0; i--) {
            soma += (base43.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}

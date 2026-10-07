package br.com.tribia.util;

/**
 * Normalização e validação de CNPJ numérico (dígitos verificadores módulo 11).
 * Também usado pelo gerador de XMLs de teste para criar CNPJs fictícios válidos.
 */
public final class CnpjUtil {

    private static final int[] PESOS_DV1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_DV2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private CnpjUtil() {
    }

    /** Remove pontuação. Devolve null se a entrada for null. */
    public static String somenteDigitos(String cnpj) {
        return cnpj == null ? null : cnpj.replaceAll("\\D", "");
    }

    public static boolean valido(String cnpj) {
        String d = somenteDigitos(cnpj);
        if (d == null || d.length() != 14 || d.chars().distinct().count() == 1) {
            return false;
        }
        return d.equals(completarDigitos(d.substring(0, 12)));
    }

    /** Recebe os 12 primeiros dígitos e devolve o CNPJ completo com os 2 verificadores. */
    public static String completarDigitos(String base12) {
        if (base12 == null || !base12.matches("\\d{12}")) {
            throw new IllegalArgumentException("Base do CNPJ deve ter 12 dígitos");
        }
        String com13 = base12 + digito(base12, PESOS_DV1);
        return com13 + digito(com13, PESOS_DV2);
    }

    public static String formatar(String cnpj) {
        String d = somenteDigitos(cnpj);
        if (d == null || d.length() != 14) {
            return cnpj;
        }
        return d.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }

    private static int digito(String numeros, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (numeros.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}

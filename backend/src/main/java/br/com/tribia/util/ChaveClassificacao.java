package br.com.tribia.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Chave do cache de classificação: NCM + descrição normalizada (maiúsculas, sem acento, sem espaços duplos).
 * "Feijão  carioca 1kg" e "FEIJAO CARIOCA 1KG" com o mesmo NCM caem na mesma chave.
 */
public final class ChaveClassificacao {

    private ChaveClassificacao() {
    }

    public static String de(String ncm, String descricao) {
        return (ncm == null ? "" : ncm.trim()) + "|" + normalizar(descricao);
    }

    public static String normalizar(String descricao) {
        if (descricao == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(descricao, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}

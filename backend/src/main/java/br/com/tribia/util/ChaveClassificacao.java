package br.com.tribia.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
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

    /** Chave privada com tamanho fixo, sem alterar o schema legado (varchar 520). */
    public static String daEmpresa(Long clienteId, String ncm, String descricao) {
        if (clienteId == null || clienteId <= 0) {
            throw new IllegalArgumentException("Cache privado exige empresa persistida.");
        }
        return "EMPRESA:v1:" + clienteId + ":" + digest(ncm, descricao);
    }

    public static String doCatalogo(String ncm, String descricao) {
        return "CATALOGO:v1:" + digest(ncm, descricao);
    }

    private static String digest(String ncm, String descricao) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(de(ncm, descricao).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel no runtime Java.", e);
        }
    }

    public static String normalizar(String descricao) {
        if (descricao == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(descricao, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}

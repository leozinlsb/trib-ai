package br.com.tribia.apipublica.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Formato e hash das chaves de API: {@code tribia_<prefixo 12 hex>_<segredo 43 base64url>} (256 bits aleatórios no
 * segredo). O prefixo é público e indexado; o banco guarda só o SHA-256 da chave inteira. Com 256 bits de entropia,
 * um hash rápido basta (não há o que adivinhar por dicionário, ao contrário de senhas, que usam BCrypt).
 */
public final class ChavesApi {

    public static final String INICIO = "tribia_";
    private static final Pattern FORMATO = Pattern.compile("^tribia_([0-9a-f]{12})_([A-Za-z0-9_-]{43})$");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private ChavesApi() {
    }

    public record ChaveGerada(String prefixo, String chaveCompleta, String hash) {
    }

    public static ChaveGerada gerar() {
        byte[] p = new byte[6];
        byte[] s = new byte[32];
        ALEATORIO.nextBytes(p);
        ALEATORIO.nextBytes(s);
        String prefixo = HexFormat.of().formatHex(p);
        String chave = INICIO + prefixo + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(s);
        return new ChaveGerada(prefixo, chave, sha256(chave));
    }

    /** Prefixo da chave se ela tiver o formato certo; vazio para qualquer outra coisa. */
    public static Optional<String> prefixo(String chave) {
        if (chave == null || chave.length() != 63) {
            return Optional.empty();
        }
        var m = FORMATO.matcher(chave);
        return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** Compara em tempo constante o hash da chave recebida com o guardado. */
    public static boolean confere(String chave, String hashGuardado) {
        return MessageDigest.isEqual(sha256(chave).getBytes(StandardCharsets.US_ASCII),
                hashGuardado.getBytes(StandardCharsets.US_ASCII));
    }

    public static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

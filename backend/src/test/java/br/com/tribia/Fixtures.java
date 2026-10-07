package br.com.tribia;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** XMLs de teste em src/test/resources/nfe. */
public final class Fixtures {

    public static final String NFE_SAIDA_HACKATHON = "nfe_teste_hackathon.xml";
    public static final String NFE_ENTRADA_IBSCBS = "nfe_entrada_ibscbs.xml";

    private Fixtures() {
    }

    public static byte[] bytes(String nome) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/nfe/" + nome)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture não encontrada: " + nome);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String texto(String nome) {
        return new String(bytes(nome), StandardCharsets.UTF_8);
    }
}

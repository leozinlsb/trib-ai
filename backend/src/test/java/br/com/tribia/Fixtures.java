package br.com.tribia;

import br.com.tribia.config.AliquotasProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Arquivos de teste em src/test/resources (XMLs em nfe/, respostas da calculadora em calculadora/). */
public final class Fixtures {

    public static final String NFE_SAIDA_HACKATHON = "nfe_teste_hackathon.xml";
    public static final String NFE_ENTRADA_IBSCBS = "nfe_entrada_ibscbs.xml";

    /** Iguais às de application.properties. */
    public static final AliquotasProperties ALIQUOTAS = new AliquotasProperties(
            new AliquotasProperties.Hoje(new BigDecimal("1.65"), new BigDecimal("7.60"), Set.of("01", "02", "03")),
            new AliquotasProperties.Ano2027(new BigDecimal("9.43"), new BigDecimal("0.05"), new BigDecimal("0.05"), true));

    private Fixtures() {
    }

    public static byte[] bytes(String nome) {
        return recurso(nome.contains("/") ? nome : "nfe/" + nome);
    }

    /** Arquivo em src/test/resources, ex.: "calculadora/resposta-regime-geral-2027.json". */
    public static byte[] recurso(String caminho) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/" + caminho)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture não encontrada: " + caminho);
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

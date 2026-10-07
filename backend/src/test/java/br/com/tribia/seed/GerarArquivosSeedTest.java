package br.com.tribia.seed;

import br.com.tribia.Fixtures;
import br.com.tribia.seed.CatalogoSeed.NotaSeed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Regrava os XMLs de seed a partir do {@link CatalogoSeed}. Não roda no build normal.
 * <pre>
 * mvnw test -Dtest=GerarArquivosSeedTest -Dseed.gerar=true
 * </pre>
 * Saída:
 * - src/main/resources/seed/{cnpjCliente}/AAAA-MM-DD_{saida|entrada}_{numero}.xml (importados na inicialização)
 * - notas-demo-ao-vivo/{cliente}_nf{numero}.xml (para o upload na apresentação)
 */
@EnabledIfSystemProperty(named = "seed.gerar", matches = "true")
class GerarArquivosSeedTest {

    static final Path SEED = Path.of("src/main/resources/seed");
    static final Path AO_VIVO = Path.of("notas-demo-ao-vivo");

    @Test
    void gerar() throws IOException {
        limpar(SEED);
        limpar(AO_VIVO);

        for (NotaSeed s : CatalogoSeed.notas()) {
            var n = s.nota();
            String xml = GeradorNfe.gerar(n);
            Path destino;
            if (s.aoVivo()) {
                destino = AO_VIVO.resolve(s.clienteSlug() + "_nf" + n.numero() + ".xml");
            } else {
                String tipo = n.emitente().cnpj().equals(s.cnpjCliente()) ? "saida" : "entrada";
                destino = SEED.resolve(s.cnpjCliente())
                        .resolve(n.dataEmissao().toLocalDate() + "_" + tipo + "_" + n.numero() + ".xml");
            }
            Files.createDirectories(destino.getParent());
            Files.writeString(destino, xml, StandardCharsets.UTF_8);
        }
        // a nota ao vivo da distribuidora é a própria nota de teste do hackathon
        Files.write(AO_VIVO.resolve("1-distribuidora_" + Fixtures.NFE_SAIDA_HACKATHON),
                Fixtures.bytes(Fixtures.NFE_SAIDA_HACKATHON));
    }

    /** Apaga só XMLs gerados antes, para não deixar arquivo velho para trás. */
    private static void limpar(Path raiz) throws IOException {
        if (!Files.exists(raiz)) {
            return;
        }
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            for (Path p : arquivos.filter(f -> f.toString().endsWith(".xml")).toList()) {
                Files.delete(p);
            }
        }
    }
}

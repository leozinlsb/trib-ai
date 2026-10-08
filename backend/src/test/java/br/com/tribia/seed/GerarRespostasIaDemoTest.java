package br.com.tribia.seed;

import br.com.tribia.client.llm.GeminiContratoTest;
import br.com.tribia.service.ParserNfeService;
import br.com.tribia.service.classificacao.ClassificadorIa.ProdutoParaClassificar;
import br.com.tribia.service.classificacao.ClassificadorIa.ResultadoIa;
import br.com.tribia.service.classificacao.RespostasGravadasIa.Resposta;
import br.com.tribia.service.nfe.ItemLido;
import br.com.tribia.util.ChaveClassificacao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Grava em src/main/resources/demo/respostas-ia.json o que o Gemini REAL responde para os produtos das notas do
 * upload ao vivo que não estão no cache do seed. É o plano B do profile demo se a IA cair na apresentação.
 * <pre>
 * $env:GEMINI_API_KEY = "..."; mvnw test -Dtest=GerarRespostasIaDemoTest -Dseed.gerar=true
 * </pre>
 */
@EnabledIfSystemProperty(named = "seed.gerar", matches = "true")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GerarRespostasIaDemoTest {

    @Test
    void gravar() throws IOException {
        Set<String> noCache = ClassificacoesSeed.entradasDoSeed().stream()
                .map(e -> ChaveClassificacao.de(e.ncm(), e.descricao())).collect(Collectors.toSet());

        Map<String, ItemLido> produtos = new LinkedHashMap<>();
        ParserNfeService parser = new ParserNfeService();
        try (Stream<Path> xmls = Files.list(Path.of("notas-demo-ao-vivo"))) {
            for (Path xml : xmls.filter(p -> p.toString().endsWith(".xml")).sorted().toList()) {
                for (ItemLido i : parser.ler(Files.readAllBytes(xml)).itens()) {
                    String chave = ChaveClassificacao.de(i.ncm(), i.descricao());
                    if (!noCache.contains(chave)) {
                        produtos.putIfAbsent(chave, i);
                    }
                }
            }
        }
        List<ItemLido> lista = new ArrayList<>(produtos.values());
        List<ProdutoParaClassificar> pedido = new ArrayList<>();
        for (int n = 0; n < lista.size(); n++) {
            ItemLido i = lista.get(n);
            pedido.add(new ProdutoParaClassificar(n + 1, i.ncm(), i.descricao(), i.unidade(), i.valorUnitario()));
        }

        ResultadoIa r = GeminiContratoTest.classificador().classificar(pedido);

        assertThat(r.avisos()).as("a IA tem de responder todos os produtos").isEmpty();
        List<Resposta> respostas = new ArrayList<>();
        r.sugestoes().forEach((n, s) -> {
            ItemLido i = lista.get(n - 1);
            respostas.add(new Resposta(i.ncm(), i.descricao(), s.cst(), s.cClassTrib(), s.justificativa(), s.confianca()));
            System.out.printf("  %-36s %s -> %s/%s (%.2f) %s%n", i.descricao(), i.ncm(), s.cst(), s.cClassTrib(),
                    s.confianca(), s.justificativa());
        });
        Path destino = Path.of("src/main/resources/demo/respostas-ia.json");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(respostas),
                StandardCharsets.UTF_8);
    }
}

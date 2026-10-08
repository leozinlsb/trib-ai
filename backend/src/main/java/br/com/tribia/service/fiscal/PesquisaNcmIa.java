package br.com.tribia.service.fiscal;

import br.com.tribia.client.llm.LlmClient;
import br.com.tribia.client.llm.LlmException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pede à IA (Gemini) a interpretação da mercadoria e as NCMs candidatas, numa chamada só, e valida a resposta:
 * só ficam códigos de 8 dígitos, sem repetição, até {@link #MAX_CANDIDATAS}, da maior para a menor confiança.
 * A IA sugere; quem decide é a revisão humana.
 */
@Component
public class PesquisaNcmIa {

    static final int MAX_CANDIDATAS = 4;
    /** Texto de anexos enviado à IA, no total. */
    static final int MAX_TEXTO_ANEXOS = 8000;

    public record Entrada(String nome, String descricao, String composicao, String finalidade, String caracteristicas,
                          String ncmAtual, Map<String, String> textoDosAnexos) {
    }

    public record Candidata(String ncm, String descricao, List<String> motivos, String avaliacao,
                            BigDecimal confianca) {
    }

    public record Resposta(boolean suficiente, List<String> faltando, List<String> caracteristicas,
                           List<Candidata> candidatas, List<String> regrasConsideradas, List<String> observacoes,
                           List<String> descartadas) {
    }

    static final Map<String, Object> ESQUEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "suficiente", Map.of("type", "BOOLEAN"),
                    "faltando", lista(Map.of("type", "STRING")),
                    "caracteristicas", lista(Map.of("type", "STRING")),
                    "candidatas", lista(Map.of(
                            "type", "OBJECT",
                            "properties", Map.of(
                                    "ncm", Map.of("type", "STRING"),
                                    "descricao", Map.of("type", "STRING"),
                                    "motivos", lista(Map.of("type", "STRING")),
                                    "avaliacao", Map.of("type", "STRING"),
                                    "confianca", Map.of("type", "NUMBER")),
                            "required", List.of("ncm", "descricao", "motivos", "avaliacao", "confianca"))),
                    "regrasConsideradas", lista(Map.of("type", "STRING")),
                    "observacoes", lista(Map.of("type", "STRING"))),
            "required", List.of("suficiente", "faltando", "caracteristicas", "candidatas", "regrasConsideradas",
                    "observacoes"));

    private final LlmClient llm;
    private final ObjectMapper json;
    private final String instrucoes;

    public PesquisaNcmIa(LlmClient llm, ObjectMapper json) {
        this.llm = llm;
        this.json = json;
        this.instrucoes = carregarPrompt();
    }

    /** @throws LlmException se a IA não estiver configurada, estiver fora do ar ou responder algo inutilizável */
    public Resposta pesquisar(Entrada e) {
        String texto = llm.gerarJson(instrucoes, pedido(e), ESQUEMA);
        JsonNode raiz;
        try {
            raiz = json.readTree(texto);
        } catch (IOException ex) {
            throw new LlmException(LlmException.Tipo.RESPOSTA_INVALIDA, "A IA não devolveu um JSON válido.", ex);
        }
        List<String> descartadas = new ArrayList<>();
        Map<String, Candidata> porNcm = new LinkedHashMap<>();
        for (JsonNode c : raiz.path("candidatas")) {
            String ncm = c.path("ncm").asText("").replaceAll("\\D", "");
            String original = c.path("ncm").asText("");
            if (ncm.length() != 8) {
                descartadas.add("\"" + original + "\" não tem 8 dígitos");
                continue;
            }
            if (porNcm.containsKey(ncm)) {
                continue;
            }
            BigDecimal confianca = BigDecimal.valueOf(c.path("confianca").asDouble(0))
                    .max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
            porNcm.put(ncm, new Candidata(ncm, c.path("descricao").asText("").trim(), textos(c.path("motivos")),
                    c.path("avaliacao").asText("").trim(), confianca));
        }
        List<Candidata> candidatas = porNcm.values().stream()
                .sorted(Comparator.comparing(Candidata::confianca).reversed())
                .limit(MAX_CANDIDATAS)
                .toList();
        return new Resposta(raiz.path("suficiente").asBoolean(false), textos(raiz.path("faltando")),
                textos(raiz.path("caracteristicas")), candidatas, textos(raiz.path("regrasConsideradas")),
                textos(raiz.path("observacoes")), descartadas);
    }

    String pedido(Entrada e) {
        StringBuilder sb = new StringBuilder("Classifique a mercadoria abaixo.\n<mercadoria>\n");
        campo(sb, "Nome", e.nome());
        campo(sb, "Descrição", e.descricao());
        campo(sb, "Composição", e.composicao());
        campo(sb, "Finalidade/uso", e.finalidade());
        campo(sb, "Características", e.caracteristicas());
        campo(sb, "NCM que a empresa usa hoje", e.ncmAtual());
        int restante = MAX_TEXTO_ANEXOS;
        for (var anexo : e.textoDosAnexos().entrySet()) {
            if (restante <= 0) {
                break;
            }
            String conteudo = anexo.getValue().replace("</mercadoria>", "");
            String t = conteudo.length() > restante ? conteudo.substring(0, restante) : conteudo;
            restante -= t.length();
            sb.append("Anexo \"").append(anexo.getKey()).append("\":\n").append(t).append('\n');
        }
        return sb.append("</mercadoria>").toString();
    }

    private static void campo(StringBuilder sb, String rotulo, String valor) {
        if (valor != null && !valor.isBlank()) {
            // o delimitador não pode ser fechado de dentro do texto do usuário
            sb.append(rotulo).append(": ").append(valor.replace("</mercadoria>", "")).append('\n');
        }
    }

    private static List<String> textos(JsonNode lista) {
        List<String> r = new ArrayList<>();
        lista.forEach(n -> {
            String t = n.asText("").trim();
            if (!t.isEmpty()) {
                r.add(t);
            }
        });
        return r;
    }

    private static Map<String, Object> lista(Map<String, Object> item) {
        return Map.of("type", "ARRAY", "items", item);
    }

    private static String carregarPrompt() {
        try (InputStream in = new ClassPathResource("prompt-ncm.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("prompt-ncm.txt não encontrado", e);
        }
    }
}

package br.com.tribia.service.classificacao;

import br.com.tribia.client.llm.LlmClient;
import br.com.tribia.client.llm.LlmException;
import br.com.tribia.config.LlmProperties;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaCClassTrib.CClassTrib;
import br.com.tribia.service.tabelas.TabelaNcmAplicavel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pede à IA o CST e o cClassTrib de produtos, e só aceita o que a tabela oficial permite.
 *
 * - Uma chamada por lote de até tribia.llm.itens-por-chamada produtos (uma por nota, na prática).
 * - A IA recebe a lista fechada de opções e, para cada produto, as regras oficiais por NCM como pista.
 * - Resposta com código fora da lista, CST que não confere ou item faltando é rejeitada; esses itens são
 *   reenviados uma vez e, se falharem de novo, ficam sem classificação (com aviso) para a revisão manual.
 */
@Service
public class ClassificadorIa {

    private static final Logger log = LoggerFactory.getLogger(ClassificadorIa.class);
    private static final int TENTATIVAS = 2;
    private static final int MAX_JUSTIFICATIVA = 600;

    /** Produto a classificar. nItem só identifica o produto dentro desta chamada. */
    public record ProdutoParaClassificar(int nItem, String ncm, String descricao, String unidade,
                                         BigDecimal valorUnitario) {
    }

    public record SugestaoIa(int nItem, String cst, String cClassTrib, String justificativa, BigDecimal confianca) {
    }

    /** @param avisos motivos de itens que ficaram sem classificação */
    public record ResultadoIa(Map<Integer, SugestaoIa> sugestoes, List<String> avisos) {
    }

    private final LlmClient llm;
    private final LlmProperties props;
    private final TabelaCClassTrib tabela;
    private final TabelaNcmAplicavel regrasNcm;
    private final ObjectMapper json;
    private final String instrucoes;
    private final Set<String> opcoesValidas;

    public ClassificadorIa(LlmClient llm, LlmProperties props, TabelaCClassTrib tabela, TabelaNcmAplicavel regrasNcm,
                           OpcoesClassificacao opcoesPermitidas, ObjectMapper json) {
        this.llm = llm;
        this.props = props;
        this.tabela = tabela;
        this.regrasNcm = regrasNcm;
        this.json = json;
        List<CClassTrib> opcoes = opcoesPermitidas.todas();
        this.opcoesValidas = opcoes.stream().map(CClassTrib::codigo).collect(Collectors.toUnmodifiableSet());
        this.instrucoes = carregarPrompt().replace("{{OPCOES}}", opcoes.stream()
                .map(o -> o.codigo() + " | " + o.cst() + " | " + o.descricaoRegime() + " | " + abreviar(o.nome(), 110)
                        + (regrasNcm.exigeNcmNaLista(o.codigo()) ? " [SÓ COM NCM NA LISTA OFICIAL]" : ""))
                .collect(Collectors.joining("\n")));
    }

    public boolean disponivel() {
        return props.configurada();
    }

    /**
     * @throws LlmException se a IA não estiver configurada ou indisponível
     */
    public ResultadoIa classificar(List<ProdutoParaClassificar> produtos) {
        Map<Integer, SugestaoIa> aceitas = new LinkedHashMap<>();
        List<String> avisos = new ArrayList<>();
        int lote = Math.max(1, props.itensPorChamada());
        for (int i = 0; i < produtos.size(); i += lote) {
            classificarLote(produtos.subList(i, Math.min(produtos.size(), i + lote)), aceitas, avisos);
        }
        return new ResultadoIa(aceitas, avisos);
    }

    private void classificarLote(List<ProdutoParaClassificar> lote, Map<Integer, SugestaoIa> aceitas,
                                 List<String> avisos) {
        List<ProdutoParaClassificar> faltando = lote;
        Map<Integer, String> motivos = new LinkedHashMap<>();
        Map<Integer, String> motivosAnteriores = Map.of();
        for (int tentativa = 1; tentativa <= TENTATIVAS && !faltando.isEmpty(); tentativa++) {
            if (tentativa > 1) {
                motivosAnteriores = Map.copyOf(motivos);
            }
            motivos.clear();
            String resposta = llm.gerarJson(instrucoes, pedido(faltando, tentativa > 1 ? motivosAnteriores : Map.of()), ESQUEMA);
            Map<Integer, SugestaoIa> validas = validar(resposta, faltando, motivos);
            aceitas.putAll(validas);
            faltando = faltando.stream().filter(p -> !validas.containsKey(p.nItem())).toList();
            if (!faltando.isEmpty()) {
                log.warn("IA: {} item(ns) sem classificação válida na tentativa {}: {}", faltando.size(), tentativa, motivos);
            }
        }
        for (ProdutoParaClassificar p : faltando) {
            avisos.add("A IA não deu uma classificação válida para \"" + p.descricao() + "\" ("
                    + motivos.getOrDefault(p.nItem(), "sem resposta") + "). Classifique manualmente na revisão.");
        }
    }

    /** Aceita só sugestões para itens pedidos, com código da lista de opções e CST coerente. */
    private Map<Integer, SugestaoIa> validar(String resposta, List<ProdutoParaClassificar> pedidos,
                                             Map<Integer, String> motivos) {
        Map<Integer, SugestaoIa> validas = new LinkedHashMap<>();
        Set<Integer> pedidosIds = pedidos.stream().map(ProdutoParaClassificar::nItem).collect(Collectors.toSet());
        JsonNode raiz;
        try {
            raiz = json.readTree(resposta);
        } catch (IOException e) {
            pedidosIds.forEach(id -> motivos.put(id, "resposta não é um JSON válido"));
            return validas;
        }
        JsonNode lista = raiz.isObject() && raiz.size() == 1 && raiz.elements().next().isArray()
                ? raiz.elements().next() : raiz;
        if (!lista.isArray()) {
            pedidosIds.forEach(id -> motivos.put(id, "resposta não é uma lista"));
            return validas;
        }
        for (JsonNode n : lista) {
            if (!n.hasNonNull("nItem") || !pedidosIds.contains(n.get("nItem").asInt())) {
                continue;
            }
            int nItem = n.get("nItem").asInt();
            String cst = texto(n, "cst");
            String codigo = texto(n, "cClassTrib");
            String ncm = pedidos.stream().filter(p -> p.nItem() == nItem).findFirst().map(ProdutoParaClassificar::ncm).orElse(null);
            if (!opcoesValidas.contains(codigo)) {
                motivos.put(nItem, "código " + codigo + " fora da lista de opções");
            } else if (!tabela.validoParaNfe(cst, codigo)) {
                motivos.put(nItem, "CST " + cst + " não confere com o cClassTrib " + codigo);
            } else if (regrasNcm.exigeNcmNaLista(codigo) && !regrasNcm.cobre(ncm, codigo)) {
                // Benefício de anexo sem o NCM na lista oficial: provável invenção da IA, e subestimaria o imposto
                motivos.put(nItem, "o cClassTrib " + codigo + " só vale para NCMs da lista oficial e o NCM "
                        + (ncm == null ? "(vazio)" : ncm) + " não consta nela");
            } else if (validas.containsKey(nItem)) {
                // duplicado: vale a primeira resposta válida
                continue;
            } else {
                validas.put(nItem, new SugestaoIa(nItem, cst, codigo, abreviar(texto(n, "justificativa"), MAX_JUSTIFICATIVA),
                        confianca(n.get("confianca"))));
            }
        }
        for (Integer id : pedidosIds) {
            if (!validas.containsKey(id)) {
                motivos.putIfAbsent(id, "item ausente na resposta");
            }
        }
        return validas;
    }

    /** @param motivosDaFalha por que cada item foi recusado na tentativa anterior; vazio na primeira chamada */
    private String pedido(List<ProdutoParaClassificar> produtos, Map<Integer, String> motivosDaFalha) {
        StringBuilder sb = new StringBuilder();
        if (!motivosDaFalha.isEmpty()) {
            sb.append("ATENÇÃO: na resposta anterior alguns itens foram recusados. Classifique-os de novo usando SOMENTE ")
                    .append("as opções válidas, com o CST exato da opção:\n");
            motivosDaFalha.forEach((id, motivo) -> sb.append("- nItem=").append(id).append(": ").append(motivo).append('\n'));
            sb.append('\n');
        }
        sb.append("Classifique estes itens:\n");
        for (ProdutoParaClassificar p : produtos) {
            var regras = regrasNcm.regrasPara(p.ncm()).stream().filter(r -> opcoesValidas.contains(r.cClassTrib()))
                    .map(TabelaNcmAplicavel.Regra::descricao).distinct().limit(8).toList();
            sb.append("nItem=").append(p.nItem())
                    .append(" | NCM ").append(p.ncm() == null ? "(sem NCM)" : p.ncm())
                    .append(" | ").append(p.descricao())
                    .append(" | unidade ").append(p.unidade() == null ? "-" : p.unidade())
                    .append(" | regras oficiais por NCM: ")
                    .append(regras.isEmpty() ? "nenhuma" : String.join("; ", regras))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String texto(JsonNode n, String campo) {
        return n.hasNonNull(campo) ? n.get(campo).asText().trim() : "";
    }

    /** Número de 0 a 1; ausente ou ilegível vira 0,50 (a revisão cuida). */
    static BigDecimal confianca(JsonNode n) {
        if (n == null || !n.isNumber()) {
            return new BigDecimal("0.50");
        }
        BigDecimal v = n.decimalValue();
        return v.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String abreviar(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max - 1).trim() + "…";
    }

    private static String carregarPrompt() {
        try (InputStream in = new ClassPathResource("prompt-classificador.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("prompt-classificador.txt não encontrado", e);
        }
    }

    /** Esquema da resposta: lista de {nItem, cst, cClassTrib, justificativa, confianca}. */
    static final Map<String, Object> ESQUEMA = Map.of(
            "type", "ARRAY",
            "items", Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                            "nItem", Map.of("type", "INTEGER"),
                            "cst", Map.of("type", "STRING"),
                            "cClassTrib", Map.of("type", "STRING"),
                            "justificativa", Map.of("type", "STRING"),
                            "confianca", Map.of("type", "NUMBER")),
                    "required", List.of("nItem", "cst", "cClassTrib", "justificativa", "confianca")));
}

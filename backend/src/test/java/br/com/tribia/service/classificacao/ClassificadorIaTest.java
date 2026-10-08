package br.com.tribia.service.classificacao;

import br.com.tribia.client.llm.LlmClient;
import br.com.tribia.client.llm.LlmException;
import br.com.tribia.config.LlmProperties;
import br.com.tribia.service.classificacao.ClassificadorIa.ProdutoParaClassificar;
import br.com.tribia.service.classificacao.ClassificadorIa.ResultadoIa;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaNcmAplicavel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassificadorIaTest {

    static final TabelaCClassTrib TABELA = new TabelaCClassTrib();

    /** LLM de mentira: devolve as respostas na ordem e guarda o que recebeu. */
    static class LlmFalso implements LlmClient {
        final List<String> respostas;
        final List<String> instrucoes = new ArrayList<>();
        final List<String> pedidos = new ArrayList<>();

        LlmFalso(String... respostas) {
            this.respostas = new ArrayList<>(List.of(respostas));
        }

        @Override
        public String gerarJson(String instrucoes, String pedido, Map<String, Object> esquema) {
            this.instrucoes.add(instrucoes);
            this.pedidos.add(pedido);
            return respostas.remove(0);
        }
    }

    ClassificadorIa ia(LlmClient llm, int itensPorChamada) {
        var props = new LlmProperties("http://x", List.of("m"), "k", Duration.ofSeconds(1), Duration.ofSeconds(1),
                itensPorChamada);
        return new ClassificadorIa(llm, props, TABELA, new TabelaNcmAplicavel(), new ObjectMapper());
    }

    static ProdutoParaClassificar produto(int n, String ncm, String desc) {
        return new ProdutoParaClassificar(n, ncm, desc, "UN", new BigDecimal("10.00"));
    }

    static String item(int n, String cst, String codigo, String conf) {
        return "{\"nItem\":" + n + ",\"cst\":\"" + cst + "\",\"cClassTrib\":\"" + codigo
                + "\",\"justificativa\":\"porque sim\",\"confianca\":" + conf + "}";
    }

    @Test
    void aceitaSugestoesValidasDaIA() {
        var llm = new LlmFalso("[" + item(1, "200", "200003", "0.95") + "," + item(2, "000", "000001", "0.8") + "]");

        ResultadoIa r = ia(llm, 40).classificar(List.of(produto(1, "10063021", "ARROZ TIPO 1 5KG"),
                produto(2, "18063210", "CHOCOLATE AO LEITE 90G")));

        assertThat(r.avisos()).isEmpty();
        assertThat(r.sugestoes()).containsOnlyKeys(1, 2);
        assertThat(r.sugestoes().get(1).cClassTrib()).isEqualTo("200003");
        assertThat(r.sugestoes().get(1).confianca()).isEqualByComparingTo("0.95");
        assertThat(llm.pedidos).hasSize(1);
    }

    @Test
    void oPromptTrazAsOpcoesValidasEAsRegrasOficiaisPorNcm() {
        var llm = new LlmFalso("[" + item(1, "200", "200003", "0.9") + "]");

        ia(llm, 40).classificar(List.of(produto(1, "10063021", "ARROZ TIPO 1 5KG")));

        assertThat(llm.instrucoes.get(0))
                .contains("200003 | 200 | Alíquota zero (redução de 100%)")
                .contains("000001 | 000 | Tributação integral")
                .doesNotContain("{{OPCOES}}");
        assertThat(llm.pedidos.get(0))
                .contains("nItem=1 | NCM 10063021 | ARROZ TIPO 1 5KG")
                .contains("200003 (Anexo I, item 1)");
    }

    @Test
    void produtoSemRegraPorNcmDizNenhuma() {
        var llm = new LlmFalso("[" + item(1, "000", "000001", "0.8") + "]");

        ia(llm, 40).classificar(List.of(produto(1, "18063210", "CHOCOLATE AO LEITE 90G")));

        assertThat(llm.pedidos.get(0)).contains("regras oficiais por NCM: nenhuma");
    }

    @Test
    void codigoInventadoEhRejeitadoEReenviadoUmaVez() {
        var llm = new LlmFalso(
                "[" + item(1, "200", "200999", "0.9") + "," + item(2, "000", "000001", "0.8") + "]",
                "[" + item(1, "200", "200003", "0.9") + "]");

        ResultadoIa r = ia(llm, 40).classificar(List.of(produto(1, "10063021", "ARROZ"), produto(2, "18063210", "CHOCOLATE")));

        assertThat(r.sugestoes()).containsOnlyKeys(1, 2);
        assertThat(r.sugestoes().get(1).cClassTrib()).isEqualTo("200003");
        assertThat(llm.pedidos).hasSize(2);
        assertThat(llm.pedidos.get(1)).startsWith("ATENÇÃO").contains("nItem=1").doesNotContain("nItem=2");
    }

    @Test
    void cstQueNaoConfereComOCodigoEhRejeitado() {
        // 200003 é CST 200; a IA disse 000
        var llm = new LlmFalso("[" + item(1, "000", "200003", "0.9") + "]", "[" + item(1, "000", "200003", "0.9") + "]");

        ResultadoIa r = ia(llm, 40).classificar(List.of(produto(1, "10063021", "ARROZ TIPO 1 5KG")));

        assertThat(r.sugestoes()).isEmpty();
        assertThat(r.avisos()).singleElement().asString()
                .contains("ARROZ TIPO 1 5KG").contains("CST 000 não confere").contains("manualmente");
    }

    @Test
    void codigoDeRegimeEspecialForaDaListaDeOpcoesEhRejeitado() {
        // 000002 existe na tabela oficial, mas só vale para NFS-e: não está nas opções oferecidas
        var llm = new LlmFalso("[" + item(1, "000", "000002", "0.9") + "]", "[" + item(1, "000", "000002", "0.9") + "]");

        ResultadoIa r = ia(llm, 40).classificar(List.of(produto(1, "18063210", "CHOCOLATE")));

        assertThat(r.sugestoes()).isEmpty();
        assertThat(r.avisos()).singleElement().asString().contains("fora da lista de opções");
    }

    @Test
    void beneficioDeAnexoSemONcmNaListaOficialEhRecusadoEACorrecaoExplicaOMotivo() {
        // detergente (3402.50) não consta da lista do Anexo VIII: 200035 é benefício inventado, 000001 é o certo
        var llm = new LlmFalso("[" + item(1, "200", "200035", "0.90") + "]", "[" + item(1, "000", "000001", "0.80") + "]");

        ResultadoIa r = ia(llm, 40).classificar(List.of(produto(1, "34025000", "DETERGENTE LIQUIDO NEUTRO 500ML")));

        assertThat(r.sugestoes().get(1).cClassTrib()).isEqualTo("000001");
        assertThat(llm.pedidos.get(1))
                .contains("ATENÇÃO")
                .contains("nItem=1: o cClassTrib 200035 só vale para NCMs da lista oficial e o NCM 34025000 não consta nela");
        assertThat(llm.instrucoes.get(0)).contains("200035 | 200 | Redução de 60%").contains("[SÓ COM NCM NA LISTA OFICIAL]");
    }

    @Test
    void beneficioDeAnexoComONcmNaListaPassaEMedicamentoNaoTemATrava() {
        var llm = new LlmFalso("[" + item(1, "200", "200035", "0.90") + "," + item(2, "200", "200032", "0.70") + "]");

        // 96190000 (fraldas) está no Anexo VIII; 200032 (medicamentos) não depende de lista de NCM
        ResultadoIa r = ia(llm, 40).classificar(List.of(produto(1, "96190000", "FRALDA GERIATRICA G"),
                produto(2, "30049069", "DIPIRONA 500MG")));

        assertThat(r.avisos()).isEmpty();
        assertThat(r.sugestoes()).containsOnlyKeys(1, 2);
    }

    @Test
    void itemAusenteNaRespostaEhReenviadoEDepoisAvisado() {
        var llm = new LlmFalso("[" + item(1, "000", "000001", "0.8") + "]", "[]");

        ResultadoIa r = ia(llm, 40).classificar(List.of(produto(1, "18063210", "CHOCOLATE"), produto(2, "34025000", "DETERGENTE")));

        assertThat(r.sugestoes()).containsOnlyKeys(1);
        assertThat(r.avisos()).singleElement().asString().contains("DETERGENTE").contains("ausente");
    }

    @Test
    void confiancaForaDoIntervaloOuAusenteEhAjustada() {
        var llm = new LlmFalso("[" + item(1, "000", "000001", "1.7") + ","
                + "{\"nItem\":2,\"cst\":\"000\",\"cClassTrib\":\"000001\",\"justificativa\":\"x\"}]");

        ResultadoIa r = ia(llm, 40).classificar(List.of(produto(1, "18063210", "A"), produto(2, "18063210", "B")));

        assertThat(r.sugestoes().get(1).confianca()).isEqualByComparingTo("1.00");
        assertThat(r.sugestoes().get(2).confianca()).isEqualByComparingTo("0.50");
    }

    @Test
    void ignoraNItemQueNaoFoiPedido() {
        var llm = new LlmFalso("[" + item(1, "000", "000001", "0.8") + "," + item(99, "000", "000001", "0.8") + "]");

        ResultadoIa r = ia(llm, 40).classificar(List.of(produto(1, "18063210", "CHOCOLATE")));

        assertThat(r.sugestoes()).containsOnlyKeys(1);
    }

    @Test
    void respostaQueNaoEJsonViraAvisoEntregueDepoisDeReenviar() {
        var llm = new LlmFalso("isto não é json", "[ainda não");

        ResultadoIa r = ia(llm, 40).classificar(List.of(produto(1, "18063210", "CHOCOLATE")));

        assertThat(r.sugestoes()).isEmpty();
        assertThat(r.avisos()).singleElement().asString().contains("não é um JSON válido");
    }

    @Test
    void notasGrandesSaoDivididasEmLotes() {
        var llm = new LlmFalso(
                "[" + item(1, "000", "000001", "0.8") + "," + item(2, "000", "000001", "0.8") + "]",
                "[" + item(3, "000", "000001", "0.8") + "]");

        ResultadoIa r = ia(llm, 2).classificar(List.of(produto(1, "18063210", "A"), produto(2, "18063210", "B"),
                produto(3, "18063210", "C")));

        assertThat(llm.pedidos).hasSize(2);
        assertThat(r.sugestoes()).containsOnlyKeys(1, 2, 3);
    }

    @Test
    void falhaDaIAPropagaParaQuemChamaDecidir() {
        LlmClient fora = (i, p, e) -> {
            throw new LlmException(LlmException.Tipo.INDISPONIVEL, "fora do ar");
        };

        assertThatThrownBy(() -> ia(fora, 40).classificar(List.of(produto(1, "18063210", "CHOCOLATE"))))
                .isInstanceOf(LlmException.class).hasMessage("fora do ar");
    }

    @Test
    void aceitaRespostaEmbrulhadaEmObjetoComUmaLista() {
        var llm = new LlmFalso("{\"itens\":[" + item(1, "000", "000001", "0.8") + "]}");

        assertThat(ia(llm, 40).classificar(List.of(produto(1, "18063210", "CHOCOLATE"))).sugestoes()).containsOnlyKeys(1);
    }
}

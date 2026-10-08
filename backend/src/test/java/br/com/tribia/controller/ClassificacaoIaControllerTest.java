package br.com.tribia.controller;

import br.com.tribia.Fixtures;
import br.com.tribia.client.llm.LlmClient;
import br.com.tribia.client.llm.LlmException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Classificação com IA ponta a ponta. A IA é simulada por regras simples (cesta básica x integral) para o teste
 * ser determinístico e não gastar cota; o contrato com o Gemini de verdade está em GeminiContratoTest.
 */
@SpringBootTest(properties = {"tribia.seed.enabled=false", "tribia.calculo.modo=SIMPLIFICADA"})
@AutoConfigureMockMvc
@Transactional
class ClassificacaoIaControllerTest {

    /** Registra os pedidos e responde conforme o NCM. Pode ser derrubada para simular falha. */
    static class IaSimulada implements LlmClient {
        final List<String> pedidos = new ArrayList<>();
        volatile boolean fora;

        @Override
        public String gerarJson(String instrucoes, String pedido, Map<String, Object> esquema) {
            pedidos.add(pedido);
            if (fora) {
                throw new LlmException(LlmException.Tipo.INDISPONIVEL, "A IA está indisponível no momento (simulada).");
            }
            StringBuilder sb = new StringBuilder("[");
            Matcher m = Pattern.compile("nItem=(\\d+) \\| NCM (\\d+)").matcher(pedido);
            while (m.find()) {
                boolean cesta = m.group(2).startsWith("1006") || m.group(2).startsWith("0713") || m.group(2).startsWith("0401");
                sb.append(sb.length() > 1 ? "," : "").append("{\"nItem\":").append(m.group(1))
                        .append(",\"cst\":\"").append(cesta ? "200" : "000")
                        .append("\",\"cClassTrib\":\"").append(cesta ? "200003" : "000001")
                        .append("\",\"justificativa\":\"simulada\",\"confianca\":").append(cesta ? "0.92" : "0.74").append("}");
            }
            return sb.append("]").toString();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        IaSimulada iaSimulada() {
            return new IaSimulada();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    IaSimulada ia;

    @BeforeEach
    void limpar() {
        ia.pedidos.clear();
        ia.fora = false;
    }

    @Test
    void classificaOsItensPendentesComIaGravaNoCacheEMarcaComoNaoAceitos() throws Exception {
        long nota = importar(Fixtures.NFE_SAIDA_HACKATHON);

        mvc.perform(post("/api/notas/" + nota + "/classificar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classificados").value(8))
                .andExpect(jsonPath("$.porOrigem.IA").value(8))
                .andExpect(jsonPath("$.pendentes", hasSize(0)))
                .andExpect(jsonPath("$.avisos", hasSize(0)));
        assertThat(ia.pedidos).as("uma chamada por nota").hasSize(1);

        mvc.perform(get("/api/notas/" + nota))
                .andExpect(jsonPath("$.itens[0].classificacao.origem").value("IA"))
                .andExpect(jsonPath("$.itens[0].classificacao.cClassTrib").value("200003"))
                .andExpect(jsonPath("$.itens[0].classificacao.confianca").value(0.92))
                .andExpect(jsonPath("$.itens[0].classificacao.aceita").value(false))
                .andExpect(jsonPath("$.itens[0].classificacao.justificativa").value("simulada"))
                .andExpect(jsonPath("$.itens[7].classificacao.cClassTrib").value("000001"));
    }

    @Test
    void segundaNotaComOsMesmosProdutosUsaOCacheEOQueJaFoiClassificadoNaoVoltaParaIA() throws Exception {
        long primeira = importar(Fixtures.NFE_SAIDA_HACKATHON);
        mvc.perform(post("/api/notas/" + primeira + "/classificar")).andExpect(status().isOk());
        ia.pedidos.clear();

        // reclassificar a mesma nota não chama a IA: os itens já estão classificados
        mvc.perform(post("/api/notas/" + primeira + "/classificar"))
                .andExpect(jsonPath("$.classificados").value(8))
                .andExpect(jsonPath("$.porOrigem.IA").value(8));
        assertThat(ia.pedidos).isEmpty();

        // a farmácia (cliente 2) compra os mesmos produtos de outro fornecedor: cache global, sem IA
        long outra = importarPara(2, "farmacia-hackathon.xml", trocarCnpjs(Fixtures.texto(Fixtures.NFE_SAIDA_HACKATHON)));
        mvc.perform(post("/api/notas/" + outra + "/classificar"))
                .andExpect(jsonPath("$.porOrigem.CACHE").value(8))
                .andExpect(jsonPath("$.pendentes", hasSize(0)));
        assertThat(ia.pedidos).isEmpty();
        mvc.perform(get("/api/notas/" + outra))
                .andExpect(jsonPath("$.itens[0].classificacao.origem").value("CACHE"))
                .andExpect(jsonPath("$.itens[0].classificacao.aceita").value(false));
    }

    @Test
    void produtosRepetidosNaNotaVaoUmaVezSoParaAIA() throws Exception {
        // a nota de entrada tem 2 produtos; duplicamos o primeiro com a mesma descrição e NCM
        String xml = Fixtures.texto(Fixtures.NFE_SAIDA_HACKATHON);
        long nota = importarPara(1, "repetidos.xml", xml.replace("ARROZ TIPO 1 5KG", "FEIJAO CARIOCA 1KG")
                .replace("<NCM>10063021</NCM>", "<NCM>07133319</NCM>"));

        mvc.perform(post("/api/notas/" + nota + "/classificar")).andExpect(status().isOk());

        // 8 itens, 2 deles idênticos (feijão): 7 produtos distintos no pedido
        assertThat(ia.pedidos.get(0).lines().filter(l -> l.startsWith("nItem=")).count()).isEqualTo(7);
    }

    @Test
    void iaForaDoArDeixaOsItensPendentesComAvisoEORestoDoFluxoSegue() throws Exception {
        long nota = importar(Fixtures.NFE_SAIDA_HACKATHON);
        ia.fora = true;

        mvc.perform(post("/api/notas/" + nota + "/classificar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classificados").value(0))
                .andExpect(jsonPath("$.pendentes", hasSize(8)))
                .andExpect(jsonPath("$.avisos", hasItem(containsString("indisponível"))));

        mvc.perform(post("/api/notas/" + nota + "/calcular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itensCalculados").value(0))
                .andExpect(jsonPath("$.itensPendentes", hasSize(8)));

        // a IA volta: só reclassificar
        ia.fora = false;
        mvc.perform(post("/api/notas/" + nota + "/classificar"))
                .andExpect(jsonPath("$.classificados").value(8))
                .andExpect(jsonPath("$.pendentes", hasSize(0)));
    }

    @Test
    void depoisDeClassificadaANotaCalculaEMostraOComparativo() throws Exception {
        long nota = importar(Fixtures.NFE_SAIDA_HACKATHON);
        mvc.perform(post("/api/notas/" + nota + "/classificar")).andExpect(status().isOk());

        mvc.perform(post("/api/notas/" + nota + "/calcular"))
                .andExpect(jsonPath("$.itensCalculados").value(8))
                .andExpect(jsonPath("$.itensPendentes", hasSize(0)));
    }

    private static String trocarCnpjs(String xml) {
        // emitente passa a ser outro fornecedor e o destinatário vira a farmácia (cliente 2); chave nova
        return xml.replace("<CNPJ>10433218000193</CNPJ>", "<CNPJ>51938267000165</CNPJ>")
                .replace("<CNPJ>27865345000164</CNPJ>", "<CNPJ>45723174000110</CNPJ>")
                .replaceAll("Id=\"NFe\\d{44}\"", "Id=\"NFe" + chaveNova() + "\"");
    }

    private static String chaveNova() {
        return br.com.tribia.util.ChaveAcessoUtil.montar("35", "2608", "51938267000165", "55", 1, 777, 1, 11112222);
    }

    private long importar(String fixture) throws Exception {
        return importarPara(1, fixture, Fixtures.bytes(fixture));
    }

    private long importarPara(long cliente, String nome, String xml) throws Exception {
        return importarPara(cliente, nome, xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private long importarPara(long cliente, String nome, byte[] xml) throws Exception {
        String body = mvc.perform(multipart("/api/clientes/" + cliente + "/notas")
                        .file(new MockMultipartFile("arquivos", nome, "application/xml", xml)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(body);
        return n.get("importadas").get(0).get("id").asLong();
    }
}

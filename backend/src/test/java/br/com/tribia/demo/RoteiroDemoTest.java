package br.com.tribia.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O roteiro da apresentação, de ponta a ponta, 3 vezes seguidas ("demo roda 3 vezes sem erro", documento base).
 * Profile demo sem IA (os testes nunca usam a chave real): a classificação vem das respostas gravadas do Gemini,
 * exatamente o plano B da apresentação. Cálculo pelo método simplificado (mesmos valores da oficial).
 */
@SpringBootTest(properties = {"tribia.demo.habilitado=true", "tribia.demo.respostas-ia=true",
        "tribia.calculo.modo=SIMPLIFICADA"})
@AutoConfigureMockMvc
@WithUserDetails("admin@tribia.local")
class RoteiroDemoTest {

    static final Path AO_VIVO = Path.of("notas-demo-ao-vivo");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void roteiroCompletoTresVezesSeguidasComOsMesmosNumeros() throws Exception {
        List<String> resultados = new ArrayList<>();
        for (int ensaio = 1; ensaio <= 3; ensaio++) {
            resultados.add(ensaio());
        }
        assertThat(resultados).as("os três ensaios terminam com os mesmos números").containsOnly(resultados.get(0));
    }

    private String ensaio() throws Exception {
        // 0. volta ao estado inicial e confere o checklist
        mvc.perform(post("/api/demo/reiniciar").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notasImportadas").value(18))
                .andExpect(jsonPath("$.itensSemClassificacao").value(0));
        mvc.perform(get("/api/demo/status"))
                .andExpect(jsonPath("$.notas").value(18))
                .andExpect(jsonPath("$.respostasGravadasIa").value(9))
                .andExpect(jsonPath("$.iaConfigurada").value(false));

        // 1. tela inicial: clientes com indicadores
        mvc.perform(get("/api/clientes"))
                .andExpect(jsonPath("$[0].indicadores.liquido2027").value(118.08))
                .andExpect(jsonPath("$[1].indicadores.variacaoPct").value(-32.63));

        // 2. upload ao vivo de produtos novos: a IA classifica (aqui, pelas respostas gravadas) e a nota é recalculada
        long novos = upload(1, "1-distribuidora_nf1004.xml");
        mvc.perform(post("/api/notas/" + novos + "/classificar").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classificados").value(8))
                .andExpect(jsonPath("$.porOrigem.IA").value(8))
                .andExpect(jsonPath("$.pendentes").isEmpty())
                .andExpect(jsonPath("$.avisos", hasItem(containsString("Modo demonstração"))))
                .andExpect(jsonPath("$.calculo.itensCalculados").value(8));
        JsonNode itens = lerJson("/api/notas/" + novos).get("itens");
        assertThat(itens.get(4).get("classificacao").get("cClassTrib").asText()).as("banana: Anexo XV").isEqualTo("200014");
        assertThat(itens.get(6).get("classificacao").get("cClassTrib").asText()).as("azeite: integral").isEqualTo("000001");
        assertThat(itens.get(7).get("calculo").get("sujeitoIs").asBoolean()).as("cerveja no campo do IS").isTrue();

        // 3. a nota de teste do hackathon: quase tudo do cache, o detergente (NCM extinto) vai para a IA
        long hackathon = upload(1, "1-distribuidora_nfe_teste_hackathon.xml");
        mvc.perform(post("/api/notas/" + hackathon + "/classificar").with(csrf()))
                .andExpect(jsonPath("$.porOrigem.CACHE").value(7))
                .andExpect(jsonPath("$.porOrigem.IA").value(1))
                .andExpect(jsonPath("$.calculo.itensCalculados").value(8));

        // 4. revisão: 9 sugestões da IA (não aceitas) + dipirona do cache com confiança 0,65
        mvc.perform(get("/api/clientes/1/revisao")).andExpect(jsonPath("$.total").value(10));
        long carne = itens.get(0).get("id").asLong();
        mvc.perform(put("/api/itens/" + carne + "/classificacao").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aceitar\": true}").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.classificacao.revisada").value(true));
        mvc.perform(get("/api/clientes/1/revisao")).andExpect(jsonPath("$.total").value(9));

        // 5. painel e relatório já refletem tudo, sem recálculo manual
        JsonNode painel = lerJson("/api/clientes/1/dashboard");
        assertThat(painel.get("indicadores").get("pendentesRevisao").asInt()).isEqualTo(9);
        assertThat(painel.get("avisos").toString()).doesNotContain("sem cálculo");
        mvc.perform(get("/api/clientes/1/relatorio.csv")).andExpect(status().isOk());

        return painel.get("indicadores").toString() + painel.get("topItens").toString();
    }

    private long upload(long cliente, String arquivo) throws Exception {
        byte[] xml = Files.readAllBytes(AO_VIVO.resolve(arquivo));
        String body = mvc.perform(multipart("/api/clientes/" + cliente + "/notas")
                        .file(new MockMultipartFile("arquivos", arquivo, "application/xml", xml)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("importadas").get(0).get("id").asLong();
    }

    private JsonNode lerJson(String url) throws Exception {
        return json.readTree(mvc.perform(get(url))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}

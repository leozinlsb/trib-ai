package br.com.tribia.controller;

import br.com.tribia.Fixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R2 (decisão de 08/10/2026): compra cujo enquadramento corrigido na revisão difere do destacado pelo fornecedor.
 * Item: detergente da nota de entrada, base de 2027 = 360,00 − 64,80 − 5,94 − 27,36 = 261,90.
 * Crédito integral (000001): 24,70 + 0,13 + 0,13 = 24,96. Redução de 60% (200035): 9,88 + 0,05 + 0,05 = 9,98.
 * Padrão MENOR: vale o menor crédito entre a nota e a correção; NOTA segue a nota; REVISAO segue a correção.
 */
class CreditoCompraDivergenteTest {

    static final String INTEGRAL = "<CST>000</CST>\n            <cClassTrib>000001</cClassTrib>";
    static final String REDUCAO = "<CST>200</CST>\n            <cClassTrib>200035</cClassTrib>";

    abstract static class Base {
        @Autowired
        MockMvc mvc;

        @Autowired
        ObjectMapper json;

        /** Importa e classifica a compra, trocando o enquadramento que o fornecedor destacou na nota. */
        long compra(String destacado) throws Exception {
            String xml = Fixtures.texto(Fixtures.NFE_ENTRADA_IBSCBS).replace(INTEGRAL, destacado);
            String body = mvc.perform(multipart("/api/clientes/1/notas")
                            .file(new MockMultipartFile("arquivos", "compra.xml", "application/xml",
                                    xml.getBytes(StandardCharsets.UTF_8))).with(csrf()))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long nota = json.readTree(body).get("importadas").get(0).get("id").asLong();
            mvc.perform(post("/api/notas/" + nota + "/classificar").with(csrf())).andExpect(status().isOk());
            return nota;
        }

        JsonNode primeiroItem(long nota) throws Exception {
            return json.readTree(mvc.perform(get("/api/notas/" + nota)).andReturn().getResponse().getContentAsString())
                    .get("itens").get(0);
        }

        ResultActions corrigir(long nota, String cClassTrib) throws Exception {
            long item = primeiroItem(nota).get("id").asLong();
            return mvc.perform(put("/api/itens/" + item + "/classificacao").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cClassTrib\": \"" + cClassTrib + "\", \"aplicarAosIguais\": false}").with(csrf()));
        }
    }

    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", "tribia.calculo.modo=SIMPLIFICADA"})
    @AutoConfigureMockMvc
    @Transactional
    @WithUserDetails("admin@tribia.local")
    class PadraoMenor extends Base {

        @Test
        void correcaoParaReducaoMenorQueANotaUsaACorrecao() throws Exception {
            long nota = compra(INTEGRAL);
            corrigir(nota, "200035")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avisos", hasItem(containsString("enquadramento diferente do destacado"))))
                    .andExpect(jsonPath("$.item.calculo.imposto2027").value(9.98));
        }

        @Test
        void correcaoParaIntegralQuandoANotaTemReducaoMantemOCreditoMenorDaNota() throws Exception {
            long nota = compra(REDUCAO);
            corrigir(nota, "000001")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.item.classificacao.cClassTrib").value("000001"))
                    .andExpect(jsonPath("$.item.calculo.imposto2027").value(9.98)) // não sobe para 24,96
                    .andExpect(jsonPath("$.avisos", hasItem(containsString("menor valor entre a nota e a correção"))));
        }

        @Test
        void semDivergenciaNaoHaAvisoNemMudaOCredito() throws Exception {
            long nota = compra(INTEGRAL);
            mvc.perform(post("/api/notas/" + nota + "/calcular").with(csrf()))
                    .andExpect(jsonPath("$.avisos", not(hasItem(containsString("enquadramento diferente")))));
            assertThat(primeiroItem(nota).get("calculo").get("imposto2027").asDouble()).isEqualTo(24.96);
        }

        @Test
        void vendaNaoEhAfetada() throws Exception {
            mvc.perform(multipart("/api/clientes/1/notas")
                            .file(new MockMultipartFile("arquivos", "v.xml", "application/xml",
                                    Fixtures.bytes(Fixtures.NFE_SAIDA_HACKATHON))).with(csrf()))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.importadas", hasSize(1)));
        }
    }

    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", "tribia.calculo.modo=SIMPLIFICADA",
            "tribia.calculo.credito-compra-divergente=NOTA"})
    @AutoConfigureMockMvc
    @Transactional
    @WithUserDetails("admin@tribia.local")
    class SegueANota extends Base {

        @Test
        void creditoUsaOCodigoDaNotaMesmoComCorrecao() throws Exception {
            long nota = compra(INTEGRAL);
            corrigir(nota, "200035")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.item.calculo.imposto2027").value(24.96))
                    .andExpect(jsonPath("$.avisos", hasItem(containsString("segue o código destacado na nota"))));
        }
    }

    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", "tribia.calculo.modo=SIMPLIFICADA",
            "tribia.calculo.credito-compra-divergente=REVISAO"})
    @AutoConfigureMockMvc
    @Transactional
    @WithUserDetails("admin@tribia.local")
    class SegueARevisao extends Base {

        @Test
        void creditoUsaACorrecaoESemAviso() throws Exception {
            long nota = compra(REDUCAO);
            corrigir(nota, "000001")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.item.calculo.imposto2027").value(24.96))
                    .andExpect(jsonPath("$.avisos", not(hasItem(containsString("enquadramento diferente")))));
        }
    }
}

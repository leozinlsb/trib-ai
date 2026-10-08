package br.com.tribia.controller;

import br.com.tribia.Fixtures;
import br.com.tribia.service.classificacao.ClassificacaoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Classificação + cálculo ponta a ponta, com o cálculo simplificado (não depende da calculadora estar no ar). */
@SpringBootTest(properties = {"tribia.seed.enabled=false", "tribia.calculo.modo=SIMPLIFICADA"})
@AutoConfigureMockMvc
@Transactional
class ApuracaoControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ClassificacaoService classificacaoService;

    @Test
    void notaComIbsCbsNoXmlEhClassificadaPeloXmlECalculadaComoCredito() throws Exception {
        long nota = importar(Fixtures.NFE_ENTRADA_IBSCBS);

        mvc.perform(post("/api/notas/" + nota + "/classificar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classificados").value(2))
                .andExpect(jsonPath("$.porOrigem.XML").value(2))
                .andExpect(jsonPath("$.pendentes", hasSize(0)));

        // compra no Lucro Real: hoje 1,65% + 7,6%; em 2027 CBS 9,43% + IBS 0,05% + 0,05%
        mvc.perform(post("/api/notas/" + nota + "/calcular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origem").value("SIMPLIFICADA"))
                .andExpect(jsonPath("$.simulado").value(true))
                .andExpect(jsonPath("$.aliquotaCbs").value(9.43))
                .andExpect(jsonPath("$.itensCalculados").value(2))
                .andExpect(jsonPath("$.comparativo.hoje.debito").value(0))
                .andExpect(jsonPath("$.comparativo.hoje.credito").value(72.15))   // 33,30 + 38,85
                .andExpect(jsonPath("$.comparativo['2027'].credito").value(74.34)) // 34,31 + 40,03
                .andExpect(jsonPath("$.avisos", hasItem(containsString("estimativa"))));

        mvc.perform(get("/api/notas/" + nota))
                .andExpect(jsonPath("$.itens[0].classificacao.origem").value("XML"))
                .andExpect(jsonPath("$.itens[0].classificacao.cClassTrib").value("000001"))
                .andExpect(jsonPath("$.itens[0].classificacao.regime").value("INTEGRAL"))
                .andExpect(jsonPath("$.itens[0].classificacao.aceita").value(true))
                .andExpect(jsonPath("$.itens[0].calculo.natureza").value("CREDITO"))
                .andExpect(jsonPath("$.itens[0].calculo.vCbs").value(33.95))
                .andExpect(jsonPath("$.itens[0].calculo.impostoHoje").value(33.30))
                .andExpect(jsonPath("$.itens[0].calculo.imposto2027").value(34.31));
    }

    @Test
    void cacheClassificaOQueConheceEOrestoFicaPendenteForaDoCalculo() throws Exception {
        classificacaoService.gravarNoCache("10063021", "Arroz tipo 1 5kg", "200", "200003", "cesta básica",
                new BigDecimal("0.95"), "SEED", true);
        classificacaoService.gravarNoCache("18063210", "CHOCOLATE AO LEITE 90G", "000", "000001", "integral",
                new BigDecimal("0.90"), "SEED", true);
        long nota = importar(Fixtures.NFE_SAIDA_HACKATHON);

        mvc.perform(post("/api/notas/" + nota + "/classificar"))
                .andExpect(jsonPath("$.totalItens").value(8))
                .andExpect(jsonPath("$.porOrigem.CACHE").value(2))
                .andExpect(jsonPath("$.pendentes", hasSize(6)));

        // arroz (1.116,00, alíquota zero) + chocolate (599,00 integral): débito hoje = PIS/Cofins destacados
        mvc.perform(post("/api/notas/" + nota + "/calcular"))
                .andExpect(jsonPath("$.itensCalculados").value(2))
                .andExpect(jsonPath("$.itensPendentes", hasSize(6)))
                .andExpect(jsonPath("$.comparativo.hoje.debito").value(55.40))   // 0 + 9,88 + 45,52
                .andExpect(jsonPath("$.comparativo['2027'].debito").value(57.09)) // 0 + 56,49 + 0,30 + 0,30
                .andExpect(jsonPath("$.avisos", hasItem(containsString("sem classificação"))));

        mvc.perform(get("/api/notas/" + nota))
                .andExpect(jsonPath("$.itens[0].classificacao.origem").value("CACHE"))
                .andExpect(jsonPath("$.itens[0].classificacao.regime").value("ALIQUOTA_ZERO"))
                .andExpect(jsonPath("$.itens[0].calculo.imposto2027").value(0))
                .andExpect(jsonPath("$.itens[1].classificacao").doesNotExist())
                .andExpect(jsonPath("$.itens[1].calculo").doesNotExist());
    }

    @Test
    void cenarioDeCbsRecalculaEOValorInvalidoDa400() throws Exception {
        long nota = importar(Fixtures.NFE_ENTRADA_IBSCBS);
        mvc.perform(post("/api/notas/" + nota + "/classificar"));

        mvc.perform(post("/api/notas/" + nota + "/calcular").param("cbs", "8.8"))
                .andExpect(jsonPath("$.aliquotaCbs").value(8.8))
                .andExpect(jsonPath("$.comparativo['2027'].credito").value(69.42)) // 31,68 + 36,96 + 4 x IBS
                .andExpect(jsonPath("$.avisos", hasItem(containsString("Cenário simulado"))));

        mvc.perform(post("/api/notas/" + nota + "/calcular").param("cbs", "50"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recalculaTodasAsNotasDoCliente() throws Exception {
        long entrada = importar(Fixtures.NFE_ENTRADA_IBSCBS);
        mvc.perform(post("/api/notas/" + entrada + "/classificar"));

        mvc.perform(post("/api/clientes/1/calcular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notas").value(1))
                .andExpect(jsonPath("$.itensCalculados").value(2))
                .andExpect(jsonPath("$.comparativo['2027'].credito").value(74.34));
    }

    @Test
    void notaInexistenteDa404() throws Exception {
        mvc.perform(post("/api/notas/999/classificar")).andExpect(status().isNotFound());
        mvc.perform(post("/api/notas/999/calcular")).andExpect(status().isNotFound());
        mvc.perform(post("/api/clientes/999/calcular")).andExpect(status().isNotFound());
    }

    private long importar(String fixture) throws Exception {
        String body = mvc.perform(multipart("/api/clientes/1/notas")
                        .file(new MockMultipartFile("arquivos", fixture, "application/xml", Fixtures.bytes(fixture))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(body);
        return n.get("importadas").get(0).get("id").asLong();
    }
}

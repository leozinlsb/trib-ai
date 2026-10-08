package br.com.tribia.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Painel sobre os dados do seed (classificados e calculados na inicialização). Os valores batem com a calculadora
 * oficial: o cálculo simplificado usado aqui é conferido contra ela no SimplificadaVsOficialContratoTest.
 */
@SpringBootTest(properties = "tribia.calculo.modo=SIMPLIFICADA")
@AutoConfigureMockMvc
class DashboardControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void distribuidoraPagaMaisEm2027PeloRefrigeranteEPeloOleo() throws Exception {
        mvc.perform(get("/api/clientes/1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.nome").value("Distribuidora Fictícia"))
                .andExpect(jsonPath("$.cliente.regime").value("LUCRO_REAL"))
                .andExpect(jsonPath("$.periodo.de").value("2026-08"))
                .andExpect(jsonPath("$.periodo.ate").value("2026-10"))
                .andExpect(jsonPath("$.indicadores.faturamento").value(15915.32))
                .andExpect(jsonPath("$.indicadores.compras").value(9092.00))
                .andExpect(jsonPath("$.indicadores.liquidoHoje").value(33.22))
                .andExpect(jsonPath("$.indicadores.liquido2027").value(143.90))
                .andExpect(jsonPath("$.indicadores.variacaoPct").value(333.17))
                .andExpect(jsonPath("$.indicadores.credito2027").value(154.95))
                .andExpect(jsonPath("$.indicadores.saldoCredor").value(false))
                .andExpect(jsonPath("$.indicadores.pendentesRevisao").value(0))
                .andExpect(jsonPath("$.comparativo.hoje.debito").value(183.62))
                .andExpect(jsonPath("$.comparativo.hoje.credito").value(150.40))
                .andExpect(jsonPath("$.comparativo['2027'].debito").value(298.85))
                .andExpect(jsonPath("$.comparativo['2027'].credito").value(154.95))
                .andExpect(jsonPath("$.avisos", hasItem(containsString("estimativa"))));
    }

    @Test
    void farmaciaDoPresumidoGanhaCreditoEm2027ETemMedicamentosParaRevisar() throws Exception {
        mvc.perform(get("/api/clientes/2/dashboard"))
                .andExpect(jsonPath("$.indicadores.faturamento").value(14908.50))
                .andExpect(jsonPath("$.indicadores.compras").value(9211.00))
                .andExpect(jsonPath("$.comparativo.hoje.credito").value(0))
                .andExpect(jsonPath("$.indicadores.liquidoHoje").value(374.66))
                .andExpect(jsonPath("$.indicadores.credito2027").value(419.29))
                .andExpect(jsonPath("$.indicadores.liquido2027").value(298.49))
                .andExpect(jsonPath("$.indicadores.variacaoPct").value(-20.33))
                // medicamentos (0,65) e vitamina C (0,60) ficam abaixo de 0,70
                .andExpect(jsonPath("$.indicadores.pendentesRevisao").value(14))
                .andExpect(jsonPath("$.avisos", hasItem(containsString("14 item(ns) aguardando revisão"))));
    }

    @Test
    void lojaFicaPraticamenteEstavel() throws Exception {
        mvc.perform(get("/api/clientes/3/dashboard"))
                .andExpect(jsonPath("$.indicadores.faturamento").value(14395.80))
                .andExpect(jsonPath("$.indicadores.liquidoHoje").value(552.57))
                .andExpect(jsonPath("$.indicadores.liquido2027").value(545.10))
                .andExpect(jsonPath("$.indicadores.variacaoPct").value(-1.35));
    }

    @Test
    void filtraPorCompetencia() throws Exception {
        // agosto: saída 1001 (biscoito 38,74 + chocolate 44,33 de PIS/Cofins; cesta básica zero) e compra de cesta
        mvc.perform(get("/api/clientes/1/dashboard").param("de", "2026-08").param("ate", "2026-08"))
                .andExpect(jsonPath("$.periodo.de").value("2026-08"))
                .andExpect(jsonPath("$.periodo.ate").value("2026-08"))
                .andExpect(jsonPath("$.indicadores.faturamento").value(4687.90))
                .andExpect(jsonPath("$.indicadores.compras").value(5570.00))
                .andExpect(jsonPath("$.indicadores.liquidoHoje").value(83.07));

        mvc.perform(get("/api/clientes/1/dashboard").param("de", "2027-01"))
                .andExpect(jsonPath("$.indicadores.faturamento").value(0))
                .andExpect(jsonPath("$.indicadores.variacaoPct").doesNotExist())
                .andExpect(jsonPath("$.avisos", hasItem("Nenhuma nota no período.")));
    }

    @Test
    @Transactional
    void cenarioDeCbsRecalculadoApareceNoPainel() throws Exception {
        mvc.perform(post("/api/clientes/1/calcular").param("cbs", "8.8")).andExpect(status().isOk());

        mvc.perform(get("/api/clientes/1/dashboard"))
                .andExpect(jsonPath("$.avisos", hasItem("Cenário simulado: CBS de 8.8% em 2027.")));
    }

    @Test
    void listaDeClientesTrazOsIndicadoresResumidos() throws Exception {
        mvc.perform(get("/api/clientes"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].cnpj").value("10433218000193"))
                .andExpect(jsonPath("$[0].notas").value(6))
                .andExpect(jsonPath("$[0].indicadores.liquido2027").value(143.90))
                .andExpect(jsonPath("$[1].indicadores.variacaoPct").value(-20.33))
                .andExpect(jsonPath("$[1].indicadores.pendentesRevisao").value(14))
                .andExpect(jsonPath("$[2].indicadores.faturamento").value(14395.80));
    }

    @Test
    void periodoInvalidoDa400EClienteInexistente404() throws Exception {
        mvc.perform(get("/api/clientes/1/dashboard").param("de", "08/2026")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/clientes/1/dashboard").param("de", "2026-10").param("ate", "2026-08"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("depois de")));
        mvc.perform(get("/api/clientes/999/dashboard")).andExpect(status().isNotFound());
    }
}

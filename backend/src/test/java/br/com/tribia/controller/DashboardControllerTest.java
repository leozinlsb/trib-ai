package br.com.tribia.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
@WithUserDetails("admin@tribia.local")
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
                .andExpect(jsonPath("$.indicadores.liquido2027").value(118.08))
                .andExpect(jsonPath("$.indicadores.variacaoPct").value(255.45))
                .andExpect(jsonPath("$.indicadores.credito2027").value(112.73))
                .andExpect(jsonPath("$.indicadores.saldoCredor").value(false))
                .andExpect(jsonPath("$.indicadores.pendentesRevisao").value(0))
                .andExpect(jsonPath("$.comparativo.hoje.debito").value(183.62))
                .andExpect(jsonPath("$.comparativo.hoje.credito").value(150.40))
                .andExpect(jsonPath("$.comparativo['2027'].debito").value(230.81))
                .andExpect(jsonPath("$.comparativo['2027'].credito").value(112.73))
                .andExpect(jsonPath("$.avisos", hasItem(containsString("estimativa"))));
    }

    @Test
    void impostoLiquidoPorMes() throws Exception {
        // agosto: débito 2027 = biscoito 29,03 + chocolate 33,21 + óleo 18,28 (cesta básica zero); compras só de cesta
        mvc.perform(get("/api/clientes/1/dashboard"))
                .andExpect(jsonPath("$.porMes", hasSize(3)))
                .andExpect(jsonPath("$.porMes[0].competencia").value("2026-08"))
                .andExpect(jsonPath("$.porMes[0].faturamento").value(4687.90))
                .andExpect(jsonPath("$.porMes[0].liquidoHoje").value(83.07))
                .andExpect(jsonPath("$.porMes[0].liquido2027").value(80.52))
                .andExpect(jsonPath("$.porMes[2].competencia").value("2026-10"));
    }

    @Test
    void faturamentoPorRegime() throws Exception {
        mvc.perform(get("/api/clientes/1/dashboard"))
                .andExpect(jsonPath("$.porRegime[0].regime").value("ALIQUOTA_ZERO"))
                .andExpect(jsonPath("$.porRegime[0].valor").value(12491.80))
                .andExpect(jsonPath("$.porRegime[0].percentual").value(78.49))
                .andExpect(jsonPath("$.porRegime[?(@.regime == 'SUJEITO_IS')]").isEmpty())
                .andExpect(jsonPath("$.sujeitoIs.valor").value(959.04))
                .andExpect(jsonPath("$.sujeitoIs.itens").value(1))
                .andExpect(jsonPath("$.porRegime[?(@.regime == 'REDUZIDA')].valor").value(479.40)); // óleo de soja
    }

    @Test
    void produtosQueMaisMudamOImpostoEFornecedoresQueMaisGeramCredito() throws Exception {
        mvc.perform(get("/api/clientes/1/dashboard"))
                .andExpect(jsonPath("$.topItens[0].descricao").value("REFRIGERANTE COLA 2L"))
                .andExpect(jsonPath("$.topItens[0].impostoHoje").value(0))
                .andExpect(jsonPath("$.topItens[0].imposto2027").value(74.94))
                .andExpect(jsonPath("$.topItens[0].diferenca").value(74.94))
                .andExpect(jsonPath("$.topItens[0].regime").value("INTEGRAL"))
                .andExpect(jsonPath("$.topItens[1].descricao").value("CHOCOLATE AO LEITE 90G"))
                .andExpect(jsonPath("$.topItens[1].diferenca").value(-24.99))
                .andExpect(jsonPath("$.topItens[?(@.descricao == 'OLEO DE SOJA 900ML')].diferenca").value(18.28))
                .andExpect(jsonPath("$.topFornecedores[0].nome").value("INDUSTRIA FICTICIA DE DOCES E BISCOITOS LTDA"))
                .andExpect(jsonPath("$.topFornecedores[0].compras").value(1626.00))
                .andExpect(jsonPath("$.topFornecedores[0].creditoHoje").value(150.40))
                .andExpect(jsonPath("$.topFornecedores[0].credito2027").value(112.73))
                .andExpect(jsonPath("$.topFornecedores", hasSize(3)));
    }

    @Test
    void compraReduzOImpostoEApareceComSinalNegativoNoImpacto() throws Exception {
        // farmácia (Presumido): compras não davam crédito hoje e passam a dar em 2027
        mvc.perform(get("/api/clientes/2/dashboard"))
                .andExpect(jsonPath("$.topItens[?(@.descricao == 'FRALDA GERIATRICA TAMANHO G PCT 8')].impostoHoje").value(0.0))
                .andExpect(jsonPath("$.topItens[?(@.descricao == 'FRALDA GERIATRICA TAMANHO G PCT 8')].diferenca",
                        hasItem(org.hamcrest.Matchers.lessThan(0.0))));
    }

    @Test
    void farmaciaDoPresumidoGanhaCreditoEm2027ETemMedicamentosParaRevisar() throws Exception {
        mvc.perform(get("/api/clientes/2/dashboard"))
                .andExpect(jsonPath("$.indicadores.faturamento").value(14908.50))
                .andExpect(jsonPath("$.indicadores.compras").value(9211.00))
                .andExpect(jsonPath("$.comparativo.hoje.credito").value(0))
                .andExpect(jsonPath("$.indicadores.liquidoHoje").value(374.66))
                .andExpect(jsonPath("$.indicadores.credito2027").value(316.48))
                .andExpect(jsonPath("$.indicadores.liquido2027").value(252.40))
                .andExpect(jsonPath("$.indicadores.variacaoPct").value(-32.63))
                // medicamentos (0,65) e vitamina C (0,60) ficam abaixo de 0,70
                .andExpect(jsonPath("$.indicadores.pendentesRevisao").value(14))
                .andExpect(jsonPath("$.avisos", hasItem(containsString("14 item(ns) aguardando revisão"))));
    }

    @Test
    void lojaPagaMenosEm2027() throws Exception {
        mvc.perform(get("/api/clientes/3/dashboard"))
                .andExpect(jsonPath("$.indicadores.faturamento").value(14395.80))
                .andExpect(jsonPath("$.indicadores.liquidoHoje").value(552.57))
                .andExpect(jsonPath("$.indicadores.liquido2027").value(396.55))
                .andExpect(jsonPath("$.indicadores.variacaoPct").value(-28.24));
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
        mvc.perform(post("/api/clientes/1/calcular").param("cbs", "8.8").with(csrf())).andExpect(status().isOk());

        mvc.perform(get("/api/clientes/1/dashboard"))
                .andExpect(jsonPath("$.avisos", hasItem("Cenário simulado: CBS de 8.8% em 2027.")));
    }

    @Test
    void listaDeClientesTrazOsIndicadoresResumidos() throws Exception {
        mvc.perform(get("/api/clientes"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].cnpj").value("10433218000193"))
                .andExpect(jsonPath("$[0].notas").value(6))
                .andExpect(jsonPath("$[0].indicadores.liquido2027").value(118.08))
                .andExpect(jsonPath("$[1].indicadores.variacaoPct").value(-32.63))
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

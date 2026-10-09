package br.com.tribia.apipublica;

import br.com.tribia.apipublica.model.ChaveApi;
import br.com.tribia.apipublica.model.EscopoApi;
import br.com.tribia.apipublica.repository.ChaveApiRepository;
import br.com.tribia.apipublica.seguranca.ChavesApi;
import br.com.tribia.client.llm.LlmClient;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.Regime;
import br.com.tribia.repository.ClassificacaoRepository;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.repository.NotaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API pública v1, fase 2: classificação de produtos avulsos e simulação do cálculo de 2027, com a IA simulada e o
 * cálculo simplificado (os mesmos valores da calculadora oficial, conferidos no SimplificadaVsOficialContratoTest).
 */
class ApiPublicaFase2Test {

    static final String ISOLADO = "tribia.teste.contexto=api-publica-fase2";

    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", "tribia.calculo.modo=SIMPLIFICADA", ISOLADO})
    @AutoConfigureMockMvc
    class Fase2 {

        @Autowired
        MockMvc mvc;
        @Autowired
        ObjectMapper json;
        @Autowired
        ChaveApiRepository chaves;
        @Autowired
        ClienteRepository clientes;
        @Autowired
        NotaRepository notas;
        @Autowired
        ClassificacaoRepository classificacoes;
        @MockitoBean
        LlmClient llm;

        void iaResponde() {
            when(llm.gerarJson(anyString(), anyString(), anyMap()))
                    .thenAnswer(inv -> ApiPublicaNotasTest.respostaIa(inv.getArgument(1)));
        }

        Cliente novaEmpresa() {
            String cnpj = String.format("%014d", ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
            return clientes.save(Cliente.nova(cnpj, new Cliente.Dados("Empresa Teste " + cnpj, null, Regime.LUCRO_REAL,
                    null, "SP", null, "3550308", null, null, null, null)));
        }

        String novaChave(long clienteId, Set<EscopoApi> escopos, Integer cotaItensIa) {
            ChavesApi.ChaveGerada g = ChavesApi.gerar();
            chaves.save(new ChaveApi(g.prefixo(), g.hash(), "ERP de teste", clientes.findById(clienteId).orElseThrow(),
                    escopos, Instant.now(), "teste", null, null, null, null, cotaItensIa));
            return g.chaveCompleta();
        }

        String novaChave(long clienteId) {
            return novaChave(clienteId, EscopoApi.PADRAO, null);
        }

        ResultActions postar(String chave, String url, Object corpo) throws Exception {
            return mvc.perform(post(url).header("X-API-Key", chave).contentType("application/json")
                    .content(json.writeValueAsString(corpo)));
        }

        JsonNode corpo(ResultActions r) throws Exception {
            return json.readTree(r.andReturn().getResponse().getContentAsString());
        }

        static Map<String, Object> produto(String ref, String ncm, String descricao) {
            return Map.of("referencia", ref, "ncm", ncm, "descricao", descricao);
        }

        static Map<String, Object> produtos(Object... lista) {
            return Map.of("produtos", List.of(lista));
        }

        static Map<String, Object> refrigerante(BigDecimal icms) {
            return Map.of("referencia", "REF", "ncm", "2202.10.00", "cClassTrib", "000001", "valor", new BigDecimal("959.04"),
                    "icms", icms);
        }

        // ---------------- classificação ----------------

        @Test
        void classificaComIaAgrupaRepetidosEDepoisUsaOCacheDaEmpresa() throws Exception {
            iaResponde();
            Cliente empresa = novaEmpresa();
            String chave = novaChave(empresa.getId());
            Object pedido = produtos(produto("A", "1006.30.21", "ARROZ TIPO 1 5KG"),
                    produto("B", "22021000", "REFRIGERANTE COLA 2L"),
                    produto("C", "10063021", "Arroz tipo 1 5kg"));
            JsonNode r = corpo(postar(chave, "/api/v1/classificacoes", pedido).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.natureza").value("SUGESTAO_AUTOMATICA"))
                    .andExpect(jsonPath("$.itensClassificadosPorIa").value(2))
                    .andExpect(jsonPath("$.produtos.length()").value(3)));
            JsonNode arroz = r.get("produtos").get(0);
            assertThat(arroz.get("referencia").asText()).isEqualTo("A");
            assertThat(arroz.get("ncm").asText()).isEqualTo("10063021");
            assertThat(arroz.get("cClassTrib").asText()).isEqualTo("200003");
            assertThat(arroz.get("regime").asText()).isEqualTo("ALIQUOTA_ZERO");
            assertThat(arroz.get("origem").asText()).isEqualTo("IA");
            assertThat(arroz.get("situacao").asText()).isEqualTo("PENDENTE_REVISAO");
            assertThat(r.get("produtos").get(2).get("cClassTrib").asText()).as("repetido, mesma sugestão").isEqualTo("200003");
            assertThat(r.get("produtos").get(1).get("cClassTrib").asText()).isEqualTo("000001");
            assertThat(r.get("avisos").toString()).contains("não são classificação fiscal definitiva");
            verify(llm, times(1)).gerarJson(anyString(), anyString(), anyMap());

            clearInvocations(llm);
            postar(chave, "/api/v1/classificacoes", pedido).andExpect(status().isOk())
                    .andExpect(jsonPath("$.itensClassificadosPorIa").value(0))
                    .andExpect(jsonPath("$.produtos[0].origem").value("CACHE"));
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());
            mvc.perform(get("/api/v1/uso").header("X-API-Key", chave))
                    .andExpect(jsonPath("$.consumo.itensIaHoje").value(2));
        }

        @Test
        void cacheDeUmaEmpresaNaoServeParaOutra() throws Exception {
            iaResponde();
            Object pedido = produtos(produto("A", "22021000", "REFRIGERANTE GUARANA 2L"));
            postar(novaChave(novaEmpresa().getId()), "/api/v1/classificacoes", pedido)
                    .andExpect(jsonPath("$.produtos[0].origem").value("IA"));
            postar(novaChave(novaEmpresa().getId()), "/api/v1/classificacoes", pedido)
                    .andExpect(jsonPath("$.produtos[0].origem").value("IA"));
            verify(llm, times(2)).gerarJson(anyString(), anyString(), anyMap());
        }

        @Test
        void cotaQueNaoCabeDeixaSemClassificacaoECotaEsgotadaDa429() throws Exception {
            iaResponde();
            Cliente empresa = novaEmpresa();
            String chave = novaChave(empresa.getId(), EscopoApi.PADRAO, 2);
            postar(chave, "/api/v1/classificacoes", produtos(produto("A", "22021000", "REFRIGERANTE A"),
                    produto("B", "22021000", "REFRIGERANTE B"), produto("C", "22021000", "REFRIGERANTE C")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itensClassificadosPorIa").value(0))
                    .andExpect(jsonPath("$.produtos[0].situacao").value("SEM_CLASSIFICACAO"));
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());

            postar(chave, "/api/v1/classificacoes", produtos(produto("A", "22021000", "REFRIGERANTE A"),
                    produto("B", "22021000", "REFRIGERANTE B"))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.itensClassificadosPorIa").value(2));
            postar(chave, "/api/v1/classificacoes", produtos(produto("D", "22021000", "REFRIGERANTE D")))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.codigo").value("COTA_DIARIA_ITENS_IA_EXCEDIDA"));
        }

        @Test
        void cotaDeItensECompartilhadaComOsEnviosDeNota() throws Exception {
            iaResponde();
            Cliente empresa = novaEmpresa();
            String chave = novaChave(empresa.getId(), EscopoApi.PADRAO, 10);
            // nota com 8 itens novos para a IA
            postar(chave, "/api/v1/notas", Map.of("xml", ApiPublicaNotasTest.xmlNovo(empresa.getCnpj())))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.itensClassificadosPorIa").value(8));
            postar(chave, "/api/v1/classificacoes", produtos(produto("A", "22021000", "SUCO A"),
                    produto("B", "22021000", "SUCO B"), produto("C", "22021000", "SUCO C")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itensClassificadosPorIa").value(0))
                    .andExpect(jsonPath("$.avisos[2]").value(org.hamcrest.Matchers.containsString("restam 2")));
        }

        @Test
        void entradaInvalidaDa400SemChamarIa() throws Exception {
            String chave = novaChave(novaEmpresa().getId());
            postar(chave, "/api/v1/classificacoes", produtos(produto("A", "123", "X Y Z")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
            postar(chave, "/api/v1/classificacoes", Map.of("produtos", List.of()))
                    .andExpect(status().isBadRequest());
            Object[] muitos = new Object[51];
            for (int i = 0; i < 51; i++) muitos[i] = produto("P" + i, "22021000", "PRODUTO " + i);
            postar(chave, "/api/v1/classificacoes", produtos(muitos)).andExpect(status().isBadRequest());
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());
        }

        // ---------------- simulação ----------------

        @Test
        void simulaVendaComBaseSemIcmsEDaOMesmoValorDaNota() throws Exception {
            String chave = novaChave(1);
            long notasAntes = notas.count();
            long classificacoesAntes = classificacoes.count();
            JsonNode r = corpo(postar(chave, "/api/v1/calculos/simular",
                    Map.of("operacao", "VENDA", "itens", List.of(refrigerante(new BigDecimal("172.63")))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.natureza").value("PROJECAO_PENDENTE_VALIDACAO"))
                    .andExpect(jsonPath("$.origemCalculo").value("SIMPLIFICADA"))
                    .andExpect(jsonPath("$.aliquotasNominais.cbs").value(9.43)));
            // (959,04 − 172,63) = 786,41 → CBS 74,16 + IBS 0,39 + 0,39 = 74,94 (mesmo valor do refrigerante no painel)
            JsonNode item = r.get("itens").get(0);
            assertThat(item.get("base").decimalValue()).isEqualByComparingTo("786.41");
            assertThat(item.get("cbs").decimalValue()).isEqualByComparingTo("74.16");
            assertThat(item.get("ibsUf").decimalValue()).isEqualByComparingTo("0.39");
            assertThat(item.get("debito").decimalValue()).isEqualByComparingTo("74.94");
            assertThat(item.get("credito").isNull()).isTrue();
            assertThat(item.get("cst").asText()).as("deduzido da tabela").isEqualTo("000");
            // refrigerante está no campo do IS, mas a empresa revende (não é fabricante): IS zero
            assertThat(item.get("sujeitoImpostoSeletivo").asBoolean()).isTrue();
            assertThat(item.get("impostoSeletivo").decimalValue()).isEqualByComparingTo("0");
            assertThat(r.get("totais").get("debito").decimalValue()).isEqualByComparingTo("74.94");
            assertThat(r.get("avisos").toString()).contains("projeção pendente de validação fiscal");

            assertThat(notas.count()).as("simulação não grava nota").isEqualTo(notasAntes);
            assertThat(classificacoes.count()).isEqualTo(classificacoesAntes);
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());
        }

        @Test
        void compraDevolveCreditoReducaoDe60ECenarioDeCbs() throws Exception {
            String chave = novaChave(1);
            postar(chave, "/api/v1/calculos/simular",
                    Map.of("operacao", "COMPRA", "itens", List.of(refrigerante(new BigDecimal("172.63")))))
                    .andExpect(jsonPath("$.itens[0].credito").value(74.94))
                    .andExpect(jsonPath("$.itens[0].debito").doesNotExist())
                    .andExpect(jsonPath("$.totais.credito").value(74.94));

            // sabonete com redução de 60%: 1.000,00 × 9,43% × 40% = 37,72; IBS 0,20 + 0,20
            postar(chave, "/api/v1/calculos/simular", Map.of("operacao", "VENDA", "itens", List.of(
                    Map.of("ncm", "34011190", "cClassTrib", "200035", "valor", new BigDecimal("1000")))))
                    .andExpect(jsonPath("$.itens[0].regime").value("REDUZIDA"))
                    .andExpect(jsonPath("$.itens[0].reducaoCbs").value(60))
                    .andExpect(jsonPath("$.itens[0].cbs").value(37.72))
                    .andExpect(jsonPath("$.itens[0].debito").value(38.12));

            // cenário: CBS de 8,8% → 786,41 × 8,8% = 69,20
            postar(chave, "/api/v1/calculos/simular", Map.of("operacao", "VENDA", "aliquotaCbs", new BigDecimal("8.8"),
                    "itens", List.of(refrigerante(new BigDecimal("172.63")))))
                    .andExpect(jsonPath("$.aliquotasNominais.cbs").value(8.8))
                    .andExpect(jsonPath("$.itens[0].cbs").value(69.20));
        }

        @Test
        void simulacaoInvalidaDa400ComOCampo() throws Exception {
            String chave = novaChave(1);
            postar(chave, "/api/v1/calculos/simular", Map.of("operacao", "VENDA", "itens", List.of(
                    Map.of("cClassTrib", "999999", "valor", BigDecimal.TEN))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                    .andExpect(jsonPath("$.campos['itens[0].cClassTrib']").exists());
            postar(chave, "/api/v1/calculos/simular", Map.of("operacao", "VENDA", "itens", List.of(
                    Map.of("cst", "200", "cClassTrib", "000001", "valor", BigDecimal.TEN))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos['itens[0].cst']").exists());
            postar(chave, "/api/v1/calculos/simular", Map.of("operacao", "TRANSFERENCIA", "itens", List.of(
                    Map.of("cClassTrib", "000001", "valor", BigDecimal.TEN))))
                    .andExpect(status().isBadRequest());
            postar(chave, "/api/v1/calculos/simular", Map.of("operacao", "VENDA", "itens", List.of(
                    Map.of("cClassTrib", "000001", "valor", BigDecimal.ZERO))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void escoposDoClassificadorEDaCalculadora() throws Exception {
            String soCalcular = novaChave(1, EnumSet.of(EscopoApi.CALCULAR), null);
            String soClassificar = novaChave(1, EnumSet.of(EscopoApi.CLASSIFICAR), null);
            String antigas = novaChave(1, EnumSet.of(EscopoApi.ANALISES_CRIAR, EscopoApi.ANALISES_LER), null);
            Object simulacao = Map.of("operacao", "VENDA", "itens", List.of(refrigerante(BigDecimal.ZERO)));
            Object classificacao = produtos(produto("A", "22021000", "REFRIGERANTE"));

            postar(soCalcular, "/api/v1/calculos/simular", simulacao).andExpect(status().isOk());
            postar(soCalcular, "/api/v1/classificacoes", classificacao).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ESCOPO_INSUFICIENTE"));
            postar(soClassificar, "/api/v1/calculos/simular", simulacao).andExpect(status().isForbidden());
            postar(antigas, "/api/v1/calculos/simular", simulacao).andExpect(status().isForbidden());
            postar(antigas, "/api/v1/classificacoes", classificacao).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/uso").header("X-API-Key", soCalcular)).andExpect(status().isOk());
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());
        }
    }
}

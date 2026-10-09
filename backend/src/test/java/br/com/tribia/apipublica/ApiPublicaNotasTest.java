package br.com.tribia.apipublica;

import br.com.tribia.Fixtures;
import br.com.tribia.apipublica.model.ChaveApi;
import br.com.tribia.apipublica.model.EscopoApi;
import br.com.tribia.apipublica.repository.ChaveApiRepository;
import br.com.tribia.apipublica.repository.EnvioNotaApiRepository;
import br.com.tribia.apipublica.seguranca.ChavesApi;
import br.com.tribia.client.llm.LlmClient;
import br.com.tribia.exception.ApiException;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.Regime;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.repository.NotaRepository;
import br.com.tribia.security.AcessoService;
import br.com.tribia.security.EscopoIntegracao;
import br.com.tribia.service.calculo.CalculoService;
import br.com.tribia.util.ChaveAcessoUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
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
 * API pública v1, fase 1 (notas fiscais e comparativo): XML real da plataforma, banco H2 em memória, classificação e
 * cálculo reais (cálculo simplificado), IA simulada. Sem @Transactional, como os testes da análise: as transações
 * confirmam de verdade.
 */
class ApiPublicaNotasTest {

    /** Contexto próprio: as notas confirmadas aqui não podem aparecer nas contagens de outros testes. */
    static final String ISOLADO = "tribia.teste.contexto=api-publica-notas";

    /** Números de NF-e únicos por execução (a mesma nota duas vezes na empresa dá 409). */
    static final AtomicLong NUMERO = new AtomicLong(ThreadLocalRandom.current().nextLong(100_000, 900_000_000));

    /** Responde como o classificador espera: cesta básica (arroz, feijão, leite) 200003, o resto integral. */
    static String respostaIa(String pedido) {
        StringBuilder sb = new StringBuilder("[");
        Matcher m = Pattern.compile("nItem=(\\d+) \\| NCM (\\d+)").matcher(pedido);
        while (m.find()) {
            boolean cesta = m.group(2).startsWith("1006") || m.group(2).startsWith("0713") || m.group(2).startsWith("0401");
            sb.append(sb.length() > 1 ? "," : "").append("{\"nItem\":").append(m.group(1))
                    .append(",\"cst\":\"").append(cesta ? "200" : "000")
                    .append("\",\"cClassTrib\":\"").append(cesta ? "200003" : "000001")
                    .append("\",\"justificativa\":\"simulada\",\"confianca\":0.9}");
        }
        return sb.append("]").toString();
    }

    /** NF-e de saída da Distribuidora (cliente 1, 8 itens sem grupo IBS/CBS), com número e chave novos. */
    static String xmlNovo() {
        return xmlNovo("10433218000193");
    }

    /**
     * A mesma NF-e emitida por outra empresa. Cada cenário usa uma empresa nova: a IA grava no cache privado da
     * empresa, e a segunda nota com os mesmos produtos já não iria para a IA.
     */
    static String xmlNovo(String cnpjEmitente) {
        long numero = NUMERO.incrementAndGet();
        String chave = ChaveAcessoUtil.montar("35", "2608", cnpjEmitente, "55", 1, numero, 1, 12345678);
        return Fixtures.texto(Fixtures.NFE_SAIDA_HACKATHON)
                .replace("10433218000193", cnpjEmitente)
                .replaceAll("Id=\"NFe\\d{44}\"", "Id=\"NFe" + chave + "\"")
                .replaceAll("<nNF>\\d+</nNF>", "<nNF>" + numero + "</nNF>")
                .replaceAll("<cDV>\\d</cDV>", "<cDV>" + chave.charAt(43) + "</cDV>");
    }

    abstract static class Base {
        @Autowired
        MockMvc mvc;
        @Autowired
        ObjectMapper json;
        @Autowired
        ChaveApiRepository chaves;
        @Autowired
        EnvioNotaApiRepository envios;
        @Autowired
        NotaRepository notas;
        @Autowired
        ClienteRepository clientes;
        @MockitoBean
        LlmClient llm;

        /** Empresa nova só do teste (cache de classificação vazio). */
        Cliente novaEmpresa() {
            String cnpj = String.format("%014d", ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
            return clientes.save(Cliente.nova(cnpj, new Cliente.Dados("Empresa Teste " + cnpj, null, Regime.LUCRO_REAL,
                    null, "SP", null, null, null, null, null, null)));
        }

        void iaResponde() {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenAnswer(inv -> respostaIa(inv.getArgument(1)));
        }

        String novaChave(long clienteId) {
            return novaChave(clienteId, EscopoApi.PADRAO, null);
        }

        String novaChave(long clienteId, Set<EscopoApi> escopos, Integer cotaItensIa) {
            ChavesApi.ChaveGerada g = ChavesApi.gerar();
            Cliente c = clientes.findById(clienteId).orElseThrow();
            chaves.save(new ChaveApi(g.prefixo(), g.hash(), "ERP de teste", c, escopos, Instant.now(), "teste",
                    null, null, null, null, cotaItensIa));
            return g.chaveCompleta();
        }

        String corpo(String referencia, String xml) throws Exception {
            return json.writeValueAsString(Map.of("referenciaExterna", referencia, "xml", xml));
        }

        ResultActions enviar(String chave, String idempotencyKey, String corpo) throws Exception {
            MockHttpServletRequestBuilder r = post("/api/v1/notas").contentType("application/json").content(corpo);
            if (chave != null) {
                r.header("X-API-Key", chave);
            }
            if (idempotencyKey != null) {
                r.header("Idempotency-Key", idempotencyKey);
            }
            return mvc.perform(r);
        }

        ResultActions ler(String chave, String url) throws Exception {
            return mvc.perform(get(url).header("X-API-Key", chave));
        }

        JsonNode corpoDe(MvcResult r) throws Exception {
            return json.readTree(r.getResponse().getContentAsString());
        }
    }

    // =====================================================================================================
    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", "tribia.calculo.modo=SIMPLIFICADA", ISOLADO})
    @AutoConfigureMockMvc
    class Fluxo extends Base {

        @Autowired
        AcessoService acesso;

        @Test
        void enviaXmlClassificaComIaCalcula2027EConsulta() throws Exception {
            iaResponde();
            Cliente empresa = novaEmpresa();
            String chave = novaChave(empresa.getId());
            JsonNode n = corpoDe(enviar(chave, null, corpo("ERP-NF-1", xmlNovo(empresa.getCnpj())))
                    .andExpect(status().isAccepted())
                    .andExpect(header().exists("Location"))
                    .andExpect(header().string("Idempotent-Replayed", "false"))
                    .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                    .andExpect(jsonPath("$.finalizada").value(true))
                    .andExpect(jsonPath("$.natureza").value("PROJECAO_PENDENTE_VALIDACAO"))
                    .andExpect(jsonPath("$.referenciaExterna").value("ERP-NF-1"))
                    .andExpect(jsonPath("$.documento.operacao").value("VENDA"))
                    .andExpect(jsonPath("$.documento.natureza").value("DEBITO"))
                    .andExpect(jsonPath("$.itens.length()").value(8))
                    .andExpect(jsonPath("$.itensClassificadosPorIa").value(8))
                    .andReturn());

            // IA nunca nasce aceita: os 8 itens ficam pendentes de revisão humana, mas já entram no cálculo
            assertThat(n.get("itensPendentesRevisao").asInt()).isEqualTo(8);
            JsonNode arroz = n.get("itens").get(0);
            assertThat(arroz.get("classificacao").get("origem").asText()).isEqualTo("IA");
            assertThat(arroz.get("classificacao").get("situacao").asText()).isEqualTo("PENDENTE_REVISAO");
            assertThat(arroz.get("classificacao").get("cClassTrib").asText()).isEqualTo("200003");
            assertThat(arroz.get("calculo").get("imposto2027").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(n.get("comparativo").get("ano2027").get("debito").decimalValue()).isPositive();
            assertThat(n.get("avisos").toString()).contains("projeção pendente de validação fiscal");
            verify(llm, times(1)).gerarJson(anyString(), anyString(), anyMap());

            // não expõe ids internos (nota, itens, empresa)
            assertThat(n.toString()).doesNotContain("\"clienteId\"").doesNotContain("\"notaId\"");

            String id = n.get("id").asText();
            ler(chave, "/api/v1/notas/" + id).andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.itens.length()").value(8));
            ler(chave, "/api/v1/notas?referenciaExterna=ERP-NF-1").andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.itens[0].id").value(id));
            ler(chave, "/api/v1/uso").andExpect(status().isOk())
                    .andExpect(jsonPath("$.limites.cotaDiariaItensIa").value(500))
                    .andExpect(jsonPath("$.consumo.itensIaHoje").value(8))
                    .andExpect(jsonPath("$.consumo.itensIaRestantesHoje").value(492));

            // a nota é da plataforma: aparece para a empresa no site como qualquer outra
            String chaveAcesso = n.get("documento").get("chaveAcesso").asText();
            assertThat(notas.existsByClienteIdAndChave(empresa.getId(), chaveAcesso)).isTrue();

            // os mesmos produtos numa segunda nota saem do cache da empresa: não vão à IA nem gastam cota
            clearInvocations(llm);
            enviar(chave, null, corpo("ERP-NF-1B", xmlNovo(empresa.getCnpj()))).andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.itensClassificadosPorIa").value(0))
                    .andExpect(jsonPath("$.itens[0].classificacao.origem").value("CACHE"));
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());
            ler(chave, "/api/v1/uso").andExpect(jsonPath("$.consumo.itensIaHoje").value(8));
        }

        @Test
        void idempotenciaRepeteSemNovaIaEConteudoDiferenteDa409() throws Exception {
            iaResponde();
            Cliente empresa = novaEmpresa();
            String chave = novaChave(empresa.getId());
            String corpo = corpo("ERP-NF-2", xmlNovo(empresa.getCnpj()));
            String id = corpoDe(enviar(chave, "pedido-2", corpo).andExpect(status().isAccepted()).andReturn())
                    .get("id").asText();
            clearInvocations(llm);
            enviar(chave, "pedido-2", corpo).andExpect(status().isAccepted())
                    .andExpect(header().string("Idempotent-Replayed", "true"))
                    .andExpect(jsonPath("$.id").value(id));
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());
            enviar(chave, "pedido-2", corpo("ERP-NF-2", xmlNovo(empresa.getCnpj()))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_CONFLITO"))
                    .andExpect(jsonPath("$.notaId").value(id));
            ler(chave, "/api/v1/uso").andExpect(jsonPath("$.consumo.itensIaHoje").value(8));
        }

        @Test
        void notaRepetidaInvalidaOuDeOutraEmpresaNaoGravaNemChamaIa() throws Exception {
            iaResponde();
            String chave = novaChave(1);
            String xml = xmlNovo();
            enviar(chave, null, corpo("ERP-NF-3", xml)).andExpect(status().isAccepted());
            clearInvocations(llm);
            long antes = envios.count();
            long notasAntes = notas.count();

            enviar(chave, null, corpo("ERP-NF-3", xml)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("NOTA_JA_IMPORTADA"));
            enviar(chave, null, corpo("X", "<NFe>quebrado")).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("NOTA_INVALIDA"));
            // XML da Distribuidora enviado com a chave da Farmácia: a nota não pertence à empresa da chave
            enviar(novaChave(2), null, corpo("X", xmlNovo())).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("NOTA_INVALIDA"))
                    .andExpect(jsonPath("$.detail").value("Esta nota não pertence ao cliente"));
            enviar(chave, null, "{\"referenciaExterna\": \"X\"}").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));

            assertThat(envios.count()).isEqualTo(antes);
            assertThat(notas.count()).isEqualTo(notasAntes);
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());
        }

        @Test
        void cotaDeItensParaIaNaoCabeNaNotaClassificaSoXmlECacheEAvisa() throws Exception {
            iaResponde();
            Cliente empresa = novaEmpresa();
            String chave = novaChave(empresa.getId(), EscopoApi.PADRAO, 5);
            JsonNode n = corpoDe(enviar(chave, null, corpo("ERP-NF-4", xmlNovo(empresa.getCnpj()))).andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("CONCLUIDA")).andReturn());
            assertThat(n.get("itensClassificadosPorIa").asInt()).isZero();
            assertThat(n.get("itens").get(0).get("classificacao").get("situacao").asText()).isEqualTo("SEM_CLASSIFICACAO");
            assertThat(n.get("avisos").toString()).contains("Cota diária de itens para a IA");
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());
            ler(chave, "/api/v1/uso").andExpect(jsonPath("$.consumo.itensIaHoje").value(0));
        }

        @Test
        void cotaEsgotadaDa429SemGravarNada() throws Exception {
            iaResponde();
            Cliente empresa = novaEmpresa();
            String chave = novaChave(empresa.getId(), EscopoApi.PADRAO, 8);
            enviar(chave, null, corpo("ERP-NF-5", xmlNovo(empresa.getCnpj()))).andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.itensClassificadosPorIa").value(8));
            long antes = notas.count();
            enviar(chave, null, corpo("ERP-NF-6", xmlNovo(empresa.getCnpj()))).andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.codigo").value("COTA_DIARIA_ITENS_IA_EXCEDIDA"));
            assertThat(notas.count()).isEqualTo(antes);
        }

        @Test
        void escoposSeparamAnalisesDeNotas() throws Exception {
            String soAnalises = novaChave(1, EnumSet.of(EscopoApi.ANALISES_CRIAR, EscopoApi.ANALISES_LER), null);
            String soLerNotas = novaChave(1, EnumSet.of(EscopoApi.NOTAS_LER), null);
            String soEnviarNotas = novaChave(1, EnumSet.of(EscopoApi.NOTAS_ENVIAR), null);
            long antes = notas.count();

            enviar(soAnalises, null, corpo("X", xmlNovo())).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ESCOPO_INSUFICIENTE"));
            ler(soAnalises, "/api/v1/notas").andExpect(status().isForbidden());
            ler(soAnalises, "/api/v1/comparativo").andExpect(status().isForbidden());

            enviar(soLerNotas, null, corpo("X", xmlNovo())).andExpect(status().isForbidden());
            ler(soLerNotas, "/api/v1/comparativo").andExpect(status().isOk());
            ler(soLerNotas, "/api/v1/notas").andExpect(status().isOk());
            ler(soLerNotas, "/api/v1/uso").andExpect(status().isOk());
            ler(soLerNotas, "/api/v1/analises").andExpect(status().isForbidden());

            ler(soEnviarNotas, "/api/v1/notas").andExpect(status().isForbidden());
            assertThat(notas.count()).isEqualTo(antes);
        }

        @Test
        void outraEmpresaNaoVeANotaNemNaListagem() throws Exception {
            iaResponde();
            String daDistribuidora = novaChave(1);
            String daFarmacia = novaChave(2);
            String id = corpoDe(enviar(daDistribuidora, null, corpo("ERP-NF-7", xmlNovo()))
                    .andExpect(status().isAccepted()).andReturn()).get("id").asText();

            ler(daFarmacia, "/api/v1/notas/" + id).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NOTA_NAO_ENCONTRADA"));
            ler(daFarmacia, "/api/v1/notas?referenciaExterna=ERP-NF-7").andExpect(jsonPath("$.total").value(0));
            for (String malformado : new String[]{"1", "abc", "00000000-0000-0000-0000-000000000000"}) {
                ler(daDistribuidora, "/api/v1/notas/" + malformado).andExpect(status().isNotFound());
            }
            // o comparativo da Farmácia é o da Farmácia (sem notas neste contexto), não o da Distribuidora
            ler(daFarmacia, "/api/v1/comparativo").andExpect(status().isOk())
                    .andExpect(jsonPath("$.indicadores.faturamento").value(0));
        }

        @Test
        void comparativoDaEmpresaComValidacaoDoPeriodo() throws Exception {
            iaResponde();
            String chave = novaChave(1);
            enviar(chave, null, corpo("ERP-NF-8", xmlNovo())).andExpect(status().isAccepted());
            ler(chave, "/api/v1/comparativo?de=2026-08&ate=2026-08").andExpect(status().isOk())
                    .andExpect(jsonPath("$.natureza").value("PROJECAO_PENDENTE_VALIDACAO"))
                    .andExpect(jsonPath("$.de").value("2026-08"))
                    .andExpect(jsonPath("$.porMes[0].competencia").value("2026-08"))
                    .andExpect(jsonPath("$.comparativo.ano2027.debito").isNumber());
            ler(chave, "/api/v1/comparativo?de=08-2026").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
            ler(chave, "/api/v1/comparativo?de=2026-10&ate=2026-08").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
        }

        @Test
        void escopoDeIntegracaoNaoVazaParaForaDoBloco() {
            assertThatThrownBy(() -> acesso.atual()).isInstanceOf(ApiException.class);
            assertThat(EscopoIntegracao.executar(1L, () -> acesso.clienteAcessivel(1L).getId())).isEqualTo(1L);
            // dentro do bloco, só a empresa da chave: outra empresa dá 404 como para um usuário de empresa
            assertThatThrownBy(() -> EscopoIntegracao.executar(1L, () -> acesso.clienteAcessivel(2L)))
                    .isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> EscopoIntegracao.executar(1L, () -> {
                throw new IllegalStateException("erro no meio");
            })).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> acesso.atual()).as("removido mesmo depois de erro").isInstanceOf(ApiException.class);
        }
    }

    // =====================================================================================================
    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", "tribia.calculo.modo=SIMPLIFICADA", ISOLADO,
            "tribia.teste.variante=falha-calculo"})
    @AutoConfigureMockMvc
    class FalhaNoProcessamento extends Base {

        @MockitoSpyBean
        CalculoService calculo;

        @Test
        void falhaDepoisDaImportacaoViraFalhouComANotaPreservada() throws Exception {
            iaResponde();
            doThrow(new IllegalStateException("calculadora quebrada")).when(calculo).calcular(anyLong(), any());
            String chave = novaChave(1);
            JsonNode n = corpoDe(enviar(chave, null, corpo("ERP-NF-9", xmlNovo())).andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("FALHOU"))
                    .andExpect(jsonPath("$.finalizada").value(true))
                    .andExpect(jsonPath("$.erro.codigo").value("PROCESSAMENTO_FALHOU"))
                    .andReturn());
            assertThat(n.get("erro").get("mensagem").asText()).doesNotContain("calculadora quebrada");
            assertThat(notas.existsByClienteIdAndChave(1L, n.get("documento").get("chaveAcesso").asText())).isTrue();
        }
    }
}

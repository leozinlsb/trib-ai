package br.com.tribia.apipublica;

import br.com.tribia.apipublica.model.ChaveApi;
import br.com.tribia.apipublica.model.EscopoApi;
import br.com.tribia.apipublica.model.SolicitacaoApi;
import br.com.tribia.apipublica.repository.ChaveApiRepository;
import br.com.tribia.apipublica.repository.SolicitacaoApiRepository;
import br.com.tribia.apipublica.seguranca.ChavesApi;
import br.com.tribia.client.llm.LlmClient;
import br.com.tribia.client.llm.LlmException;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.Regime;
import br.com.tribia.model.Usuario;
import br.com.tribia.repository.AnaliseFiscalRepository;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.repository.UsuarioRepository;
import br.com.tribia.security.UsuarioDetailsService;
import br.com.tribia.security.UsuarioLogado;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API pública v1 de ponta a ponta (MockMvc + banco H2 em memória + motor de Inteligência Fiscal real), com a IA
 * simulada (o Gemini real nunca é chamado) e a JEV desligada. Sem @Transactional: as transações confirmam de verdade,
 * como em produção, para a concorrência e a idempotência valerem.
 */
class ApiPublicaV1Test {

    /** Mesma resposta de IA usada nos testes da Inteligência Fiscal: sabonete, 3401.11.90 (0,90) e 3401.19.00 (0,50). */
    static final String RESPOSTA_SABONETE = """
            {"suficiente": true, "faltando": [],
             "caracteristicas": ["sabão em barra", "uso para higiene pessoal"],
             "candidatas": [
               {"ncm": "3401.11.90", "descricao": "Sabões de toucador em barras - outros",
                "motivos": ["Barra de sabão para higiene pessoal (RGI 1, posição 34.01)"],
                "avaliacao": "Descrição compatível com sabão de toucador.", "confianca": 0.9},
               {"ncm": "34011900", "descricao": "Outros sabões em barras",
                "motivos": ["Sabão em barra não de toucador"], "avaliacao": "Menos provável.", "confianca": 0.5}],
             "regrasConsideradas": ["RGI 1", "RGI 6"],
             "observacoes": []}
            """;

    static final String CORPO = """
            {"referenciaExterna": "%s",
             "mercadoria": {"nome": "Sabonete de glicerina 90 g",
                            "descricao": "Sabonete em barra de glicerina para higiene pessoal, embalado individualmente.",
                            "composicao": "glicerina, óleo vegetal", "ncmInformada": "%s"}}
            """;

    /**
     * Marca os contextos destes testes: sem ela, o Spring reaproveitaria o contexto (e o banco em memória) de outros
     * testes com a mesma configuração, e as análises confirmadas aqui apareceriam nas contagens deles.
     */
    static final String ISOLADO = "tribia.teste.contexto=api-publica";

    static String corpo(String ref, String ncm) {
        return CORPO.formatted(ref, ncm);
    }

    abstract static class Base {
        @Autowired
        MockMvc mvc;
        @Autowired
        ObjectMapper json;
        @Autowired
        ChaveApiRepository chaves;
        @Autowired
        SolicitacaoApiRepository solicitacoes;
        @Autowired
        AnaliseFiscalRepository analisesFiscais;
        @Autowired
        ClienteRepository clientes;
        @Autowired
        PlatformTransactionManager transacoes;
        @MockitoBean
        LlmClient llm;

        String novaChave(long clienteId) {
            return novaChave(clienteId, EscopoApi.PADRAO, null, null, null, null);
        }

        String novaChave(long clienteId, Set<EscopoApi> escopos, Instant expiraEm, Integer porMinuto, Integer cota,
                         Integer simultaneas) {
            ChavesApi.ChaveGerada g = ChavesApi.gerar();
            Cliente c = clientes.findById(clienteId).orElseThrow();
            chaves.save(new ChaveApi(g.prefixo(), g.hash(), "ERP de teste", c, escopos, Instant.now(), "teste",
                    expiraEm, porMinuto, cota, simultaneas));
            return g.chaveCompleta();
        }

        /** Empresa nova só do teste: contagens e listagens não sofrem com dados de outros testes do mesmo contexto. */
        long novaEmpresa() {
            String cnpj = String.format("%014d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100_000_000_000_000L);
            return clientes.save(Cliente.nova(cnpj, new Cliente.Dados("Empresa Teste " + cnpj, null, Regime.LUCRO_REAL,
                    null, "SP", null, null, null, null, null, null))).getId();
        }

        ResultActions enviar(String chave, String idempotencyKey, String corpo) throws Exception {
            MockHttpServletRequestBuilder r = post("/api/v1/analises").contentType("application/json").content(corpo);
            if (chave != null) {
                r.header("X-API-Key", chave);
            }
            if (idempotencyKey != null) {
                r.header("Idempotency-Key", idempotencyKey);
            }
            return mvc.perform(r);
        }

        String criar(String chave, String corpo) throws Exception {
            return ler(enviar(chave, null, corpo).andExpect(status().isAccepted()).andReturn()).get("id").asText();
        }

        ResultActions consultar(String chave, String id) throws Exception {
            return mvc.perform(get("/api/v1/analises/" + id).header("X-API-Key", chave));
        }

        JsonNode ler(MvcResult r) throws Exception {
            return json.readTree(r.getResponse().getContentAsString());
        }

        long solicitacoesDaChave(String chave) {
            String prefixo = ChavesApi.prefixo(chave).orElseThrow();
            Long id = chaves.buscarPorPrefixo(prefixo).orElseThrow().getId();
            return new TransactionTemplate(transacoes).execute(s ->
                    solicitacoes.findAll().stream().filter(x -> x.getChave().getId().equals(id)).count());
        }

        Long analiseInterna(String publicoId) {
            return new TransactionTemplate(transacoes).execute(s -> solicitacoes.findAll().stream()
                    .filter(x -> x.getPublicoId().equals(publicoId)).findFirst().map(SolicitacaoApi::getAnalise)
                    .orElseThrow().getId());
        }
    }

    // =====================================================================================================
    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", ISOLADO})
    @AutoConfigureMockMvc
    class Autenticacao extends Base {

        @Test
        void semChaveDa401ComCodigoERequestIdSemCriarNada() throws Exception {
            long antes = analisesFiscais.count();
            enviar(null, null, corpo("A1", "")).andExpect(status().isUnauthorized())
                    .andExpect(header().exists("X-Request-Id"))
                    .andExpect(header().string("WWW-Authenticate", "ApiKey header=\"X-API-Key\""))
                    .andExpect(jsonPath("$.codigo").value("CHAVE_AUSENTE"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
            assertThat(analisesFiscais.count()).isEqualTo(antes);
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());
        }

        @Test
        void chaveMalformadaOuComSegredoErradoDa401Igual() throws Exception {
            String valida = novaChave(1);
            String segredoErrado = valida.substring(0, valida.length() - 4) + (valida.endsWith("AAAA") ? "BBBB" : "AAAA");
            String prefixoInexistente = ChavesApi.gerar().chaveCompleta();
            for (String c : List.of("abc", "Bearer " + valida, segredoErrado, prefixoInexistente)) {
                MvcResult r = enviar(c, null, corpo("A2", "")).andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.codigo").value("CHAVE_INVALIDA")).andReturn();
                assertThat(r.getResponse().getContentAsString()).doesNotContain(valida.substring(20));
            }
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());
        }

        @Test
        void requestIdDoIntegradorVoltaSeForSeguro() throws Exception {
            String chave = novaChave(1);
            mvc.perform(get("/api/v1/uso").header("X-API-Key", chave).header("X-Request-Id", "erp-req-12345"))
                    .andExpect(status().isOk()).andExpect(header().string("X-Request-Id", "erp-req-12345"));
            MvcResult r = mvc.perform(get("/api/v1/uso").header("X-API-Key", chave).header("X-Request-Id", "<script>"))
                    .andExpect(status().isOk()).andReturn();
            assertThat(r.getResponse().getHeader("X-Request-Id")).isNotEqualTo("<script>").matches("[0-9a-f-]{36}");
        }

        @Test
        void chaveRevogadaDa401ENaoCriaAnalise() throws Exception {
            String chave = novaChave(1);
            ChaveApi c = chaves.buscarPorPrefixo(ChavesApi.prefixo(chave).orElseThrow()).orElseThrow();
            c.revogar(Instant.now(), "teste");
            chaves.save(c);
            long antes = analisesFiscais.count();
            enviar(chave, null, corpo("A3", "")).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CHAVE_REVOGADA"));
            consultar(chave, UUID.randomUUID().toString()).andExpect(status().isUnauthorized());
            assertThat(analisesFiscais.count()).isEqualTo(antes);
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());
        }

        @Test
        void chaveExpiradaDa401() throws Exception {
            String chave = novaChave(1, EscopoApi.PADRAO, Instant.now().minus(Duration.ofMinutes(1)), null, null, null);
            enviar(chave, null, corpo("A4", "")).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CHAVE_EXPIRADA"));
        }

        @Test
        void empresaDesativadaDa403() throws Exception {
            Cliente c = clientes.save(Cliente.nova("11222333000181", new Cliente.Dados("Empresa Desativada Teste",
                    null, Regime.LUCRO_REAL, null, "SP", null, null, null, null, null, null)));
            String chave = novaChave(c.getId());
            c.desativar();
            clientes.save(c);
            enviar(chave, null, corpo("A5", "")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("EMPRESA_DESATIVADA"));
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());
        }

        @Test
        void escoposMinimos() throws Exception {
            String soLeitura = novaChave(1, EnumSet.of(EscopoApi.ANALISES_LER), null, null, null, null);
            String soCriacao = novaChave(1, EnumSet.of(EscopoApi.ANALISES_CRIAR), null, null, null, null);
            long antes = analisesFiscais.count();
            enviar(soLeitura, null, corpo("A6", "")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ESCOPO_INSUFICIENTE"));
            assertThat(analisesFiscais.count()).isEqualTo(antes);
            mvc.perform(get("/api/v1/uso").header("X-API-Key", soCriacao)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ESCOPO_INSUFICIENTE"));
            mvc.perform(get("/api/v1/uso").header("X-API-Key", soLeitura)).andExpect(status().isOk());
        }

        @Test
        void sessaoDaPlataformaNaoAbreApiPublicaEChaveNaoAbreRotaInterna(@Autowired UsuarioDetailsService detalhes)
                throws Exception {
            var admin = user(detalhes.loadUserByUsername("admin@tribia.local").semSenha());
            mvc.perform(get("/api/v1/uso").with(admin)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CHAVE_AUSENTE"));
            String chave = novaChave(1);
            mvc.perform(get("/api/clientes").header("X-API-Key", chave)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/admin/chaves-api").header("X-API-Key", chave)).andExpect(status().isUnauthorized());
        }

        @Test
        void apiPublicaNaoCriaSessaoNemCookie() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            MvcResult r = enviar(novaChave(1), null, corpo("A7", "")).andExpect(status().isAccepted()).andReturn();
            assertThat(r.getResponse().getCookies()).isEmpty();
            assertThat(r.getRequest().getSession(false)).isNull();
        }
    }

    // =====================================================================================================
    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", ISOLADO})
    @AutoConfigureMockMvc
    class FluxoComMotorFiscal extends Base {

        @Autowired
        UsuarioDetailsService detalhes;

        @Test
        void criaProcessaEConsultaUsandoOMotorDaPlataforma() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            String chave = novaChave(1);

            MvcResult criado = enviar(chave, "idem-fluxo-1", corpo("SKU-1", "3401.11.90"))
                    .andExpect(status().isAccepted())
                    .andExpect(header().string("Idempotent-Replayed", "false"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andReturn();
            JsonNode c = ler(criado);
            String id = c.get("id").asText();
            assertThat(id).matches("[0-9a-f-]{36}");
            assertThat(criado.getResponse().getHeader("Location")).isEqualTo("/api/v1/analises/" + id);

            JsonNode d = ler(consultar(chave, id).andExpect(status().isOk()).andReturn());
            assertThat(d.get("status").asText()).isEqualTo("CONCLUIDA");
            assertThat(d.get("finalizada").asBoolean()).isTrue();
            assertThat(d.get("referenciaExterna").asText()).isEqualTo("SKU-1");
            assertThat(d.get("mercadoria").get("ncmInformada").asText()).isEqualTo("34011190");
            JsonNode r = d.get("resultado");
            assertThat(r.get("natureza").asText()).isEqualTo("SUGESTAO_AUTOMATICA_VERIFICADA");
            assertThat(r.get("ncmSugerida").asText()).isEqualTo("34011190");
            assertThat(r.get("ncmSugeridaFormatada").asText()).isEqualTo("3401.11.90");
            assertThat(r.get("situacaoValidacao").asText()).isEqualTo("VALIDADO_VERIFICACOES");
            assertThat(r.get("descricaoOficial").asText()).contains("De toucador");
            assertThat(r.get("alternativas").findValuesAsText("ncm")).containsExactly("34011190", "34011900");
            assertThat(r.get("validacao").get("vigencia").get("inicio").asText()).isEqualTo("2022-04-01");
            assertThat(r.get("fundamentacao").get("limitacoes").toString()).contains("não é classificação fiscal definitiva");
            assertThat(r.get("fontes").findValuesAsText("titulo")).contains("Nomenclatura Comum do Mercosul (NCM)");
            assertThat(d.get("revisaoHumana").get("situacao").asText()).isEqualTo("NAO_SOLICITADA");
            assertThat(d.get("erro").isNull()).isTrue();
            assertThat(d.get("avisos").toString()).contains("não é classificação fiscal definitiva");
            // sem dado interno: id numérico da análise, empresa, prompt
            assertThat(d.toString()).doesNotContain("clienteId").doesNotContain("<dados_").doesNotContain("downloadUrl");

            // a análise é a mesma da plataforma: o administrador a vê na Inteligência Fiscal da empresa 1
            Long interna = analiseInterna(id);
            var admin = user(detalhes.loadUserByUsername("admin@tribia.local").semSenha());
            mvc.perform(get("/api/analises-fiscais/" + interna).with(admin)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.clienteId").value(1))
                    .andExpect(jsonPath("$.status").value("CONCLUIDA"));
            verify(llm, times(1)).gerarJson(anyString(), anyString(), anyMap());
        }

        @Test
        void divergenciaVaiParaRevisaoEARevisaoHumanaDaPlataformaApareceNaApi() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            String chave = novaChave(1);
            String id = criar(chave, corpo("SKU-2", "33073000"));

            JsonNode d = ler(consultar(chave, id).andReturn());
            assertThat(d.get("status").asText()).isEqualTo("AGUARDANDO_REVISAO");
            assertThat(d.get("resultado").get("natureza").asText()).isEqualTo("SUGESTAO_AUTOMATICA_PENDENTE_REVISAO");
            assertThat(d.get("revisaoHumana").get("situacao").asText()).isEqualTo("PENDENTE");
            assertThat(d.get("mensagem").asText()).contains("3307.30.00");
            assertThat(d.get("resultado").get("validacao").get("divergencias").toString()).contains("3307.30.00");

            // uma pessoa da empresa decide na plataforma (rota interna, sessão + CSRF)
            var admin = user(detalhes.loadUserByUsername("admin@tribia.local").semSenha());
            mvc.perform(put("/api/analises-fiscais/" + analiseInterna(id) + "/revisao").with(admin).with(csrf())
                            .contentType("application/json")
                            .content("{\"ncm\": \"3401.19.00\", \"observacao\": \"Não é de toucador: uso doméstico.\"}"))
                    .andExpect(status().isOk());

            JsonNode depois = ler(consultar(chave, id).andReturn());
            assertThat(depois.get("status").asText()).isEqualTo("CONCLUIDA");
            assertThat(depois.get("resultado").get("natureza").asText()).isEqualTo("DECISAO_REVISAO_HUMANA");
            assertThat(depois.get("resultado").get("ncmSugerida").asText()).isEqualTo("34011190"); // evidência preservada
            JsonNode rev = depois.get("revisaoHumana");
            assertThat(rev.get("situacao").asText()).isEqualTo("REALIZADA");
            assertThat(rev.get("decisao").asText()).isEqualTo("ALTERADA");
            assertThat(rev.get("ncmDecidida").asText()).isEqualTo("34011900");
            assertThat(rev.get("totalRevisoes").asInt()).isEqualTo(1);
            assertThat(depois.toString()).doesNotContain("admin@tribia.local"); // quem revisou não sai na API pública
        }

        @Test
        void falhaDaIaViraFalhouComMensagemSeguraSemStackTrace() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap()))
                    .thenThrow(new LlmException(LlmException.Tipo.INDISPONIVEL, "503 do provedor com detalhe interno"));
            String chave = novaChave(1);
            String id = criar(chave, corpo("SKU-3", ""));
            JsonNode d = ler(consultar(chave, id).andReturn());
            assertThat(d.get("status").asText()).isEqualTo("FALHOU");
            assertThat(d.get("finalizada").asBoolean()).isTrue();
            assertThat(d.get("resultado").isNull()).isTrue();
            assertThat(d.get("erro").get("codigo").asText()).isEqualTo("ANALISE_FALHOU");
            assertThat(d.get("erro").get("podeRepetir").asBoolean()).isTrue();
            assertThat(d.get("erro").get("mensagem").asText()).contains("indisponível");
            assertThat(d.toString()).doesNotContain("detalhe interno").doesNotContain("Exception").doesNotContain("at br.");
            assertThat(d.get("revisaoHumana").get("situacao").asText()).isEqualTo("NAO_APLICAVEL");
        }

        @Test
        void informacoesInsuficientesTemStatusProprio() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn("""
                    {"suficiente": false, "faltando": ["composição percentual"], "caracteristicas": [],
                     "candidatas": [], "regrasConsideradas": [], "observacoes": []}
                    """);
            String chave = novaChave(1);
            JsonNode d = ler(consultar(chave, criar(chave, corpo("SKU-4", ""))).andReturn());
            assertThat(d.get("status").asText()).isEqualTo("INFORMACOES_INSUFICIENTES");
            assertThat(d.get("erro").get("codigo").asText()).isEqualTo("INFORMACOES_INSUFICIENTES");
            assertThat(d.get("erro").get("mensagem").asText()).contains("composição percentual");
        }

        @Test
        void entradaInvalidaDa400SemCriarAnaliseNemChamarIa() throws Exception {
            String chave = novaChave(1);
            long antes = analisesFiscais.count();
            enviar(chave, null, "{}").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                    .andExpect(jsonPath("$.campos.mercadoria").exists());
            enviar(chave, null, "{\"mercadoria\": {\"nome\": \"X\", \"descricao\": \"curta\"}}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos['mercadoria.descricao']").exists());
            enviar(chave, null, corpo("SKU-5", "123")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos['mercadoria.ncmInformada']").exists());
            enviar(chave, null, corpo("ref com espaço", "")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.referenciaExterna").exists());
            enviar(chave, null, "{nao é json").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
            mvc.perform(post("/api/v1/analises").header("X-API-Key", chave).contentType("text/plain").content("oi"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.codigo").value("TIPO_CONTEUDO_NAO_SUPORTADO"));
            enviar(chave, "idem com espaço", corpo("SKU-6", "")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("IDEMPOTENCY_KEY_INVALIDA"));
            assertThat(analisesFiscais.count()).isEqualTo(antes);
            assertThat(solicitacoesDaChave(chave)).isZero();
            verify(llm, never()).gerarJson(anyString(), anyString(), anyMap());
        }

        @Test
        void listagemFiltraPorReferenciaEUsoMostraConsumo() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            String chave = novaChave(novaEmpresa(), EscopoApi.PADRAO, null, null, 10, null);
            criar(chave, corpo("LISTA-A", ""));
            criar(chave, corpo("LISTA-B", ""));
            criar(chave, corpo("LISTA-A", ""));
            mvc.perform(get("/api/v1/analises").param("referenciaExterna", "LISTA-A").header("X-API-Key", chave))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.itens[0].referenciaExterna").value("LISTA-A"))
                    .andExpect(jsonPath("$.itens[0].status").value("CONCLUIDA"));
            mvc.perform(get("/api/v1/analises").param("tamanho", "500").header("X-API-Key", chave))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
            mvc.perform(get("/api/v1/uso").header("X-API-Key", chave)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.empresa.cnpj").exists())
                    .andExpect(jsonPath("$.limites.cotaDiariaAnalises").value(10))
                    .andExpect(jsonPath("$.consumo.analisesCriadasHoje").value(3))
                    .andExpect(jsonPath("$.consumo.restantesHoje").value(7))
                    .andExpect(jsonPath("$.chave.prefixo").value(ChavesApi.prefixo(chave).orElseThrow()));
        }
    }

    // =====================================================================================================
    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", ISOLADO})
    @AutoConfigureMockMvc
    class Multiempresa extends Base {

        @Test
        void empresaBNaoEnxergaAnaliseDaEmpresaA() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            String chaveA = novaChave(1);
            String outraChaveA = novaChave(1, EnumSet.of(EscopoApi.ANALISES_LER), null, null, null, null);
            String chaveB = novaChave(novaEmpresa());
            String idA = criar(chaveA, corpo("ISOL-A", ""));

            consultar(chaveA, idA).andExpect(status().isOk());
            consultar(outraChaveA, idA).andExpect(status().isOk()); // mesma empresa, outra chave
            String outra = consultar(chaveB, idA).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("ANALISE_NAO_ENCONTRADA"))
                    .andReturn().getResponse().getContentAsString();
            String inexistente = consultar(chaveB, UUID.randomUUID().toString()).andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();
            // a resposta para "de outra empresa" é a mesma de "não existe" (não revela que o id existe)
            assertThat(json.readTree(outra).get("detail")).isEqualTo(json.readTree(inexistente).get("detail"));
            assertThat(outra).doesNotContain("Sabonete").doesNotContain("ISOL-A");

            mvc.perform(get("/api/v1/analises").header("X-API-Key", chaveB))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
            mvc.perform(get("/api/v1/analises").param("referenciaExterna", "ISOL-A").header("X-API-Key", chaveB))
                    .andExpect(jsonPath("$.total").value(0));
        }

        @Test
        void idsManipuladosNaoContornamAutorizacao() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            String chaveA = novaChave(1);
            String chaveB = novaChave(2);
            String idA = criar(chaveA, corpo("ISOL-B", ""));
            Long interno = analiseInterna(idA);
            for (String tentativa : List.of(String.valueOf(interno), idA.toUpperCase(), idA + "%00", "..%2F..%2Fclientes",
                    "' or 1=1 --", "00000000-0000-0000-0000-000000000000")) {
                // 404 do serviço ou 400 do firewall do Spring Security (barra/nulo codificados): nunca 200
                int st = consultar(chaveB, tentativa).andReturn().getResponse().getStatus();
                assertThat(st).as(tentativa).isIn(400, 404);
            }
            // a chave B não alcança a rota interna da análise da empresa A
            mvc.perform(get("/api/analises-fiscais/" + interno).header("X-API-Key", chaveB))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void empresaNoCorpoEIgnoradaValeSempreAEmpresaDaChave() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            String chaveB = novaChave(2);
            String corpoComEmpresa = """
                    {"clienteId": 1, "empresa": 1, "cnpj": "10433218000193",
                     "mercadoria": {"nome": "Sabonete", "clienteId": 1,
                                    "descricao": "Sabonete em barra de glicerina para higiene pessoal."}}
                    """;
            String id = ler(enviar(chaveB, null, corpoComEmpresa).andExpect(status().isAccepted()).andReturn())
                    .get("id").asText();
            Long cliente = new TransactionTemplate(transacoes).execute(s -> solicitacoes.findAll().stream()
                    .filter(x -> x.getPublicoId().equals(id)).findFirst().orElseThrow().getAnalise().getCliente().getId());
            assertThat(cliente).isEqualTo(2L);
        }
    }

    // =====================================================================================================
    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", ISOLADO})
    @AutoConfigureMockMvc
    class Idempotencia extends Base {

        @Test
        void repeticaoDevolveAMesmaAnaliseSemNovaChamadaAIa() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            String chave = novaChave(1);
            String id1 = ler(enviar(chave, "pedido-42", corpo("IDEM-1", "3401.11.90")).andReturn()).get("id").asText();
            // mesmo conteúdo, formatação diferente (espaços, NCM sem pontos): mesma solicitação
            String equivalente = """
                    {"mercadoria": {"descricao": "  Sabonete em barra de glicerina para higiene pessoal, embalado individualmente.",
                     "nome": "Sabonete de glicerina 90 g ", "composicao": "glicerina, óleo vegetal", "ncmInformada": "34011190"},
                     "referenciaExterna": "IDEM-1"}
                    """;
            MvcResult r2 = enviar(chave, "pedido-42", equivalente).andExpect(status().isAccepted())
                    .andExpect(header().string("Idempotent-Replayed", "true")).andReturn();
            assertThat(ler(r2).get("id").asText()).isEqualTo(id1);
            assertThat(ler(r2).get("status").asText()).isEqualTo("CONCLUIDA");
            assertThat(solicitacoesDaChave(chave)).isEqualTo(1);
            verify(llm, times(1)).gerarJson(anyString(), anyString(), anyMap());
        }

        @Test
        void mesmaChaveComCorpoDiferenteDa409() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            String chave = novaChave(1);
            String id = criarCom(chave, "pedido-43", corpo("IDEM-2", ""));
            enviar(chave, "pedido-43", corpo("IDEM-2-OUTRO", "")).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_CONFLITO"))
                    .andExpect(jsonPath("$.analiseId").value(id));
            assertThat(solicitacoesDaChave(chave)).isEqualTo(1);
            verify(llm, times(1)).gerarJson(anyString(), anyString(), anyMap());
        }

        @Test
        void chavesDeApiDiferentesNaoCompartilhamIdempotencyKey() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            String a = criarCom(novaChave(1), "pedido-comum", corpo("IDEM-3", ""));
            String b = criarCom(novaChave(2), "pedido-comum", corpo("IDEM-3", ""));
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void requisicoesSimultaneasComAMesmaChaveCriamUmaAnaliseSo() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            String chave = novaChave(1, EscopoApi.PADRAO, null, 1000, null, null);
            int n = 8;
            CountDownLatch largada = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(n);
            try {
                List<Future<MvcResult>> futuros = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    Callable<MvcResult> c = () -> {
                        largada.await();
                        return enviar(chave, "pedido-concorrente", corpo("IDEM-4", "")).andReturn();
                    };
                    futuros.add(pool.submit(c));
                }
                largada.countDown();
                List<String> ids = new ArrayList<>();
                int repetidas = 0;
                for (Future<MvcResult> f : futuros) {
                    MvcResult r = f.get(60, TimeUnit.SECONDS);
                    assertThat(r.getResponse().getStatus()).isEqualTo(202);
                    ids.add(ler(r).get("id").asText());
                    repetidas += "true".equals(r.getResponse().getHeader("Idempotent-Replayed")) ? 1 : 0;
                }
                assertThat(ids).hasSize(n).containsOnly(ids.get(0));
                assertThat(repetidas).isEqualTo(n - 1);
            } finally {
                pool.shutdownNow();
            }
            assertThat(solicitacoesDaChave(chave)).isEqualTo(1);
            verify(llm, times(1)).gerarJson(anyString(), anyString(), anyMap());
        }

        String criarCom(String chave, String idem, String corpo) throws Exception {
            return ler(enviar(chave, idem, corpo).andExpect(status().isAccepted()).andReturn()).get("id").asText();
        }
    }

    // =====================================================================================================
    /** Processamento assíncrono de verdade (pool de threads), com a IA segurada por um trinco. */
    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", ISOLADO, "tribia.fiscal.sincrono=false"})
    @AutoConfigureMockMvc
    class ConsumoEAssincrono extends Base {

        @Test
        void limiteDeSimultaneasECotaDiariaPorChave() throws Exception {
            CountDownLatch libera = new CountDownLatch(1);
            AtomicLong chamadas = new AtomicLong();
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenAnswer(inv -> {
                chamadas.incrementAndGet();
                libera.await(30, TimeUnit.SECONDS);
                return RESPOSTA_SABONETE;
            });
            String chave = novaChave(1, EscopoApi.PADRAO, null, 1000, 3, 2);

            JsonNode a1 = ler(enviar(chave, "c-1", corpo("Q-1", "")).andExpect(status().isAccepted()).andReturn());
            assertThat(a1.get("status").asText()).isIn("RECEBIDA", "EM_PROCESSAMENTO");
            assertThat(a1.get("finalizada").asBoolean()).isFalse();
            assertThat(a1.get("resultado").isNull()).isTrue();
            enviar(chave, "c-2", corpo("Q-2", "")).andExpect(status().isAccepted());
            enviar(chave, "c-3", corpo("Q-3", "")).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.codigo").value("LIMITE_ANALISES_SIMULTANEAS"))
                    .andExpect(header().exists("Retry-After"));
            assertThat(solicitacoesDaChave(chave)).isEqualTo(2);

            libera.countDown();
            String id1 = a1.get("id").asText();
            esperarFinal(chave, id1);
            JsonNode lista = ler(mvc.perform(get("/api/v1/analises").header("X-API-Key", chave)).andReturn());
            for (JsonNode item : lista.get("itens")) {
                esperarFinal(chave, item.get("id").asText());
            }
            JsonNode final1 = ler(consultar(chave, id1).andReturn());
            assertThat(final1.get("status").asText()).isEqualTo("CONCLUIDA");
            assertThat(final1.get("etapa").asText()).isEqualTo("CONCLUIDA");

            enviar(chave, "c-3", corpo("Q-3", "")).andExpect(status().isAccepted());
            esperarFinal(chave, ler(enviar(chave, "c-3", corpo("Q-3", "")).andReturn()).get("id").asText());
            enviar(chave, "c-4", corpo("Q-4", "")).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.codigo").value("COTA_DIARIA_EXCEDIDA"))
                    .andExpect(header().exists("Retry-After"));
            // repetição idempotente não consome cota, mesmo com a cota esgotada
            enviar(chave, "c-1", corpo("Q-1", "")).andExpect(status().isAccepted())
                    .andExpect(header().string("Idempotent-Replayed", "true"));
            mvc.perform(get("/api/v1/uso").header("X-API-Key", chave))
                    .andExpect(jsonPath("$.consumo.analisesCriadasHoje").value(3))
                    .andExpect(jsonPath("$.consumo.restantesHoje").value(0))
                    .andExpect(jsonPath("$.consumo.emProcessamento").value(0));
            assertThat(chamadas.get()).isEqualTo(3);
        }

        void esperarFinal(String chave, String id) throws Exception {
            long limite = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < limite) {
                if (ler(consultar(chave, id).andReturn()).get("finalizada").asBoolean()) {
                    return;
                }
                Thread.sleep(50);
            }
            throw new AssertionError("Análise " + id + " não terminou em 30 s");
        }
    }

    // =====================================================================================================
    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", ISOLADO, "tribia.api-publica.falhas-autenticacao-por-minuto=3"})
    @AutoConfigureMockMvc
    class LimitesDeRequisicao extends Base {

        @Test
        void limitePorMinutoDaChaveDa429ComRetryAfter() throws Exception {
            String chave = novaChave(1, EscopoApi.PADRAO, null, 3, null, null);
            MvcResult bloqueada = null;
            for (int i = 0; i < 10 && bloqueada == null; i++) {
                MvcResult r = mvc.perform(get("/api/v1/uso").header("X-API-Key", chave)).andReturn();
                assertThat(r.getResponse().getHeader("X-RateLimit-Limit")).isEqualTo("3");
                if (r.getResponse().getStatus() == 429) {
                    bloqueada = r;
                }
            }
            assertThat(bloqueada).isNotNull();
            assertThat(ler(bloqueada).get("codigo").asText()).isEqualTo("LIMITE_REQUISICOES");
            assertThat(bloqueada.getResponse().getHeader("Retry-After")).isNotBlank();
            assertThat(bloqueada.getResponse().getHeader("X-RateLimit-Remaining")).isEqualTo("0");
            // outra chave não é afetada
            mvc.perform(get("/api/v1/uso").header("X-API-Key", novaChave(1))).andExpect(status().isOk());
        }

        @Test
        void muitasFalhasDeAutenticacaoDoMesmoIpDao429() throws Exception {
            for (int i = 0; i < 3; i++) {
                mvc.perform(get("/api/v1/uso").header("X-API-Key", "tribia_invalida").with(ip("10.9.9.9")))
                        .andExpect(status().isUnauthorized());
            }
            mvc.perform(get("/api/v1/uso").header("X-API-Key", "tribia_invalida").with(ip("10.9.9.9")))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.codigo").value("MUITAS_FALHAS_AUTENTICACAO"));
            // outro IP segue normal
            mvc.perform(get("/api/v1/uso").header("X-API-Key", novaChave(1)).with(ip("10.9.9.10")))
                    .andExpect(status().isOk());
        }

        static org.springframework.test.web.servlet.request.RequestPostProcessor ip(String endereco) {
            return r -> {
                r.setRemoteAddr(endereco);
                return r;
            };
        }
    }

    // =====================================================================================================
    @Nested
    @SpringBootTest(properties = {"tribia.seed.enabled=false", ISOLADO})
    @AutoConfigureMockMvc
    @ExtendWith(OutputCaptureExtension.class)
    class GestaoDeChaves extends Base {

        @Autowired
        UsuarioDetailsService detalhes;
        @Autowired
        UsuarioRepository usuarios;

        @Test
        void administradorEmiteUsaListaERevogaSemVazarAChave(CapturedOutput saida) throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            var admin = user(detalhes.loadUserByUsername("admin@tribia.local").semSenha());
            MvcResult criada = mvc.perform(post("/api/admin/chaves-api").with(admin).with(csrf())
                            .contentType("application/json")
                            .content("{\"clienteId\": 2, \"nomeIntegrador\": \"ERP Farmácia\", \"validadeDias\": 30}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.dados.situacao").value("ATIVA"))
                    .andExpect(jsonPath("$.dados.clienteId").value(2))
                    .andReturn();
            JsonNode corpoCriado = ler(criada);
            String chave = corpoCriado.get("chave").asText();
            long idChave = corpoCriado.get("dados").get("id").asLong();
            assertThat(chave).matches("tribia_[0-9a-f]{12}_[A-Za-z0-9_-]{43}");

            ChaveApi gravada = chaves.findById(idChave).orElseThrow();
            assertThat(gravada.getHashSegredo()).isEqualTo(ChavesApi.sha256(chave)).isNotEqualTo(chave);
            assertThat(gravada.getExpiraEm()).isAfter(Instant.now().plus(Duration.ofDays(29)));

            String id = criar(chave, corpo("ADM-1", ""));
            consultar(chave, id).andExpect(status().isOk());

            String lista = mvc.perform(get("/api/admin/chaves-api").param("clienteId", "2").with(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(lista).contains(gravada.getPrefixo()).doesNotContain(chave)
                    .doesNotContain(chave.substring(20)).doesNotContain(gravada.getHashSegredo());
            assertThat(json.readTree(lista).get(0).get("ultimoUsoEm").isNull()).isFalse();

            mvc.perform(post("/api/admin/chaves-api/" + idChave + "/revogar").with(admin).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("REVOGADA"));
            consultar(chave, id).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CHAVE_REVOGADA"));

            // a chave inteira nunca aparece no log (só o prefixo)
            assertThat(saida.getAll()).doesNotContain(chave).doesNotContain(chave.substring(20))
                    .contains(gravada.getPrefixo());
        }

        @Test
        void soAdministradorGerenciaChaves() throws Exception {
            Usuario u = usuarios.save(Usuario.daEmpresa("Fiscal", "fiscal-chaves@test.local", "hash",
                    clientes.findById(1L).orElseThrow()));
            var empresa = user(UsuarioLogado.de(u).semSenha());
            String form = "{\"clienteId\": 1, \"nomeIntegrador\": \"ERP\"}";
            mvc.perform(post("/api/admin/chaves-api").with(empresa).with(csrf()).contentType("application/json")
                    .content(form)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/chaves-api").with(empresa)).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/chaves-api").contentType("application/json").content(form).with(csrf()))
                    .andExpect(status().isUnauthorized());
            var admin = user(detalhes.loadUserByUsername("admin@tribia.local").semSenha());
            // sem CSRF, nem o administrador emite
            mvc.perform(post("/api/admin/chaves-api").with(admin).contentType("application/json").content(form))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/chaves-api").with(admin).with(csrf()).contentType("application/json")
                            .content("{\"clienteId\": 1, \"nomeIntegrador\": \"ERP\", \"escopos\": [\"ADMIN\"]}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/admin/chaves-api").with(admin).with(csrf()).contentType("application/json")
                            .content("{\"clienteId\": 1, \"nomeIntegrador\": \"ERP\", \"validadeDias\": 9999}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void documentacaoOpenApiDaApiPublica() throws Exception {
            String publica = mvc.perform(get("/v3/api-docs/publica-v1")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode doc = json.readTree(publica);
            assertThat(doc.get("paths").has("/api/v1/analises")).isTrue();
            assertThat(doc.get("paths").has("/api/v1/analises/{id}")).isTrue();
            assertThat(doc.get("paths").has("/api/clientes")).isFalse();
            assertThat(doc.get("components").get("securitySchemes").get("chaveApi").get("name").asText())
                    .isEqualTo("X-API-Key");
            assertThat(publica).contains("Idempotency-Key").contains("SolicitacaoAnalise");
            String interna = mvc.perform(get("/v3/api-docs/interna")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(json.readTree(interna).get("paths").has("/api/v1/analises")).isFalse();
        }
    }
}

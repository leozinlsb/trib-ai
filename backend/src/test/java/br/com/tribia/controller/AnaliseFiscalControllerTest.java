package br.com.tribia.controller;

import br.com.tribia.client.llm.LlmClient;
import br.com.tribia.client.llm.LlmException;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Pontuacao;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.Usuario;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.repository.UsuarioRepository;
import br.com.tribia.security.UsuarioLogado;
import br.com.tribia.service.fiscal.AvaliadorJev;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inteligência Fiscal: os 4 endpoints do contrato do front, com a IA simulada (nunca chama o Gemini real) e o
 * processamento síncrono (tribia.fiscal.sincrono=true nos testes).
 */
class AnaliseFiscalControllerTest {

    /** Resposta da IA: sabonete em barra, candidata principal 3401.11.90 (0,90) e alternativa 3401.19.00 (0,50). */
    static final String RESPOSTA_SABONETE = """
            {"suficiente": true, "faltando": [],
             "caracteristicas": ["sabão em barra", "uso para higiene pessoal"],
             "candidatas": [
               {"ncm": "3401.11.90", "descricao": "Sabões de toucador em barras - outros",
                "motivos": ["Barra de sabão para higiene pessoal (RGI 1, posição 34.01)"],
                "avaliacao": "Descrição compatível com sabão de toucador.", "confianca": 0.9},
               {"ncm": "34011900", "descricao": "Outros sabões em barras",
                "motivos": ["Sabão em barra não de toucador"], "avaliacao": "Menos provável.", "confianca": 0.5},
               {"ncm": "3401", "descricao": "posição incompleta", "motivos": [], "avaliacao": "", "confianca": 0.4}],
             "regrasConsideradas": ["RGI 1", "RGI 6"],
             "observacoes": ["A NCM informada pela empresa parece adequada."]}
            """;

    static final String DADOS = """
            {"nome": "Sabonete de glicerina 90 g", "descricao": "Sabonete em barra de glicerina para higiene pessoal, embalado individualmente.",
             "composicao": "glicerina, óleo vegetal", "ncmAtual": "%s"}
            """;

    abstract static class Base {
        @Autowired
        MockMvc mvc;

        @Autowired
        ObjectMapper json;

        @MockitoBean
        LlmClient llm;

        ResultActions enviar(long cliente, String dados, MockMultipartFile... arquivos) throws Exception {
            MockMultipartHttpServletRequestBuilder req = multipart("/api/clientes/" + cliente + "/analises-fiscais");
            req.file(new MockMultipartFile("dados", "", "application/json", dados.getBytes(StandardCharsets.UTF_8)));
            for (MockMultipartFile f : arquivos) {
                req.file(f);
            }
            return mvc.perform(req.with(csrf()));
        }

        long criar(long cliente, String dados, MockMultipartFile... arquivos) throws Exception {
            String body = enviar(cliente, dados, arquivos).andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();
            return json.readTree(body).get("id").asLong();
        }

        JsonNode detalhe(long id) throws Exception {
            return json.readTree(mvc.perform(get("/api/analises-fiscais/" + id)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
        }

        static MockMultipartFile arquivo(String nome, String conteudo) {
            return new MockMultipartFile("arquivos", nome, "application/octet-stream", conteudo.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nested
    @SpringBootTest(properties = "tribia.seed.enabled=false")
    @AutoConfigureMockMvc
    @Transactional
    @WithUserDetails("admin@tribia.local")
    class Fluxo extends Base {

        @Autowired
        ClienteRepository clientes;

        @Autowired
        UsuarioRepository usuarios;

        @Test
        void analiseCompletaComNcmIgualAAtualEValidadaPelasVerificacoesDisponiveis() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);

            String resposta = enviar(1, DADOS.formatted("3401.11.90"),
                    arquivo("ficha.txt", "Ficha técnica: sabonete 90 g, pH 9."),
                    arquivo("foto.png", "png-falso"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("AGUARDANDO"))
                    .andExpect(jsonPath("$.clienteId").value(1))
                    .andExpect(jsonPath("$.relatorioDisponivel").value(false))
                    .andReturn().getResponse().getContentAsString();
            long id = json.readTree(resposta).get("id").asLong();

            JsonNode d = detalhe(id);
            assertThat(d.get("status").asText()).isEqualTo("CONCLUIDA");
            assertThat(d.get("ncmSugerida").asText()).isEqualTo("34011190");
            assertThat(d.get("historico").findValuesAsText("status")).containsExactly("AGUARDANDO", "INTERPRETANDO",
                    "PESQUISANDO_NCM", "AVALIANDO", "VALIDANDO", "GERANDO_RELATORIO", "CONCLUIDA");
            assertThat(d.get("entrada").get("ncmAtual").asText()).isEqualTo("34011190");
            assertThat(d.get("anexos").findValuesAsText("nome")).containsExactly("ficha.txt", "foto.png");

            JsonNode r = d.get("resultado");
            assertThat(r.get("ncm").asText()).isEqualTo("34011190");
            assertThat(r.get("situacaoValidacao").asText()).isEqualTo("VALIDADO_VERIFICACOES");

            // candidata de 4 dígitos descartada; as duas válidas aparecem, sem pontuação (JEV ainda não integrada)
            assertThat(d.get("alternativas").findValuesAsText("ncm")).containsExactly("34011190", "34011900");
            assertThat(d.get("alternativas").get(0).has("pontuacao")).isFalse();
            assertThat(d.get("fundamentacao").get("observacoes").toString()).contains("descartados");

            JsonNode v = d.get("validacao");
            Map<String, String> verificacoes = new java.util.LinkedHashMap<>();
            v.get("verificacoes").forEach(x -> verificacoes.put(x.get("nome").asText(), x.get("resultado").asText()));
            assertThat(verificacoes).containsEntry("Existência e vigência na TIPI", "NAO_REALIZADA")
                    .containsEntry("Comparação com a NCM usada hoje", "OK");
            assertThat(v.get("regrasAplicaveis").toString()).contains("200035");
            assertThat(d.get("fontes").findValuesAsText("titulo")).contains("Lei Complementar nº 214/2025");

            String limitacoes = d.get("fundamentacao").get("limitacoes").toString();
            assertThat(limitacoes).contains("JEV AI").contains("foto.png").contains("TIPI");

            // o texto do anexo .txt foi para a IA, dentro do bloco de dados
            ArgumentCaptor<String> pedido = ArgumentCaptor.forClass(String.class);
            verify(llm).gerarJson(anyString(), pedido.capture(), anyMap());
            assertThat(pedido.getValue()).contains("<mercadoria>").contains("pH 9").doesNotContain("png-falso");
        }

        @Test
        void ncmDiferenteDaUsadaHojeVaiParaRevisaoComDivergencia() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            JsonNode d = detalhe(criar(1, DADOS.formatted("33073000")));

            assertThat(d.get("status").asText()).isEqualTo("AGUARDANDO_REVISAO");
            assertThat(d.get("resultado").get("situacaoValidacao").asText()).isEqualTo("PENDENTE_REVISAO");
            assertThat(d.get("validacao").get("divergencias").toString()).contains("3307.30.00").contains("3401.11.90");
            assertThat(d.get("mensagem").asText()).contains("Confirmar na TIPI");
        }

        @Test
        void informacoesInsuficientesListaOQueFaltaSemResultado() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn("""
                    {"suficiente": false, "faltando": ["composição percentual", "forma de apresentação"],
                     "caracteristicas": [], "candidatas": [
                       {"ncm": "39269090", "descricao": "Outras obras de plástico", "motivos": [], "avaliacao": "", "confianca": 0.3}],
                     "regrasConsideradas": [], "observacoes": []}
                    """);
            JsonNode d = detalhe(criar(1, DADOS.formatted("")));

            assertThat(d.get("status").asText()).isEqualTo("INFORMACOES_INSUFICIENTES");
            assertThat(d.has("resultado")).isFalse();
            assertThat(d.get("mensagem").asText()).contains("composição percentual").contains("3926.90.90");
        }

        @Test
        void iaIndisponivelViraFalhaComMensagemClara() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap()))
                    .thenThrow(new LlmException(LlmException.Tipo.NAO_CONFIGURADO, "sem chave"));
            JsonNode d = detalhe(criar(1, DADOS.formatted("")));

            assertThat(d.get("status").asText()).isEqualTo("FALHA");
            assertThat(d.get("mensagem").asText()).contains("GEMINI_API_KEY");
            assertThat(d.get("historico").findValuesAsText("status")).endsWith("FALHA");
        }

        @Test
        void validacoesDeEntradaNaoChamamAIa() throws Exception {
            enviar(1, "{\"nome\": \"X\", \"descricao\": \"curta\"}").andExpect(status().isBadRequest());
            enviar(1, DADOS.formatted("123")).andExpect(status().isBadRequest());
            enviar(1, DADOS.formatted(""), arquivo("virus.exe", "x"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail", containsString("Formato não aceito")));
            MockMultipartFile[] onze = new MockMultipartFile[11];
            for (int i = 0; i < 11; i++) onze[i] = arquivo("a" + i + ".txt", "x");
            enviar(1, DADOS.formatted(""), onze).andExpect(status().isBadRequest());
            enviar(999, DADOS.formatted("")).andExpect(status().isNotFound());
            verify(llm, never()).gerarJson(any(), any(), any());
        }

        @Test
        void empresaDesativadaNaoIniciaAnalise() throws Exception {
            Cliente c = clientes.findById(3L).orElseThrow();
            c.desativar();
            enviar(3, DADOS.formatted("")).andExpect(status().isConflict());
            verify(llm, never()).gerarJson(any(), any(), any());
        }

        @Test
        void listaFiltraPaginaEIndicadores() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            criar(1, DADOS.formatted("3401.11.90"));   // CONCLUIDA
            criar(1, DADOS.formatted("33073000"));     // AGUARDANDO_REVISAO
            criar(2, DADOS.formatted(""));             // outra empresa

            mvc.perform(get("/api/clientes/1/analises-fiscais"))
                    .andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.pagina").value(0))
                    .andExpect(jsonPath("$.tamanho").value(10))
                    .andExpect(jsonPath("$.itens", hasSize(2)))
                    .andExpect(jsonPath("$.itens[*].clienteId", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(1))));
            mvc.perform(get("/api/clientes/1/analises-fiscais").param("status", "CONCLUIDA"))
                    .andExpect(jsonPath("$.total").value(1));
            mvc.perform(get("/api/clientes/1/analises-fiscais").param("q", "3401.11"))
                    .andExpect(jsonPath("$.total").value(2));
            mvc.perform(get("/api/clientes/1/analises-fiscais").param("q", "glicerina"))
                    .andExpect(jsonPath("$.total").value(2));
            mvc.perform(get("/api/clientes/1/analises-fiscais").param("q", "parafuso"))
                    .andExpect(jsonPath("$.total").value(0));
            mvc.perform(get("/api/clientes/1/analises-fiscais").param("tamanho", "1").param("pagina", "1"))
                    .andExpect(jsonPath("$.itens", hasSize(1)))
                    .andExpect(jsonPath("$.total").value(2));
            mvc.perform(get("/api/clientes/1/analises-fiscais").param("de", "2000-01-01").param("ate", "2000-01-31"))
                    .andExpect(jsonPath("$.total").value(0));
            mvc.perform(get("/api/clientes/1/analises-fiscais/indicadores"))
                    .andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.concluidas").value(1))
                    .andExpect(jsonPath("$.emProcessamento").value(0))
                    .andExpect(jsonPath("$.aguardandoRevisao").value(1));

            mvc.perform(get("/api/clientes/1/analises-fiscais").param("status", "XPTO")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/clientes/1/analises-fiscais").param("de", "01/10/2026")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/clientes/1/analises-fiscais").param("tamanho", "500")).andExpect(status().isBadRequest());
        }

        @Test
        void usuarioDeOutraEmpresaRecebe404SemChamarAIa() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            long daEmpresa1 = criar(1, DADOS.formatted(""));
            Usuario u = usuarios.save(Usuario.daEmpresa("Farmácia", "fiscal-isolamento@test.local", "hash",
                    clientes.findById(2L).orElseThrow()));
            UsuarioLogado farmacia = UsuarioLogado.de(u).semSenha();
            var comoFarmacia = authentication(UsernamePasswordAuthenticationToken.authenticated(
                    farmacia, null, farmacia.getAuthorities()));
            org.mockito.Mockito.clearInvocations(llm);

            mvc.perform(get("/api/analises-fiscais/" + daEmpresa1).with(comoFarmacia))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
            mvc.perform(get("/api/clientes/1/analises-fiscais").with(comoFarmacia)).andExpect(status().isNotFound());
            mvc.perform(get("/api/clientes/1/analises-fiscais/indicadores").with(comoFarmacia)).andExpect(status().isNotFound());
            mvc.perform(multipart("/api/clientes/1/analises-fiscais")
                            .file(new MockMultipartFile("dados", "", "application/json",
                                    DADOS.formatted("").getBytes(StandardCharsets.UTF_8)))
                            .with(comoFarmacia).with(csrf()))
                    .andExpect(status().isNotFound());
            verify(llm, never()).gerarJson(any(), any(), any());

            // a própria empresa funciona normalmente
            mvc.perform(get("/api/clientes/2/analises-fiscais").with(comoFarmacia)).andExpect(status().isOk());
        }

        @Test
        void analiseInexistenteDa404DeNegocio() throws Exception {
            mvc.perform(get("/api/analises-fiscais/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
        }
    }

    @Nested
    @SpringBootTest(properties = "tribia.seed.enabled=false")
    @AutoConfigureMockMvc
    @Transactional
    @WithUserDetails("admin@tribia.local")
    class ComJev extends Base {

        /** Simula a JEV do colaborador: basta existir um bean AvaliadorJev. */
        @TestConfiguration
        static class JevDeTeste {
            @Bean
            AvaliadorJev jev() {
                return new AvaliadorJev() {
                    @Override
                    public boolean disponivel() {
                        return true;
                    }

                    @Override
                    public Map<String, Pontuacao> avaliar(MercadoriaParaJev m, List<Candidata> candidatas) {
                        assertThat(m.caracteristicasInterpretadas()).contains("sabão em barra");
                        return candidatas.stream().collect(Collectors.toMap(Candidata::ncm,
                                c -> new Pontuacao(c.ncm().equals("34011190") ? new BigDecimal("0.82") : new BigDecimal("0.31"),
                                        "0 a 1", "Compatibilidade entre a descrição e o texto do código.")));
                    }
                };
            }
        }

        @Test
        void pontuacaoDaJevApareceNasAlternativas() throws Exception {
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(RESPOSTA_SABONETE);
            JsonNode d = detalhe(criar(1, DADOS.formatted("34011190")));

            JsonNode primeira = d.get("alternativas").get(0);
            assertThat(primeira.get("pontuacao").get("valor").decimalValue()).isEqualByComparingTo("0.82");
            assertThat(primeira.get("pontuacao").get("escala").asText()).isEqualTo("0 a 1");
            assertThat(d.get("fundamentacao").get("limitacoes").toString()).doesNotContain("JEV AI");
        }
    }
}

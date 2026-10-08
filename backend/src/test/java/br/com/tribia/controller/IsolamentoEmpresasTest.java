package br.com.tribia.controller;

import br.com.tribia.Fixtures;
import br.com.tribia.client.calculadora.CalculadoraOficialClient;
import br.com.tribia.client.calculadora.CalculadoraSimplificadaClient;
import br.com.tribia.client.llm.LlmClient;
import br.com.tribia.dto.RevisarItemRequest;
import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.*;
import br.com.tribia.repository.*;
import br.com.tribia.security.UsuarioDetailsService;
import br.com.tribia.security.UsuarioLogado;
import br.com.tribia.service.*;
import br.com.tribia.service.calculo.CalculoService;
import br.com.tribia.service.classificacao.*;
import br.com.tribia.service.demo.*;
import br.com.tribia.service.painel.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Duas empresas com produtos idênticos, cálculos persistidos e itens pendentes que exigiriam IA. */
@SpringBootTest(properties = {"tribia.seed.enabled=false", "tribia.demo.habilitado=true",
        "tribia.calculo.modo=SIMPLIFICADA"})
@AutoConfigureMockMvc
@Transactional
class IsolamentoEmpresasTest {
    @Autowired MockMvc mvc;
    @Autowired ClienteRepository clientes;
    @Autowired UsuarioRepository usuarios;
    @Autowired ItemRepository itens;
    @Autowired ClassificacaoRepository classificacoes;
    @Autowired ClassificacaoCacheRepository cache;
    @Autowired UsuarioDetailsService detalhes;
    @Autowired ClienteService clienteService;
    @Autowired NotaService notaService;
    @Autowired ClassificacaoService classificacaoService;
    @Autowired CalculoService calculoService;
    @Autowired RevisaoService revisaoService;
    @Autowired DashboardService dashboard;
    @Autowired DadosApuracao dados;
    @Autowired RelatorioCsvService csv;
    @Autowired RelatorioService relatorio;
    @Autowired UsuarioService usuarioService;
    @Autowired DemoService demo;
    @Autowired SeedService seed;
    @Autowired ClassificacoesSeedLoader cargaCache;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoBean LlmClient llm;
    @MockitoBean CalculadoraOficialClient oficial;
    @MockitoSpyBean CalculadoraSimplificadaClient simplificada;
    UsuarioLogado admin;
    final Map<Long, UsuarioLogado> pessoas = new LinkedHashMap<>();
    final Map<Long, Long> notas = new LinkedHashMap<>();
    final Map<Long, Long> primeirosItens = new LinkedHashMap<>();

    @BeforeEach
    void preparar() {
        admin = detalhes.loadUserByUsername("admin@tribia.local").semSenha();
        autenticar(admin);
        for (long id : List.of(1L, 2L)) {
            Cliente cliente = clientes.findById(id).orElseThrow();
            Usuario u = usuarios.save(Usuario.daEmpresa("Empresa " + id, "isolamento" + id + "@test.local",
                    "hash-nao-usado-no-login", cliente));
            pessoas.put(id, UsuarioLogado.de(u).semSenha());
            String xml = Fixtures.texto(Fixtures.NFE_ENTRADA_IBSCBS)
                    .replace("10433218000193", cliente.getCnpj())
                    .replaceAll("(?s)<IBSCBS>.*?</IBSCBS>", "");
            Nota nota = notaService.importar(id, xml.getBytes(StandardCharsets.UTF_8));
            notas.put(id, nota.getId());
            Item item = itens.daNota(nota.getId()).getFirst();
            primeirosItens.put(id, item.getId());
            Classificacao c = new Classificacao(item);
            c.definir("000", "000001", RegimeTributario.INTEGRAL, "Fixture de isolamento",
                    BigDecimal.ONE, OrigemClassificacao.XML, true);
            classificacoes.save(c);
            calculoService.calcular(nota.getId(), null);
        }
        em.flush();
        em.clear();
        SecurityContextHolder.clearContext();
        clearInvocations(llm, oficial, simplificada);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void negaTodasAsRotasCruzadasSemEfeitos(long dono) throws Exception {
        for (var req : rotas(dono)) negar(req, pessoas.get(3 - dono), 404);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void servicosTambemNegamAcessoCruzadoAntesDeConsultarOuAlterar(long dono) {
        autenticar(pessoas.get(3 - dono));
        long nota = notas.get(dono), item = primeirosItens.get(dono);
        Cliente estrangeiro = clientes.findById(dono).orElseThrow();
        for (Executable op : List.<Executable>of(
                () -> clienteService.buscar(dono), () -> notaService.listar(dono, null, null),
                () -> notaService.detalhar(nota), () -> notaService.importar(dono, new byte[0]),
                () -> classificacaoService.classificar(nota), () -> classificacaoService.classificar(nota, false),
                () -> calculoService.calcular(nota, null), () -> calculoService.recalcular(nota),
                () -> calculoService.definirPagamento(nota, false), () -> calculoService.calcularCliente(dono, null),
                () -> revisaoService.listar(dono, null, null),
                () -> revisaoService.revisar(item, new RevisarItemRequest(true, null, null, null, null, true)),
                () -> classificacaoService.gravarNoCacheDoItem(item, "000", "000001", "Negada"),
                () -> classificacaoService.validarNoCacheDoItem(item, "000", "000001"),
                () -> dados.doCliente(dono, null, null), () -> dados.daNota(nota),
                () -> dashboard.dashboard(dono, null, null), () -> dashboard.resumoDaNota(nota),
                () -> csv.doCliente(dono, null, null, RelatorioCsvService.Formato.PADRAO),
                () -> csv.daNota(nota, RelatorioCsvService.Formato.PADRAO),
                () -> relatorio.gerar(estrangeiro, null, null))) {
            var antes = estado();
            assertThrows(RecursoNaoEncontradoException.class, op);
            assertEquals(antes, estado());
            verifyNoInteractions(llm, oficial, simplificada);
        }
    }

    @Test
    void empresaNaoExecutaServicosAdministrativosNemCargaGlobal() {
        autenticar(pessoas.get(1L));
        Cliente cliente = clientes.findById(2L).orElseThrow();
        for (Executable op : List.<Executable>of(
                () -> clienteService.criar(null), () -> clienteService.atualizar(2L, null),
                () -> clienteService.desativar(2L), () -> clienteService.reativar(2L),
                () -> usuarioService.daEmpresa(2L), () -> usuarioService.criarDaEmpresa(cliente, null),
                () -> usuarioService.remover(pessoas.get(2L).id()),
                () -> classificacaoService.gravarNoCache("34022000", "X", "000", "000001", "X",
                        BigDecimal.ONE, "MANUAL", true),
                demo::status, demo::reiniciar, seed::carregar, cargaCache::carregar)) {
            var antes = estado();
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, op).getStatus());
            assertEquals(antes, estado());
            verifyNoInteractions(llm, oficial, simplificada);
        }
    }

    @Test
    void demoExigeAdminEContinuaExigindoCsrf() throws Exception {
        negar(get("/api/demo/status"), pessoas.get(1L), 403);
        negar(post("/api/demo/reiniciar"), pessoas.get(2L), 403);
        var antes = estado();
        mvc.perform(get("/api/demo/status")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/demo/reiniciar").with(user(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/demo/status").with(user(admin))).andExpect(status().isOk());
        assertEquals(antes, estado());
        verifyNoInteractions(llm, oficial, simplificada);
    }

    @Test
    void empresaDesativadaEUsuarioRemovidoNaoOperamNemComSessaoAntiga() throws Exception {
        clientes.findById(1L).orElseThrow().desativar();
        em.flush();
        for (var req : rotas(1L)) negar(req, pessoas.get(1L), 403);
        negar(get("/api/clientes"), pessoas.get(1L), 403);
        usuarios.deleteById(pessoas.get(2L).id());
        em.flush();
        for (var req : rotas(2L)) negar(req, pessoas.get(2L), 401);
        negar(get("/api/clientes"), pessoas.get(2L), 401);
        mvc.perform(get("/api/notas/" + notas.get(1L)).with(user(admin))).andExpect(status().isOk());
        negar(multipart("/api/clientes/1/notas").file(arquivo()), admin, 409);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void propriaEmpresaEAdminContinuamConsultandoSemVazamento(long dono) throws Exception {
        var pessoa = pessoas.get(dono);
        mvc.perform(get("/api/clientes").with(user(pessoa))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(dono))
                .andExpect(jsonPath("$[0].ativo").value(true)).andExpect(jsonPath("$[0].notas").value(1));
        for (UsuarioLogado p : List.of(pessoa, admin)) {
            for (var req : leituras(dono)) mvc.perform(req.with(user(p))).andExpect(status().isOk());
            mvc.perform(get("/api/classificacoes/opcoes").with(user(p))).andExpect(status().isOk());
        }
        verifyNoInteractions(llm, oficial, simplificada);
    }

    @Test
    void listaAdminExplicitaAtivoFalseEReativacaoSemPerderDados() throws Exception {
        mvc.perform(delete("/api/clientes/2").with(user(admin)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/clientes").with(user(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3))).andExpect(jsonPath("$[0].ativo").value(true))
                .andExpect(jsonPath("$[1].ativo").value(false)).andExpect(jsonPath("$[1].notas").value(1));
        mvc.perform(post("/api/clientes/2/reativar").with(user(admin)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/clientes").with(user(pessoas.get(2L)))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ativo").value(true)).andExpect(jsonPath("$[0].notas").value(1));
    }

    @Test
    void csrfBloqueiaOperacoesDaPropriaEmpresaSemEfeitos() throws Exception {
        var antes = estado();
        mvc.perform(post("/api/notas/" + notas.get(1L) + "/classificar").with(user(pessoas.get(1L))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/clientes/1/calcular").with(user(pessoas.get(1L))))
                .andExpect(status().isForbidden());
        assertEquals(antes, estado());
        verifyNoInteractions(llm, oficial, simplificada);
    }

    @Test
    void lotePagamentoERevisaoDeIdenticosNaoModificamOutraEmpresa() throws Exception {
        var outra = estadoDaEmpresa(2L);
        var quem = pessoas.get(1L);
        mvc.perform(post("/api/clientes/1/calcular").with(user(quem)).with(csrf())).andExpect(status().isOk());
        mvc.perform(put("/api/notas/" + notas.get(1L) + "/pagamento?confirmado=false")
                .with(user(quem)).with(csrf())).andExpect(status().isOk());
        mvc.perform(put("/api/itens/" + primeirosItens.get(1L) + "/classificacao").with(user(quem)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cClassTrib\":\"000001\",\"justificativa\":\"Teste de isolamento\",\"aplicarAosIguais\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itensAtualizados").value(1));
        assertEquals(outra, estadoDaEmpresa(2L));
        verifyNoInteractions(llm, oficial);
        verify(simplificada, atLeastOnce()).calcular(any());
    }

    @Test
    void empresaClassificaComIaSimuladaSemAlterarRegistrosDeOutraEmpresa() throws Exception {
        var outra = estadoDaEmpresa(2L);
        when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn("""
                [{"nItem":1,"cst":"000","cClassTrib":"000001","justificativa":"IA simulada","confianca":0.90}]
                """);
        mvc.perform(post("/api/notas/" + notas.get(1L) + "/classificar").with(user(pessoas.get(1L))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.classificados").value(2))
                .andExpect(jsonPath("$.porOrigem.IA").value(1)).andExpect(jsonPath("$.calculo.itensCalculados").value(2));
        assertEquals(outra, estadoDaEmpresa(2L));
        verify(llm).gerarJson(anyString(), anyString(), anyMap());
        verify(simplificada).calcular(any());
        verifyNoInteractions(oficial);
    }

    /** Regressão de confidencialidade: uma revisão privada não pode virar justificativa de outro cliente. */
    @Test
    void justificativaManualPrivadaNaoDeveVazarPeloCacheGlobal() throws Exception {
        long pendenteA = itens.daNota(notas.get(1L)).getLast().getId();
        Item produto = itens.findById(pendenteA).orElseThrow();
        autenticar(admin);
        classificacaoService.gravarNoCache(produto.getNcm(), produto.getDescricao(), "000", "000001",
                "CATALOGO PUBLICO SINTETICO", new BigDecimal("0.60"), "SEED", false);
        SecurityContextHolder.clearContext();
        String marcador = "INTERNO-EMPRESA-A-CONTRATO-SINTETICO";
        mvc.perform(put("/api/itens/" + pendenteA + "/classificacao")
                .with(user(pessoas.get(1L))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cClassTrib\":\"000001\",\"justificativa\":\"" + marcador
                        + "\",\"aplicarAosIguais\":false}"))
                .andExpect(status().isOk());
        clearInvocations(llm, oficial, simplificada);
        mvc.perform(post("/api/notas/" + notas.get(2L) + "/classificar?calcular=false")
                .with(user(pessoas.get(2L))).with(csrf()))
                .andExpect(status().isOk());
        verifyNoInteractions(llm, oficial, simplificada);
        mvc.perform(get("/api/notas/" + notas.get(2L)).with(user(pessoas.get(2L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[1].classificacao.justificativa")
                        .value(not(containsString(marcador))))
                .andExpect(jsonPath("$.itens[1].classificacao.justificativa").value("CATALOGO PUBLICO SINTETICO"))
                .andExpect(jsonPath("$.itens[1].classificacao.aceita").value(false));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void revisaoPrivadaReutilizadaSomentePelaEmpresaDaNotaMesmoComAdmin(long dono) {
        autenticar(admin);
        Item produto = itens.daNota(notas.get(dono)).getLast();
        classificacaoService.gravarNoCacheDoItem(produto.getId(), "000", "000001", "Privada " + dono);
        assertTrue(cache.findByChave(br.com.tribia.util.ChaveClassificacao.daEmpresa(dono,
                produto.getNcm(), produto.getDescricao())).orElseThrow().isValidada());
        var outraAntes = estadoDaEmpresa(3 - dono);
        Nota nova = novaNota(dono, 5679, false);
        classificacaoService.classificar(nova.getId(), false);
        Classificacao c = classificacoes.findByItemId(itens.daNota(nova.getId()).getLast().getId()).orElseThrow();
        assertEquals("Privada " + dono, c.getJustificativa());
        assertEquals(OrigemClassificacao.CACHE, c.getOrigem());
        assertTrue(c.isAceita());
        assertEquals(outraAntes, estadoDaEmpresa(3 - dono));
        autenticar(pessoas.get(3 - dono));
        classificacaoService.classificar(notas.get(3 - dono), false);
        assertTrue(classificacoes.findByItemId(itens.daNota(notas.get(3 - dono)).getLast().getId()).isEmpty());
        verifyNoInteractions(llm, oficial, simplificada);
    }

    @Test
    void aprendizadoDaIaEPrivadoEContinuaNaoAceito() {
        autenticar(pessoas.get(1L));
        when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn("""
                [{"nItem":1,"cst":"000","cClassTrib":"000001","justificativa":"IA privada A","confianca":0.90}]
                """);
        classificacaoService.classificar(notas.get(1L), true);
        Nota nova = novaNota(1L, 5679, false);
        classificacaoService.classificar(nova.getId(), false);
        Classificacao c = classificacoes.findByItemId(itens.daNota(nova.getId()).getLast().getId()).orElseThrow();
        assertEquals("IA privada A", c.getJustificativa());
        assertEquals(OrigemClassificacao.CACHE, c.getOrigem());
        assertFalse(c.isAceita());
        autenticar(pessoas.get(2L));
        classificacaoService.classificar(notas.get(2L), false);
        assertTrue(classificacoes.findByItemId(itens.daNota(notas.get(2L)).getLast().getId()).isEmpty());
        verify(llm).gerarJson(anyString(), anyString(), anyMap());
        verifyNoInteractions(oficial, simplificada);
    }

    @Test
    void aceitarIaNaoValidaCachePrivadoDaOutraEmpresa() {
        for (long dono : List.of(1L, 2L)) {
            autenticar(pessoas.get(dono));
            when(llm.gerarJson(anyString(), anyString(), anyMap())).thenReturn(
                    "[{\"nItem\":1,\"cst\":\"000\",\"cClassTrib\":\"000001\",\"justificativa\":\"IA empresa "
                            + dono + "\",\"confianca\":0.90}]");
            classificacaoService.classificar(notas.get(dono), true);
        }
        Item produto = itens.daNota(notas.get(1L)).getLast();
        String chaveB = br.com.tribia.util.ChaveClassificacao.daEmpresa(2L, produto.getNcm(), produto.getDescricao());
        em.flush();
        var cacheBAntes = jdbc.queryForList("select * from classificacao_cache where chave=?", chaveB);
        var empresaBAntes = estadoDaEmpresa(2L);
        clearInvocations(llm, oficial, simplificada);
        autenticar(pessoas.get(1L));
        revisaoService.revisar(produto.getId(), new RevisarItemRequest(true, null, null, null, null, false));
        em.flush();
        assertEquals(cacheBAntes, jdbc.queryForList("select * from classificacao_cache where chave=?", chaveB));
        assertEquals(empresaBAntes, estadoDaEmpresa(2L));
        assertFalse(cache.findByChave(chaveB).orElseThrow().isValidada());
        assertTrue(cache.findByChave(br.com.tribia.util.ChaveClassificacao.daEmpresa(1L,
                produto.getNcm(), produto.getDescricao())).orElseThrow().isValidada());
        verifyNoInteractions(llm, oficial);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IA", "MANUAL", "ANTIGA"})
    void legadoSemDonoNaoEReutilizadoNemReatribuido(String fonte) {
        Item produto = itens.daNota(notas.get(1L)).getLast();
        String chave = br.com.tribia.util.ChaveClassificacao.de(produto.getNcm(), produto.getDescricao());
        ClassificacaoCache legado = new ClassificacaoCache(chave, produto.getNcm());
        legado.definir("000", "000001", "LEGADO CONFIDENCIAL SEM DONO", BigDecimal.ONE, fonte, true);
        cache.saveAndFlush(legado);
        var antes = jdbc.queryForList("select * from classificacao_cache order by id");
        for (long dono : List.of(1L, 2L)) {
            autenticar(pessoas.get(dono));
            classificacaoService.classificar(notas.get(dono), false);
            assertTrue(classificacoes.findByItemId(itens.daNota(notas.get(dono)).getLast().getId()).isEmpty());
        }
        em.flush();
        assertEquals(antes, jdbc.queryForList("select * from classificacao_cache order by id"));
        verifyNoInteractions(llm, oficial, simplificada);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aceiteDeCatalogoNovoOuLegadoNaoPromoveOutrasEmpresas(boolean legado) {
        Item produto = itens.daNota(notas.get(1L)).getLast();
        String chave = legado
                ? br.com.tribia.util.ChaveClassificacao.de(produto.getNcm(), produto.getDescricao())
                : br.com.tribia.util.ChaveClassificacao.doCatalogo(produto.getNcm(), produto.getDescricao());
        ClassificacaoCache publico = new ClassificacaoCache(chave, produto.getNcm());
        publico.definir("000", "000001", "Catalogo curado", new BigDecimal("0.60"), "SEED", false);
        cache.saveAndFlush(publico);
        var publicoAntes = jdbc.queryForList("select * from classificacao_cache where chave=?", chave);
        for (long dono : List.of(1L, 2L)) {
            autenticar(pessoas.get(dono));
            classificacaoService.classificar(notas.get(dono), false);
        }
        var empresaBAntes = estadoDaEmpresa(2L);
        clearInvocations(llm, oficial, simplificada);
        autenticar(pessoas.get(1L));
        revisaoService.revisar(produto.getId(), new RevisarItemRequest(true, null, null, null, null, false));
        em.flush();
        assertEquals(publicoAntes, jdbc.queryForList("select * from classificacao_cache where chave=?", chave));
        assertEquals(empresaBAntes, estadoDaEmpresa(2L));
        assertFalse(classificacoes.findByItemId(itens.daNota(notas.get(2L)).getLast().getId()).orElseThrow().isAceita());
        assertTrue(cache.findByChave(br.com.tribia.util.ChaveClassificacao.daEmpresa(1L,
                produto.getNcm(), produto.getDescricao())).orElseThrow().isValidada());
        verifyNoInteractions(llm, oficial);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IA", "MANUAL"})
    void nemAdminPodeGravarAprendizadoPrivadoComoCatalogoGlobal(String fonte) {
        autenticar(admin);
        var antes = estado();
        assertThrows(IllegalArgumentException.class, () -> classificacaoService.gravarNoCache("34022000", "X",
                "000", "000001", "Nao publicar", BigDecimal.ONE, fonte, true));
        assertEquals(antes, estado());
        verifyNoInteractions(llm, oficial, simplificada);
    }

    @Test
    void cargaCuradaNaoSobrescreveCachePrivadoNemLegado() {
        autenticar(admin);
        Item produto = itens.daNota(notas.get(1L)).getLast();
        classificacaoService.gravarNoCacheDoItem(produto.getId(), "000", "000001", "Correcao privada");
        ClassificacaoCache legado = new ClassificacaoCache(br.com.tribia.util.ChaveClassificacao.de(
                produto.getNcm(), produto.getDescricao()), produto.getNcm());
        legado.definir("000", "000001", "Preservar legado", BigDecimal.ONE, "MANUAL", true);
        cache.saveAndFlush(legado);
        var anteriores = jdbc.queryForList("select * from classificacao_cache order by id");
        classificacaoService.gravarNoCache(produto.getNcm(), produto.getDescricao(), "000", "000001",
                "Catalogo novo", new BigDecimal("0.60"), "SEED", false);
        em.flush();
        for (var registro : anteriores)
            assertEquals(registro, jdbc.queryForMap("select * from classificacao_cache where id=?", registro.get("ID")));
        Nota nova = novaNota(1L, 5679, false);
        classificacaoService.classificar(nova.getId(), false);
        assertEquals("Correcao privada", classificacoes.findByItemId(
                itens.daNota(nova.getId()).getLast().getId()).orElseThrow().getJustificativa());
        verifyNoInteractions(llm, oficial, simplificada);
    }

    @Test
    void xmlContinuaPrevalecendoSobreCachePrivado() {
        autenticar(pessoas.get(1L));
        Item produto = itens.daNota(notas.get(1L)).getFirst();
        classificacaoService.gravarNoCacheDoItem(produto.getId(), "000", "000001", "Nao substituir XML");
        Nota nova = novaNota(1L, 5679, true);
        classificacaoService.classificar(nova.getId(), false);
        Classificacao c = classificacoes.findByItemId(itens.daNota(nova.getId()).getFirst().getId()).orElseThrow();
        assertEquals(OrigemClassificacao.XML, c.getOrigem());
        assertTrue(c.isAceita());
        assertNotEquals("Nao substituir XML", c.getJustificativa());
        verifyNoInteractions(llm, oficial, simplificada);
    }

    private Nota novaNota(long dono, long numero, boolean manterIbsCbs) {
        autenticar(pessoas.get(dono));
        String chave = br.com.tribia.util.ChaveAcessoUtil.montar("35", "2609", "51938267000165",
                "55", 1, numero, 1, 87654321);
        String xml = Fixtures.texto(Fixtures.NFE_ENTRADA_IBSCBS)
                .replace("10433218000193", clientes.findById(dono).orElseThrow().getCnpj())
                .replaceAll("<nNF>\\d+</nNF>", "<nNF>" + numero + "</nNF>")
                .replaceAll("<cDV>\\d</cDV>", "<cDV>" + chave.charAt(43) + "</cDV>")
                .replaceAll("Id=\"NFe\\d{44}\"", "Id=\"NFe" + chave + "\"");
        if (!manterIbsCbs) xml = xml.replaceAll("(?s)<IBSCBS>.*?</IBSCBS>", "");
        return notaService.importar(dono, xml.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, List<Map<String, Object>>> estadoDaEmpresa(long id) {
        em.flush();
        em.clear();
        Map<String, List<Map<String, Object>>> estado = new LinkedHashMap<>();
        estado.put("nota", jdbc.queryForList("select * from nota where cliente_id = ? order by id", id));
        estado.put("item", jdbc.queryForList("select i.* from item i join nota n on n.id=i.nota_id where n.cliente_id=? order by i.id", id));
        for (String tabela : List.of("classificacao", "calculo"))
            estado.put(tabela, jdbc.queryForList("select c.* from " + tabela
                    + " c join item i on i.id=c.item_id join nota n on n.id=i.nota_id where n.cliente_id=? order by c.id", id));
        return estado;
    }

    @Test
    void consoleH2NaoPermiteContornarIsolamento() throws Exception {
        var antes = estado();
        mvc.perform(get("/h2-console/")).andExpect(status().isUnauthorized());
        mvc.perform(post("/h2-console/login.do")).andExpect(status().isUnauthorized());
        for (var pessoa : pessoas.values()) {
            mvc.perform(get("/h2-console/").with(user(pessoa))).andExpect(status().isForbidden());
            mvc.perform(post("/h2-console/login.do").with(user(pessoa))).andExpect(status().isForbidden());
        }
        // MockMvc não monta o servlet H2: 404 significa que ADMIN passou pela autorização.
        mvc.perform(get("/h2-console/").with(user(admin))).andExpect(status().isNotFound());
        assertEquals(antes, estado());
        verifyNoInteractions(llm, oficial, simplificada);
    }

    private List<MockHttpServletRequestBuilder> leituras(long dono) {
        long nota = notas.get(dono);
        return List.of(get("/api/clientes/" + dono), get("/api/clientes/" + dono + "/notas"),
                get("/api/clientes/" + dono + "/dashboard"), get("/api/clientes/" + dono + "/revisao"),
                get("/api/clientes/" + dono + "/relatorio"), get("/api/clientes/" + dono + "/relatorio.csv"),
                get("/api/notas/" + nota), get("/api/notas/" + nota + "/resumo"),
                get("/api/notas/" + nota + "/export.csv"));
    }

    private List<MockHttpServletRequestBuilder> rotas(long dono) {
        var reqs = new ArrayList<>(leituras(dono));
        long nota = notas.get(dono), item = primeirosItens.get(dono);
        reqs.add(multipart("/api/clientes/" + dono + "/notas").file(arquivo()));
        reqs.add(post("/api/notas/" + nota + "/classificar"));
        reqs.add(post("/api/notas/" + nota + "/classificar?calcular=false"));
        reqs.add(post("/api/notas/" + nota + "/calcular?cbs=8.8"));
        reqs.add(put("/api/notas/" + nota + "/pagamento?confirmado=false"));
        reqs.add(post("/api/clientes/" + dono + "/calcular?cbs=8.8"));
        for (String body : List.of("{\"aceitar\":true,\"aplicarAosIguais\":true}",
                "{\"cClassTrib\":\"000001\",\"justificativa\":\"Negada\",\"aplicarAosIguais\":true}",
                "{\"creditavel\":false}"))
            reqs.add(put("/api/itens/" + item + "/classificacao").contentType(MediaType.APPLICATION_JSON).content(body));
        return reqs;
    }

    private void negar(MockHttpServletRequestBuilder req, UsuarioLogado pessoa, int codigo) throws Exception {
        var antes = estado();
        mvc.perform(req.with(user(pessoa)).with(csrf())).andExpect(status().is(codigo));
        assertEquals(antes, estado(), "Operação negada não deve alterar registros");
        verifyNoInteractions(llm, oficial, simplificada);
    }

    private Map<String, List<Map<String, Object>>> estado() {
        em.flush();
        em.clear();
        Map<String, List<Map<String, Object>>> estado = new LinkedHashMap<>();
        for (String tabela : List.of("cliente", "usuario", "nota", "item", "classificacao", "calculo",
                "classificacao_cache", "registro_revisao"))
            estado.put(tabela, jdbc.queryForList("select * from " + tabela + " order by id"));
        return estado;
    }

    private static MockMultipartFile arquivo() {
        return new MockMultipartFile("arquivos", "nota.xml", "application/xml", Fixtures.bytes(Fixtures.NFE_ENTRADA_IBSCBS));
    }

    private static void autenticar(UsuarioLogado pessoa) {
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(pessoa, null, pessoa.getAuthorities()));
        SecurityContextHolder.setContext(contexto);
    }
}

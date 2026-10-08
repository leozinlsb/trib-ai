package br.com.tribia.controller;

import br.com.tribia.Fixtures;
import br.com.tribia.model.Usuario;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.repository.UsuarioRepository;
import br.com.tribia.security.UsuarioDetailsService;
import br.com.tribia.security.UsuarioLogado;
import br.com.tribia.util.CnpjUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolamento entre empresas e permissões. Usuários criados em cada teste (transação desfeita no fim).
 * Empresa 1 = Distribuidora (dona das notas de teste), empresa 2 = Farmácia.
 */
@SpringBootTest(properties = "tribia.seed.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AcessoEmpresasTest {

    static final String SENHA = "senha1234";

    @Autowired
    MockMvc mvc;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    ClienteRepository clientes;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    UsuarioDetailsService detalhes;

    RequestPostProcessor admin;
    RequestPostProcessor empresa1;
    RequestPostProcessor empresa2;

    @BeforeEach
    void usuarios() {
        admin = user(detalhes.loadUserByUsername("admin@tribia.local").semSenha());
        empresa1 = criarUsuario("ana@distribuidora.test", 1L);
        empresa2 = criarUsuario("bia@farmacia.test", 2L);
    }

    private RequestPostProcessor criarUsuario(String email, long clienteId) {
        Usuario u = usuarios.save(Usuario.daEmpresa("Teste", email, encoder.encode(SENHA),
                clientes.findById(clienteId).orElseThrow()));
        return user(UsuarioLogado.de(u).semSenha());
    }

    @Test
    void semLoginTudoDevolve401() throws Exception {
        mvc.perform(get("/api/clientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Faça login para continuar."));
        mvc.perform(get("/api/notas/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clientes/1/relatorio")).andExpect(status().isUnauthorized());
    }

    @Test
    void empresaSoEnxergaAPropria() throws Exception {
        mvc.perform(get("/api/clientes").with(empresa2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(2));
        mvc.perform(get("/api/clientes").with(admin)).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void empresaNaoAcessaDadosDeOutraEmNenhumaRota() throws Exception {
        long notaDaEmpresa1 = uploadDaNotaDeTeste();

        mvc.perform(get("/api/clientes/1").with(empresa2)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/1/notas").with(empresa2)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/1/relatorio").with(empresa2)).andExpect(status().isNotFound());
        mvc.perform(get("/api/notas/" + notaDaEmpresa1).with(empresa2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Nota " + notaDaEmpresa1 + " não encontrada"));
        mvc.perform(multipart("/api/clientes/1/notas").file(nota()).with(empresa2).with(csrf()))
                .andExpect(status().isNotFound());

        // a dona vê normalmente
        mvc.perform(get("/api/notas/" + notaDaEmpresa1).with(empresa1)).andExpect(status().isOk());
        mvc.perform(get("/api/clientes/1/notas").with(empresa1)).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void empresaNaoExecutaOperacoesDoAdministrador() throws Exception {
        mvc.perform(post("/api/clientes").with(empresa1).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(form("Nova", cnpjNovo())))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/clientes/1").with(empresa1).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(form("X", "10433218000193")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/clientes/1").with(empresa1).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/clientes/1/usuarios").with(empresa1)).andExpect(status().isForbidden());
    }

    @Test
    void adminCadastraEditaDesativaEReativa() throws Exception {
        String cnpj = cnpjNovo();
        String body = mvc.perform(post("/api/clientes").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(form("Padaria Teste Ltda", cnpj)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cnpj").value(cnpj))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll("(?s).*\"id\":(\\d+).*", "$1"));

        mvc.perform(get("/api/clientes").with(admin)).andExpect(jsonPath("$", hasSize(4)));

        mvc.perform(put("/api/clientes/" + id).with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(form("Padaria Renomeada Ltda", cnpj)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razaoSocial").value("Padaria Renomeada Ltda"));

        mvc.perform(delete("/api/clientes/" + id).with(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        // continua listada (exclusão lógica) e não recebe notas
        mvc.perform(get("/api/clientes").with(admin)).andExpect(jsonPath("$", hasSize(4)));
        mvc.perform(multipart("/api/clientes/" + id + "/notas").file(nota()).with(admin).with(csrf()))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/clientes/" + id + "/reativar").with(admin).with(csrf()))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void desativarEmpresaPreservaNotasEBloqueiaSeusUsuarios() throws Exception {
        long nota = uploadDaNotaDeTeste();
        mvc.perform(delete("/api/clientes/1").with(admin).with(csrf())).andExpect(status().isOk());

        mvc.perform(get("/api/clientes/1/notas").with(empresa1)).andExpect(status().isForbidden());
        mvc.perform(get("/api/notas/" + nota).with(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/clientes/1/notas").with(admin)).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void validacoesDoCadastro() throws Exception {
        mvc.perform(post("/api/clientes").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(form("Teste", "11111111111111")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("CNPJ inválido. Confira os 14 dígitos."));
        mvc.perform(post("/api/clientes").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(form("Duplicada", "10433218000193")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/clientes").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cnpj\":\"" + cnpjNovo() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.razaoSocial").value("Informe a razão social."));
    }

    @Test
    void cnpjNaoMudaSeAEmpresaTemNotas() throws Exception {
        uploadDaNotaDeTeste();
        mvc.perform(put("/api/clientes/1").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(form("Distribuidora", cnpjNovo())))
                .andExpect(status().isConflict());
    }

    @Test
    void adminGerenciaAcessosDaEmpresa() throws Exception {
        mvc.perform(post("/api/clientes/2/usuarios").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Carla\",\"email\":\"carla@farmacia.test\",\"senha\":\"abcd1234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("EMPRESA"))
                .andExpect(jsonPath("$.clienteId").value(2));
        mvc.perform(get("/api/clientes/2/usuarios").with(admin)).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(post("/api/clientes/2/usuarios").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Fraca\",\"email\":\"fraca@farmacia.test\",\"senha\":\"curta\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginRealCriaSessaoELogoutEncerra() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@distribuidora.test\",\"senha\":\"errada123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha incorretos."));

        MockHttpSession sessao = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ANA@distribuidora.test\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("EMPRESA"))
                .andExpect(jsonPath("$.clienteId").value(1))
                .andReturn().getRequest().getSession();

        mvc.perform(get("/api/auth/me").session(sessao)).andExpect(jsonPath("$.email").value("ana@distribuidora.test"));
        mvc.perform(get("/api/clientes/2").session(sessao)).andExpect(status().isNotFound());

        mvc.perform(post("/api/auth/logout").session(sessao).with(csrf())).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(sessao.isInvalid(), "logout deve invalidar a sessão");
        mvc.perform(get("/api/auth/me")).andExpect(status().isNoContent());
    }

    @Test
    void alteracaoSemTokenCsrfEhRecusada() throws Exception {
        mvc.perform(delete("/api/clientes/2").with(admin)).andExpect(status().isForbidden());
    }

    @Test
    void relatorioTrazDadosDaEmpresaCerta() throws Exception {
        uploadDaNotaDeTeste();
        mvc.perform(get("/api/clientes/1/relatorio").with(empresa1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificacao").value("REL-1-202608-202608"))
                .andExpect(jsonPath("$.empresa.id").value(1))
                .andExpect(jsonPath("$.situacao").value("PARCIAL"))
                .andExpect(jsonPath("$.resumo.notas").value(1))
                .andExpect(jsonPath("$.resumo.saidas").value(1))
                .andExpect(jsonPath("$.resumo.itens").value(8))
                .andExpect(jsonPath("$.documentos", hasSize(1)))
                .andExpect(jsonPath("$.observacoes[0].codigo").value("ITENS_SEM_CLASSIFICACAO"));

        mvc.perform(get("/api/clientes/2/relatorio").with(empresa2))
                .andExpect(jsonPath("$.situacao").value("SEM_DADOS"))
                .andExpect(jsonPath("$.resumo.notas").value(0));
        mvc.perform(get("/api/clientes/1/relatorio?de=2026-09&ate=2026-08").with(empresa1))
                .andExpect(status().isBadRequest());
    }

    private long uploadDaNotaDeTeste() throws Exception {
        String body = upload(1, admin).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("(?s).*\"importadas\":\\[\\{\"id\":(\\d+).*", "$1"));
    }

    private ResultActions upload(long clienteId, RequestPostProcessor quem) throws Exception {
        return mvc.perform(multipart("/api/clientes/" + clienteId + "/notas").file(nota()).with(quem).with(csrf()));
    }

    private static MockMultipartFile nota() {
        return new MockMultipartFile("arquivos", Fixtures.NFE_SAIDA_HACKATHON, "application/xml",
                Fixtures.bytes(Fixtures.NFE_SAIDA_HACKATHON));
    }

    private static String cnpjNovo() {
        return CnpjUtil.completarDigitos("112223330001");
    }

    private static String form(String razao, String cnpj) {
        return "{\"razaoSocial\":\"" + razao + "\",\"cnpj\":\"" + cnpj + "\",\"regime\":\"LUCRO_REAL\"}";
    }
}

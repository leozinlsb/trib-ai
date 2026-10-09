package br.com.tribia.controller;

import br.com.tribia.model.Usuario;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.repository.UsuarioRepository;
import br.com.tribia.security.UsuarioLogado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rotas da JEV: só ADMIN, POST com CSRF, nenhuma chamada sem confirmação de custo, chave nunca na resposta. */
@SpringBootTest(properties = {"tribia.seed.enabled=false", "tribia.jev.api-key=chave-de-teste-nao-usar"})
@AutoConfigureMockMvc
@Transactional
@WithUserDetails("admin@tribia.local")
class JevControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    ClienteRepository clientes;

    @Test
    void statusParaAdminSemExporAChave() throws Exception {
        mvc.perform(get("/api/admin/jev/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("DESLIGADO"))
                .andExpect(jsonPath("$.chaveConfigurada").value(true))
                .andExpect(jsonPath("$.ativaNasAnalises").value(false))
                .andExpect(jsonPath("$.modelo").value("jev-1.13.0"))
                .andExpect(content().string(not(containsString("chave-de-teste-nao-usar"))));
    }

    @Test
    void testeSemConfirmacaoDeCustoNaoChamaEExigeCsrf() throws Exception {
        mvc.perform(post("/api/admin/jev/teste")).andExpect(status().isForbidden()); // sem CSRF
        mvc.perform(post("/api/admin/jev/teste").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("confirmarCusto=true")));
    }

    @Test
    void usuarioDeEmpresaRecebe403() throws Exception {
        Usuario u = usuarios.save(Usuario.daEmpresa("Empresa", "jev-empresa@test.local", "hash", clientes.findById(1L).orElseThrow()));
        UsuarioLogado e = UsuarioLogado.de(u).semSenha();
        var comoEmpresa = authentication(UsernamePasswordAuthenticationToken.authenticated(e, null, e.getAuthorities()));
        mvc.perform(get("/api/admin/jev/status").with(comoEmpresa)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/jev/teste").param("confirmarCusto", "true").with(comoEmpresa).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/tabelas/ncm").with(comoEmpresa)).andExpect(status().isForbidden());
    }

    @Test
    void situacaoDaTabelaNcmParaAdmin() throws Exception {
        mvc.perform(get("/api/admin/tabelas/ncm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ato").value(containsString("Gecex")))
                .andExpect(jsonPath("$.limiteDias").value(120))
                .andExpect(jsonPath("$.comoAtualizar").value(containsString("atualizar_ncm.mjs")));
    }
}

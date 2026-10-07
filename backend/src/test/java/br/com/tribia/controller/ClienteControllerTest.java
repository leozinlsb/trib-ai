package br.com.tribia.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClienteControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listaOsTresClientesDeDemo() throws Exception {
        mvc.perform(get("/api/clientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].cnpj").value("10433218000193"))
                .andExpect(jsonPath("$[0].regime").value("LUCRO_REAL"))
                .andExpect(jsonPath("$[1].regime").value("LUCRO_PRESUMIDO"));
    }

    @Test
    void buscaClientePorId() throws Exception {
        mvc.perform(get("/api/clientes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razaoSocial").value("Distribuidora Fictícia de Alimentos Ltda"));
    }

    @Test
    void clienteInexistenteDevolve404() throws Exception {
        mvc.perform(get("/api/clientes/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Cliente 999 não encontrado"));
    }
}

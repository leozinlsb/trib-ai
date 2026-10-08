package br.com.tribia.controller;

import br.com.tribia.repository.NotaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "tribia.calculo.modo=SIMPLIFICADA")
@AutoConfigureMockMvc
class RelatorioControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    NotaRepository notaRepository;

    @Test
    void relatorioDoClienteParaExcelEmPortugues() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/api/clientes/1/relatorio.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("tribia-10433218000193.csv")))
                .andReturn().getResponse();

        byte[] bytes = r.getContentAsByteArray();
        assertThat(Arrays.copyOf(bytes, 3)).as("BOM UTF-8").containsExactly(0xEF, 0xBB, 0xBF);
        List<String> linhas = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8).lines().toList();

        assertThat(linhas.get(0)).startsWith("Competência;Data de emissão;Tipo;Nota;");
        assertThat(linhas).hasSize(1 + 23 + 7); // cabeçalho + 23 itens + resumo
        assertThat(linhas).anySatisfy(l -> assertThat(l)
                .contains(";REFRIGERANTE COLA 2L;22021000;")
                .contains(";959,04;")
                .contains(";INTEGRAL;CACHE;")
                .contains(";91,40;"));
        assertThat(linhas).contains(
                "Resumo;Débito;Crédito;Líquido",
                "Hoje (PIS/Cofins);183,62;150,40;33,22",
                "2027 (CBS/IBS/IS);298,85;154,95;143,90",
                "Variação do líquido (%);333,17",
                "Itens no relatório;23",
                "Itens sem cálculo (fora do resumo);0");
    }

    @Test
    void filtroDePeriodoEntraNoNomeDoArquivo() throws Exception {
        mvc.perform(get("/api/clientes/1/relatorio.csv").param("de", "2026-08").param("ate", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("tribia-10433218000193-2026-08-2026-08.csv")));
        mvc.perform(get("/api/clientes/1/relatorio.csv").param("de", "agosto")).andExpect(status().isBadRequest());
    }

    @Test
    void exportDeUmaNota() throws Exception {
        long nota = notaRepository.buscar(1L, null, null).get(0).getId();

        String csv = mvc.perform(get("/api/notas/" + nota + "/export.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("tribia-nota-" + nota + ".csv")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv.lines().filter(l -> l.startsWith("2026-")).count()).isEqualTo(3); // nota de 3 itens de cesta
    }

    @Test
    void inexistenteDa404() throws Exception {
        mvc.perform(get("/api/clientes/999/relatorio.csv")).andExpect(status().isNotFound());
        mvc.perform(get("/api/notas/999999/export.csv")).andExpect(status().isNotFound());
    }
}

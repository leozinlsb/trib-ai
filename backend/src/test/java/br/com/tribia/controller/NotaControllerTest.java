package br.com.tribia.controller;

import br.com.tribia.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cada teste roda numa transação desfeita ao final: o banco volta a ter só os clientes. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotaControllerTest {

    static final long DISTRIBUIDORA = 1;
    static final long FARMACIA = 2;

    @Autowired
    MockMvc mvc;

    @Test
    void notaDeTesteNoCliente1ViraSaidaComOs8Itens() throws Exception {
        upload(DISTRIBUIDORA, arquivo(Fixtures.NFE_SAIDA_HACKATHON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas", hasSize(1)))
                .andExpect(jsonPath("$.rejeitadas", hasSize(0)))
                .andExpect(jsonPath("$.importadas[0].tipo").value("SAIDA"))
                .andExpect(jsonPath("$.importadas[0].competencia").value("2026-08"))
                .andExpect(jsonPath("$.importadas[0].contraparteCnpj").value("27865345000164"))
                .andExpect(jsonPath("$.importadas[0].contraparteNome").value("MERCADO FICTICIO BOM PRECO LTDA"))
                .andExpect(jsonPath("$.importadas[0].valorTotal").value(4071.10))
                .andExpect(jsonPath("$.importadas[0].quantidadeItens").value(8));
    }

    @Test
    void notaDeFornecedorViraEntradaComIbsCbsDestacado() throws Exception {
        String body = upload(DISTRIBUIDORA, arquivo(Fixtures.NFE_ENTRADA_IBSCBS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas[0].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.importadas[0].contraparteCnpj").value("51938267000165"))
                .andExpect(jsonPath("$.importadas[0].competencia").value("2026-09"))
                .andReturn().getResponse().getContentAsString();
        long notaId = Long.parseLong(body.replaceAll("(?s).*\"importadas\":\\[\\{\"id\":(\\d+).*", "$1"));

        mvc.perform(get("/api/notas/" + notaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[0].nItem").value(1))
                .andExpect(jsonPath("$.itens[0].creditavel").value(true))
                .andExpect(jsonPath("$.itens[0].ibsCbsDestacado.cClassTrib").value("000001"))
                .andExpect(jsonPath("$.itens[0].ibsCbsDestacado.vCbs").value(3.24));
    }

    @Test
    void notaDeOutroClienteDevolve422() throws Exception {
        upload(FARMACIA, arquivo(Fixtures.NFE_SAIDA_HACKATHON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Esta nota não pertence ao cliente"));
    }

    @Test
    void notaDuplicadaNoMesmoClienteDevolve409() throws Exception {
        upload(DISTRIBUIDORA, arquivo(Fixtures.NFE_SAIDA_HACKATHON)).andExpect(status().isCreated());

        upload(DISTRIBUIDORA, arquivo(Fixtures.NFE_SAIDA_HACKATHON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "A nota 35260810433218000193550010000012341123456789 já foi importada para este cliente."));
    }

    @Test
    void notaDeDevolucaoEComplementarSaoRejeitadas() throws Exception {
        upload(DISTRIBUIDORA, arquivo("devolucao.xml", xmlCom("<finNFe>1</finNFe>", "<finNFe>4</finNFe>")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "Notas de devolução (finNFe=4) ainda não são suportadas. Envie apenas notas normais."));

        upload(DISTRIBUIDORA, arquivo("compl.xml", xmlCom("<finNFe>1</finNFe>", "<finNFe>2</finNFe>")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void notaComTpNfZeroERejeitada() throws Exception {
        upload(DISTRIBUIDORA, arquivo("tpnf0.xml", xmlCom("<tpNF>1</tpNF>", "<tpNF>0</tpNF>")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void loteParcialImportaOsValidosEListaOsRejeitados() throws Exception {
        upload(DISTRIBUIDORA,
                arquivo(Fixtures.NFE_SAIDA_HACKATHON),
                arquivo("quebrado.xml", "<NFe>".getBytes(StandardCharsets.UTF_8)),
                arquivo(Fixtures.NFE_ENTRADA_IBSCBS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas", hasSize(2)))
                .andExpect(jsonPath("$.rejeitadas", hasSize(1)))
                .andExpect(jsonPath("$.rejeitadas[0].arquivo").value("quebrado.xml"))
                .andExpect(jsonPath("$.rejeitadas[0].status").value(422));
    }

    @Test
    void loteSemNenhumaNotaValidaDevolve422ComAsRejeicoes() throws Exception {
        upload(FARMACIA, arquivo(Fixtures.NFE_SAIDA_HACKATHON), arquivo(Fixtures.NFE_ENTRADA_IBSCBS))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.rejeitadas", hasSize(2)));
    }

    @Test
    void listaNotasDoClienteComFiltros() throws Exception {
        upload(DISTRIBUIDORA, arquivo(Fixtures.NFE_SAIDA_HACKATHON), arquivo(Fixtures.NFE_ENTRADA_IBSCBS))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/clientes/1/notas")).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/clientes/1/notas?tipo=ENTRADA"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("ENTRADA"));
        mvc.perform(get("/api/clientes/1/notas?competencia=2026-08"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("SAIDA"));
        mvc.perform(get("/api/clientes/2/notas")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void filtrosInvalidosDevolvem400() throws Exception {
        mvc.perform(get("/api/clientes/1/notas?tipo=XPTO")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/clientes/1/notas?competencia=08-2026")).andExpect(status().isBadRequest());
    }

    @Test
    void clienteOuNotaInexistenteDevolve404() throws Exception {
        upload(999, arquivo(Fixtures.NFE_SAIDA_HACKATHON)).andExpect(status().isNotFound());
        mvc.perform(get("/api/notas/999")).andExpect(status().isNotFound());
    }

    private ResultActions upload(long clienteId, MockMultipartFile... arquivos) throws Exception {
        var req = multipart("/api/clientes/" + clienteId + "/notas");
        for (MockMultipartFile a : arquivos) {
            req.file(a);
        }
        return mvc.perform(req);
    }

    private static MockMultipartFile arquivo(String fixture) {
        return arquivo(fixture, Fixtures.bytes(fixture));
    }

    private static MockMultipartFile arquivo(String nome, byte[] conteudo) {
        return new MockMultipartFile("arquivos", nome, "application/xml", conteudo);
    }

    private static byte[] xmlCom(String de, String para) {
        return Fixtures.texto(Fixtures.NFE_SAIDA_HACKATHON).replace(de, para).getBytes(StandardCharsets.UTF_8);
    }
}

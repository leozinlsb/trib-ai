package br.com.tribia.controller;

import br.com.tribia.Fixtures;
import br.com.tribia.model.Item;
import br.com.tribia.repository.ClassificacaoCacheRepository;
import br.com.tribia.repository.ItemRepository;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.util.ChaveClassificacao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Revisão sobre os dados do seed. Na farmácia (cliente 2), 14 itens estão abaixo de 0,70 de confiança:
 * medicamentos (0,65) e vitamina C (0,60). Os testes que alteram dados rodam numa transação desfeita ao final.
 */
@SpringBootTest(properties = "tribia.calculo.modo=SIMPLIFICADA")
@AutoConfigureMockMvc
@WithUserDetails("admin@tribia.local")
class RevisaoControllerTest {

    static final String VITAMINA_C_VENDA = "VITAMINA C 1G 30 COMPRIMIDOS EFERVESCENTES";
    static final String DIPIRONA_VENDA = "DIPIRONA SODICA 500MG 10 COMPRIMIDOS";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ItemRepository itemRepository;

    @Autowired
    ClassificacaoCacheRepository cacheRepository;

    @Autowired
    TabelaCClassTrib tabela;

    @Test
    void listaOsPendentesDaFarmaciaDoMenosConfiavelParaOMais() throws Exception {
        mvc.perform(get("/api/clientes/2/revisao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confiancaMinima").value(0.70))
                .andExpect(jsonPath("$.total").value(14))
                .andExpect(jsonPath("$.itens", hasSize(14)))
                // vitamina C (0,60) antes dos medicamentos (0,65); no empate, o de maior valor primeiro
                .andExpect(jsonPath("$.itens[0].descricao").value(VITAMINA_C_VENDA))
                .andExpect(jsonPath("$.itens[0].valorTotal").value(1494.00))
                .andExpect(jsonPath("$.itens[0].motivos[0]").value("CONFIANCA_BAIXA"))
                .andExpect(jsonPath("$.itens[0].classificacao.cClassTrib").value("000001"))
                .andExpect(jsonPath("$.itens[0].classificacao.justificativa", containsString("confirmar na revisão")))
                .andExpect(jsonPath("$.itens[0].opcoesSugeridas").isArray())
                .andExpect(jsonPath("$.itens[3].classificacao.confianca").value(0.65));

        mvc.perform(get("/api/clientes/1/revisao")).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @Transactional
    void aceitarAplicaAosIdenticosTiraDaFilaERecalcula() throws Exception {
        long id = itemId(2, DIPIRONA_VENDA);

        revisar(id, "{\"aceitar\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itensAtualizados").value(2))              // vendas 501 e 502
                .andExpect(jsonPath("$.notasRecalculadas", hasSize(2)))
                .andExpect(jsonPath("$.item.classificacao.aceita").value(true))
                .andExpect(jsonPath("$.item.classificacao.revisada").value(true))
                .andExpect(jsonPath("$.item.classificacao.origem").value("CACHE"))
                .andExpect(jsonPath("$.item.classificacao.confianca").value(0.65))
                .andExpect(jsonPath("$.item.calculo").exists());

        mvc.perform(get("/api/clientes/2/revisao")).andExpect(jsonPath("$.total").value(12));
        mvc.perform(get("/api/clientes/2/dashboard")).andExpect(jsonPath("$.indicadores.pendentesRevisao").value(12));
    }

    @Test
    @Transactional
    void corrigirGravaComoManualNoCacheValidadoERecalculaComANovaReducao() throws Exception {
        long id = itemId(2, VITAMINA_C_VENDA);

        revisar(id, "{\"cClassTrib\": \"200032\", \"justificativa\": \"Produto registrado na Anvisa como medicamento.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itensAtualizados").value(2))
                .andExpect(jsonPath("$.avisos", hasSize(0)))
                .andExpect(jsonPath("$.item.classificacao.origem").value("MANUAL"))
                .andExpect(jsonPath("$.item.classificacao.cst").value("200"))          // deduzido da tabela
                .andExpect(jsonPath("$.item.classificacao.regime").value("REDUZIDA"))
                .andExpect(jsonPath("$.item.classificacao.confianca").value(1))
                .andExpect(jsonPath("$.item.classificacao.revisada").value(true))
                .andExpect(jsonPath("$.item.calculo.reducaoCbs").value(60))
                // nota 501: 45 x 24,90 = 1.120,50. CBS 9,43% x 40% = 42,265 -> 42,27; IBS 0,02% = 0,22 + 0,22
                .andExpect(jsonPath("$.item.calculo.imposto2027").value(42.71));

        mvc.perform(get("/api/clientes/2/revisao")).andExpect(jsonPath("$.total").value(12));

        var cache = cacheRepository.findByChave(ChaveClassificacao.daEmpresa(2L, "21069030", VITAMINA_C_VENDA)).orElseThrow();
        assertThat(cache.getCClassTrib()).isEqualTo("200032");
        assertThat(cache.getFonte()).isEqualTo("MANUAL");
        assertThat(cache.isValidada()).isTrue();
    }

    @Test
    @Transactional
    void semAplicarAosIguaisSoOItemMuda() throws Exception {
        long id = itemId(2, DIPIRONA_VENDA);

        revisar(id, "{\"aceitar\": true, \"aplicarAosIguais\": false}")
                .andExpect(jsonPath("$.itensAtualizados").value(1))
                .andExpect(jsonPath("$.notasRecalculadas", hasSize(1)));

        mvc.perform(get("/api/clientes/2/revisao")).andExpect(jsonPath("$.total").value(13));
    }

    @Test
    @Transactional
    void beneficioDeAnexoForaDaListaDoNcmEhGravadoComAviso() throws Exception {
        long id = itemId(3, "DETERGENTE LIQUIDO NEUTRO 500ML");

        revisar(id, "{\"cClassTrib\": \"200035\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.classificacao.cClassTrib").value("200035"))
                .andExpect(jsonPath("$.avisos", hasItem(containsString("não consta da lista oficial"))));
    }

    @Test
    @Transactional
    void usoEConsumoTiraOCreditoDaCompra() throws Exception {
        // compra de biscoitos da distribuidora: 690,00 -> crédito 2027 de 65,07 + 0,34 + 0,34
        long id = itemId(1, "BISCOITO RECHEADO SABOR CHOCOLATE 130G");

        revisar(id, "{\"creditavel\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.creditavel").value(false))
                .andExpect(jsonPath("$.item.calculo.impostoHoje").value(0))
                .andExpect(jsonPath("$.item.calculo.imposto2027").value(0));

        mvc.perform(get("/api/clientes/1/dashboard"))
                .andExpect(jsonPath("$.indicadores.credito2027").value(89.20)); // 154,95 - 65,75
    }

    @Test
    void creditavelNaoSeAplicaAVenda() throws Exception {
        revisar(itemId(1, "CHOCOLATE AO LEITE 90G"), "{\"creditavel\": false}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("entrada")));
    }

    @Test
    void validacoes() throws Exception {
        long id = itemId(2, DIPIRONA_VENDA);
        revisar(id, "{}").andExpect(status().isBadRequest());
        revisar(id, "{\"cClassTrib\": \"999999\"}").andExpect(status().isUnprocessableEntity());
        revisar(id, "{\"cst\": \"000\", \"cClassTrib\": \"200032\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("O cClassTrib 200032 pertence ao CST 200, não ao 000."));
        revisar(99999, "{\"aceitar\": true}").andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void itemSemClassificacaoNaoPodeSerAceitoMasPodeSerCorrigido() throws Exception {
        String body = mvc.perform(multipart("/api/clientes/1/notas").file(new MockMultipartFile("arquivos",
                        Fixtures.NFE_SAIDA_HACKATHON, "application/xml", Fixtures.bytes(Fixtures.NFE_SAIDA_HACKATHON))).with(csrf()))
                .andReturn().getResponse().getContentAsString();
        long nota = json.readTree(body).get("importadas").get(0).get("id").asLong();
        JsonNode itens = json.readTree(mvc.perform(get("/api/notas/" + nota)).andReturn().getResponse().getContentAsString())
                .get("itens");
        long detergente = itens.get(5).get("id").asLong();

        mvc.perform(get("/api/clientes/1/revisao"))
                .andExpect(jsonPath("$.total").value(8))
                .andExpect(jsonPath("$.itens[0].motivos[0]").value("SEM_CLASSIFICACAO"));

        revisar(detergente, "{\"aceitar\": true}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("informe o cClassTrib")));

        revisar(detergente, "{\"cClassTrib\": \"000001\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.classificacao.origem").value("MANUAL"))
                .andExpect(jsonPath("$.item.calculo.imposto2027").value(19.14));
    }

    @Test
    void opcoesDeClassificacaoComAsSugeridasPeloNcmPrimeiro() throws Exception {
        mvc.perform(get("/api/classificacoes/opcoes"))
                .andExpect(jsonPath("$", hasSize(tabela.opcoesNfe().size())));

        mvc.perform(get("/api/classificacoes/opcoes").param("ncm", "10063021"))
                .andExpect(jsonPath("$[0].sugeridaPeloNcm").value(true))
                .andExpect(jsonPath("$[?(@.cClassTrib == '200003')].sugeridaPeloNcm").value(true))
                .andExpect(jsonPath("$[?(@.cClassTrib == '200003')].exigeNcmNaLista").value(true))
                .andExpect(jsonPath("$[?(@.cClassTrib == '000001')].sugeridaPeloNcm").value(false));
    }

    private ResultActions revisar(long itemId, String corpo) throws Exception {
        return mvc.perform(put("/api/itens/" + itemId + "/classificacao").contentType(MediaType.APPLICATION_JSON).content(corpo).with(csrf()));
    }

    /** Primeiro item do cliente com a descrição (na ordem das notas). */
    private long itemId(long clienteId, String descricao) {
        return itemRepository.doClienteNoPeriodo(clienteId, null, null).stream()
                .filter(i -> i.getDescricao().equals(descricao))
                .map(Item::getId).findFirst().orElseThrow();
    }
}

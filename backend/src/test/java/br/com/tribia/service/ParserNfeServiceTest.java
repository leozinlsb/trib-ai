package br.com.tribia.service;

import br.com.tribia.Fixtures;
import br.com.tribia.exception.NotaRejeitadaException;
import br.com.tribia.service.nfe.ItemLido;
import br.com.tribia.service.nfe.NfeLida;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserNfeServiceTest {

    private final ParserNfeService parser = new ParserNfeService();

    @Test
    void leNotaDeTesteDoHackathon() {
        NfeLida nota = parser.ler(Fixtures.bytes(Fixtures.NFE_SAIDA_HACKATHON));

        assertThat(nota.chave()).isEqualTo("35260810433218000193550010000012341123456789");
        assertThat(nota.modelo()).isEqualTo("55");
        assertThat(nota.numero()).isEqualTo(1234L);
        assertThat(nota.serie()).isEqualTo(1);
        assertThat(nota.finalidade()).isEqualTo(1);
        assertThat(nota.tipoOperacao()).isEqualTo(1);
        assertThat(nota.ambiente()).isEqualTo(2);
        assertThat(nota.dataEmissao()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(nota.emitente().documento()).isEqualTo("10433218000193");
        assertThat(nota.emitente().uf()).isEqualTo("SP");
        assertThat(nota.destinatario().documento()).isEqualTo("27865345000164");
        assertThat(nota.valorTotal()).isEqualByComparingTo("4071.10");
        assertThat(nota.valorProdutos()).isEqualByComparingTo("4071.10");

        assertThat(nota.itens()).hasSize(8);
        BigDecimal somaItens = nota.itens().stream().map(ItemLido::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaItens).isEqualByComparingTo("4071.10");
    }

    @Test
    void leCamposDoItemEGruposVariaveisDeTributos() {
        NfeLida nota = parser.ler(Fixtures.bytes(Fixtures.NFE_SAIDA_HACKATHON));

        ItemLido arroz = nota.itens().get(0);
        assertThat(arroz.nItem()).isEqualTo(1);
        assertThat(arroz.codigo()).isEqualTo("ARZ001");
        assertThat(arroz.descricao()).isEqualTo("ARROZ TIPO 1 5KG");
        assertThat(arroz.ncm()).isEqualTo("10063021");
        assertThat(arroz.cfop()).isEqualTo("5102");
        assertThat(arroz.unidade()).isEqualTo("UN");
        assertThat(arroz.quantidade()).isEqualByComparingTo("40");
        assertThat(arroz.valorUnitario()).isEqualByComparingTo("27.90");
        assertThat(arroz.valorTotal()).isEqualByComparingTo("1116.00");
        // ICMS40 e PISNT: sem valor, vira zero
        assertThat(arroz.vIcms()).isEqualByComparingTo("0");
        assertThat(arroz.cstPis()).isEqualTo("06");
        assertThat(arroz.vPis()).isEqualByComparingTo("0");
        assertThat(arroz.ibsCbs()).isNull();

        ItemLido dipirona = nota.itens().get(3);
        assertThat(dipirona.cstPis()).isEqualTo("04");
        assertThat(dipirona.vIcms()).isEqualByComparingTo("37.26");

        ItemLido detergente = nota.itens().get(5);
        assertThat(detergente.cstPis()).isEqualTo("01");
        assertThat(detergente.vPis()).isEqualByComparingTo("3.31");
        assertThat(detergente.cstCofins()).isEqualTo("01");
        assertThat(detergente.vCofins()).isEqualByComparingTo("15.27");
    }

    @Test
    void aceitaRaizNfeProcELeGrupoIbsCbsDestacado() {
        NfeLida nota = parser.ler(Fixtures.bytes(Fixtures.NFE_ENTRADA_IBSCBS));

        assertThat(nota.chave()).isEqualTo("35260951938267000165550010000056781876543216");
        assertThat(nota.emitente().documento()).isEqualTo("51938267000165");
        assertThat(nota.destinatario().documento()).isEqualTo("10433218000193");
        assertThat(nota.itens()).hasSize(2);

        var g = nota.itens().get(0).ibsCbs();
        assertThat(g).isNotNull();
        assertThat(g.getCst()).isEqualTo("000");
        assertThat(g.getCClassTrib()).isEqualTo("000001");
        assertThat(g.getVBc()).isEqualByComparingTo("360.00");
        assertThat(g.getPIbsUf()).isEqualByComparingTo("0.10");
        assertThat(g.getVIbsUf()).isEqualByComparingTo("0.36");
        assertThat(g.getVIbsMun()).isEqualByComparingTo("0");
        assertThat(g.getVIbs()).isEqualByComparingTo("0.36");
        assertThat(g.getPCbs()).isEqualByComparingTo("0.90");
        assertThat(g.getVCbs()).isEqualByComparingTo("3.24");
    }

    @Test
    void rejeitaXmlSemNamespaceDaNfe() {
        String semNs = Fixtures.texto(Fixtures.NFE_SAIDA_HACKATHON)
                .replace(" xmlns=\"http://www.portalfiscal.inf.br/nfe\"", "");

        assertThatThrownBy(() -> parser.ler(semNs.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(NotaRejeitadaException.class)
                .hasMessageContaining("namespace");
    }

    @Test
    void rejeitaXmlMalformadoVazioEOutraRaiz() {
        assertThatThrownBy(() -> parser.ler("<NFe>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(NotaRejeitadaException.class).hasMessageContaining("malformado");
        assertThatThrownBy(() -> parser.ler(new byte[0]))
                .isInstanceOf(NotaRejeitadaException.class).hasMessageContaining("vazio");
        assertThatThrownBy(() -> parser.ler("<cteProc/>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(NotaRejeitadaException.class).hasMessageContaining("não é uma NF-e");
    }

    @Test
    void rejeitaDoctypeParaEvitarXxe() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE NFe [<!ENTITY x SYSTEM "file:///c:/windows/win.ini">]>
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe">&x;</NFe>
                """;
        assertThatThrownBy(() -> parser.ler(xxe.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(NotaRejeitadaException.class)
                .hasMessageContaining("malformado");
    }

    @Test
    void rejeitaChaveComDigitoVerificadorErrado() {
        String chaveErrada = Fixtures.texto(Fixtures.NFE_SAIDA_HACKATHON)
                .replace("NFe35260810433218000193550010000012341123456789", "NFe35260810433218000193550010000012341123456780");

        assertThatThrownBy(() -> parser.ler(chaveErrada.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(NotaRejeitadaException.class)
                .hasMessageContaining("Chave de acesso inválida");
    }
}

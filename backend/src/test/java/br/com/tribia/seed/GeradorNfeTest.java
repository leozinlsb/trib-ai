package br.com.tribia.seed;

import br.com.tribia.seed.CatalogoSeed.NotaSeed;
import br.com.tribia.service.ParserNfeService;
import br.com.tribia.service.nfe.ItemLido;
import br.com.tribia.service.nfe.NfeLida;
import br.com.tribia.util.CnpjUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GeradorNfeTest {

    private final ParserNfeService parser = new ParserNfeService();

    @Test
    void todasAsNotasDoCatalogoSaoLidasPeloParserComTotaisCoerentes() {
        Set<String> chaves = new HashSet<>();
        for (NotaSeed s : CatalogoSeed.notas()) {
            NfeLida lida = parser.ler(GeradorNfe.gerar(s.nota()).getBytes(StandardCharsets.UTF_8));

            assertThat(chaves.add(lida.chave())).as("chave repetida: %s", lida.chave()).isTrue();
            assertThat(CnpjUtil.valido(lida.emitente().documento())).isTrue();
            assertThat(CnpjUtil.valido(lida.destinatario().documento())).isTrue();
            assertThat(lida.ambiente()).isEqualTo(2);
            assertThat(s.cnpjCliente()).isIn(lida.emitente().documento(), lida.destinatario().documento());

            BigDecimal soma = lida.itens().stream().map(ItemLido::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(lida.valorProdutos()).isEqualByComparingTo(soma);
            assertThat(lida.valorTotal()).isEqualByComparingTo(soma);
        }
    }

    /** Para a demo fazer sentido, nenhum cliente pode comprar mais do que vende (evita saldo credor artificial). */
    @Test
    void comprasDeCadaClienteFicamAbaixoDe70PorCentoDasVendas() {
        Map<String, BigDecimal> vendas = new HashMap<>();
        Map<String, BigDecimal> compras = new HashMap<>();
        for (NotaSeed s : CatalogoSeed.notas()) {
            if (s.aoVivo()) {
                continue;
            }
            BigDecimal total = s.nota().itens().stream()
                    .map(GeradorNfe.ItemNfe::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            var alvo = s.nota().emitente().cnpj().equals(s.cnpjCliente()) ? vendas : compras;
            alvo.merge(s.cnpjCliente(), total, BigDecimal::add);
        }
        assertThat(vendas).hasSize(3);
        vendas.forEach((cnpj, v) -> assertThat(compras.get(cnpj))
                .as("compras do cliente %s (vendas %s)", cnpj, v)
                .isLessThanOrEqualTo(v.multiply(new BigDecimal("0.70"))));
    }

    @Test
    void calculaImpostosDoItemConformeSituacaoDePisCofins() {
        NotaSeed primeira = CatalogoSeed.notas().get(0);
        List<ItemLido> itens = parser.ler(GeradorNfe.gerar(primeira.nota()).getBytes(StandardCharsets.UTF_8)).itens();

        ItemLido arroz = itens.get(0);          // 80 x 27,90, alíquota zero, ICMS isento
        assertThat(arroz.valorTotal()).isEqualByComparingTo("2232.00");
        assertThat(arroz.cstPis()).isEqualTo("06");
        assertThat(arroz.vPis()).isEqualByComparingTo("0");
        assertThat(arroz.vIcms()).isEqualByComparingTo("0");

        ItemLido biscoito = itens.get(4);       // 120 x 3,49 = 418,80; Lucro Real 1,65% + 7,6%; ICMS 18%
        assertThat(biscoito.valorTotal()).isEqualByComparingTo("418.80");
        assertThat(biscoito.cstPis()).isEqualTo("01");
        assertThat(biscoito.vPis()).isEqualByComparingTo("6.91");     // 6,9102
        assertThat(biscoito.vCofins()).isEqualByComparingTo("31.83");  // 31,8288
        assertThat(biscoito.vIcms()).isEqualByComparingTo("75.38");    // 75,384
    }
}

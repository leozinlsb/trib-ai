package br.com.tribia.service.apuracao;

import br.com.tribia.Fixtures;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static br.com.tribia.model.Regime.LUCRO_PRESUMIDO;
import static br.com.tribia.model.Regime.LUCRO_REAL;
import static br.com.tribia.model.TipoNota.ENTRADA;
import static br.com.tribia.model.TipoNota.SAIDA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fórmulas da seção 3 do adendo. Alíquotas iguais às de application.properties. */
class RegrasApuracaoTest {

    final RegrasApuracao regras = new RegrasApuracao(Fixtures.ALIQUOTAS);

    @Nested
    class PisCofinsHoje {

        @Test
        void saidaUsaOPisCofinsDestacadoNaNotaEmQualquerRegime() {
            // detergente da nota de teste: 200,88 a 1,65% + 7,6%
            var real = new ItemTributavel(bd("200.88"), "01", bd("3.31"), bd("15.27"), true);
            assertThat(regras.pisCofinsHoje(LUCRO_REAL, SAIDA, real)).isEqualByComparingTo("18.58");

            // farmácia (Presumido): vitamina C 1.120,50 a 0,65% + 3%
            var presumido = new ItemTributavel(bd("1120.50"), "01", bd("7.28"), bd("33.62"), true);
            assertThat(regras.pisCofinsHoje(LUCRO_PRESUMIDO, SAIDA, presumido)).isEqualByComparingTo("40.90");
        }

        @Test
        void saidaComAliquotaZeroOuMonofasicoNaoGeraDebito() {
            var arroz = new ItemTributavel(bd("1116.00"), "06", bd("0"), bd("0"), true);
            var dipirona = new ItemTributavel(bd("207.00"), "04", bd("0"), bd("0"), true);

            assertThat(regras.pisCofinsHoje(LUCRO_REAL, SAIDA, arroz)).isZero();
            assertThat(regras.pisCofinsHoje(LUCRO_PRESUMIDO, SAIDA, dipirona)).isZero();
        }

        @Test
        void entradaNoLucroRealCreditaNovePorCentoEVinteECincoSobreOValor() {
            // detergente comprado: 360,00 x 1,65% = 5,94 e x 7,6% = 27,36
            var item = new ItemTributavel(bd("360.00"), "01", bd("5.94"), bd("27.36"), true);
            assertThat(regras.pisCofinsHoje(LUCRO_REAL, ENTRADA, item)).isEqualByComparingTo("33.30");
        }

        @Test
        void entradaComCstSemCreditoNaoCredita() {
            var arroz = new ItemTributavel(bd("4300.00"), "06", bd("0"), bd("0"), true);
            var monofasico = new ItemTributavel(bd("570.00"), "04", bd("0"), bd("0"), true);

            assertThat(regras.pisCofinsHoje(LUCRO_REAL, ENTRADA, arroz)).isZero();
            assertThat(regras.pisCofinsHoje(LUCRO_REAL, ENTRADA, monofasico)).isZero();
        }

        @Test
        void entradaDeUsoEConsumoNaoCredita() {
            var naoCreditavel = new ItemTributavel(bd("360.00"), "01", bd("5.94"), bd("27.36"), false);
            assertThat(regras.pisCofinsHoje(LUCRO_REAL, ENTRADA, naoCreditavel)).isZero();
        }

        @Test
        void entradaNoPresumidoNuncaCredita() {
            var item = new ItemTributavel(bd("2940.00"), "01", bd("48.51"), bd("223.44"), true);
            assertThat(regras.pisCofinsHoje(LUCRO_PRESUMIDO, ENTRADA, item)).isZero();
        }

        @Test
        void arredondaCadaTributoSeparadamenteComHalfEven() {
            // 1,00 x 1,65% = 0,0165 -> 0,02 ; 1,00 x 7,6% = 0,076 -> 0,08 ; total 0,10
            var item = new ItemTributavel(bd("1.00"), "01", bd("0"), bd("0"), true);
            assertThat(regras.pisCofinsHoje(LUCRO_REAL, ENTRADA, item)).isEqualByComparingTo("0.10");
            // 0,30 x 1,65% = 0,00495 -> 0,00 ; 0,30 x 7,6% = 0,0228 -> 0,02
            var pequeno = new ItemTributavel(bd("0.30"), "01", bd("0"), bd("0"), true);
            assertThat(regras.pisCofinsHoje(LUCRO_REAL, ENTRADA, pequeno)).isEqualByComparingTo("0.02");
        }
    }

    @Nested
    class Tributos2027Simplificado {

        @Test
        void tributacaoIntegral() {
            Tributos2027 t = regras.tributos2027(bd("1000.00"), ParametrosClassificacao.INTEGRAL);

            assertThat(t.vCbs()).isEqualByComparingTo("94.30");
            assertThat(t.vIbsUf()).isEqualByComparingTo("0.50");
            assertThat(t.vIbsMun()).isEqualByComparingTo("0.50");
            assertThat(t.vIs()).isEqualByComparingTo("0");
            assertThat(t.totalDebito()).isEqualByComparingTo("95.30");
        }

        @Test
        void reducaoDe60e30PorCento() {
            Tributos2027 r60 = regras.tributos2027(bd("1000.00"), reducao("60"));
            assertThat(r60.vCbs()).isEqualByComparingTo("37.72");
            assertThat(r60.vIbsUf()).isEqualByComparingTo("0.20");
            assertThat(r60.vIbsMun()).isEqualByComparingTo("0.20");

            Tributos2027 r30 = regras.tributos2027(bd("1000.00"), reducao("30"));
            assertThat(r30.vCbs()).isEqualByComparingTo("66.01");
        }

        @Test
        void reducaoDe100PorCentoEhAliquotaZero() {
            Tributos2027 t = regras.tributos2027(bd("1116.00"), reducao("100"));
            assertThat(t.totalDebito()).isZero();
        }

        @Test
        void impostoSeletivoEntraNaBaseDaCbsEDoIbs() {
            // mesmos números da calculadora oficial: 479,52 + IS 10% (47,95) = base 527,47
            var comIs = new ParametrosClassificacao(bd("0"), bd("0"), bd("10"));
            Tributos2027 t = regras.tributos2027(bd("479.52"), comIs);

            assertThat(t.vIs()).isEqualByComparingTo("47.95");
            assertThat(t.vCbs()).isEqualByComparingTo("49.74");
            assertThat(t.vIbsUf()).isEqualByComparingTo("0.26");
            assertThat(t.vIbsMun()).isEqualByComparingTo("0.26");
        }

        @Test
        void impostoSeletivoSomaNoDebitoMasNaoNoCredito() {
            var comIs = new ParametrosClassificacao(bd("0"), bd("0"), bd("10"));
            Tributos2027 t = regras.tributos2027(bd("479.52"), comIs);

            assertThat(regras.imposto2027(SAIDA, t, true)).isEqualByComparingTo(t.vCbs().add(t.vIbs()).add(t.vIs()));
            assertThat(regras.imposto2027(ENTRADA, t, true)).isEqualByComparingTo(t.vCbs().add(t.vIbs()));
        }

        @Test
        void entradaNaoCreditavelNaoGeraCreditoEm2027() {
            Tributos2027 t = regras.tributos2027(bd("360.00"), ParametrosClassificacao.INTEGRAL);
            assertThat(regras.imposto2027(ENTRADA, t, false)).isZero();
        }

        @Test
        void rejeitaReducaoForaDe0a100() {
            assertThatThrownBy(() -> reducao("120")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> reducao("-1")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ComparativoEImpostoLiquido {

        @Test
        void presumidoNaoTemCreditoHojeMasTemEm2027() {
            var compra = new ItemTributavel(bd("2940.00"), "01", bd("48.51"), bd("223.44"), true);
            Tributos2027 t = regras.tributos2027(compra.valor(), ParametrosClassificacao.INTEGRAL);

            Comparativo c = regras.comparativoDoItem(LUCRO_PRESUMIDO, ENTRADA, compra, t);

            assertThat(c.hoje().credito()).isZero();
            assertThat(c.ano2027().credito()).isEqualByComparingTo("280.18"); // 277,24 + 1,47 + 1,47
            assertThat(c.ano2027().debito()).isZero();
        }

        @Test
        void liquidoEVariacaoDeUmPeriodo() {
            // saída de 1.000 tributada (hoje 92,50; 2027 95,30) e compra de 600 tributada
            var venda = new ItemTributavel(bd("1000.00"), "01", bd("16.50"), bd("76.00"), true);
            var compra = new ItemTributavel(bd("600.00"), "01", bd("9.90"), bd("45.60"), true);

            Comparativo total = regras.comparativoDoItem(LUCRO_REAL, SAIDA, venda,
                            regras.tributos2027(venda.valor(), ParametrosClassificacao.INTEGRAL))
                    .somar(regras.comparativoDoItem(LUCRO_REAL, ENTRADA, compra,
                            regras.tributos2027(compra.valor(), ParametrosClassificacao.INTEGRAL)));

            assertThat(total.hoje().liquido()).isEqualByComparingTo("37.00");     // 92,50 - 55,50
            assertThat(total.ano2027().liquido()).isEqualByComparingTo("38.12");  // 95,30 - 57,18
            assertThat(total.variacaoPct()).isEqualByComparingTo("3.03");         // +1,12 / 37,00
        }

        @Test
        void creditoMaiorQueDebitoViraSaldoCredorENaoImpostoNegativo() {
            Apuracao a = new Apuracao(bd("100.00"), bd("130.00"));

            assertThat(a.liquido()).isEqualByComparingTo("-30.00");
            assertThat(a.aPagar()).isZero();
            assertThat(a.saldoCredor()).isEqualByComparingTo("30.00");
            assertThat(a.temSaldoCredor()).isTrue();
        }

        @Test
        void variacaoNulaQuandoHojeNaoHaImpostoAPagar() {
            var semImpostoHoje = new Comparativo(new Apuracao(bd("0"), bd("0")), new Apuracao(bd("50"), bd("0")));
            var saldoCredorHoje = new Comparativo(new Apuracao(bd("10"), bd("20")), new Apuracao(bd("50"), bd("0")));

            assertThat(semImpostoHoje.variacaoPct()).isNull();
            assertThat(saldoCredorHoje.variacaoPct()).isNull();
        }

        @Test
        void cestaBasicaComReducaoTotalReduzOImpostoDaDistribuidora() {
            // arroz vendido (CST 06 hoje, sem PIS/Cofins) e biscoito tributado
            var arroz = new ItemTributavel(bd("2232.00"), "06", bd("0"), bd("0"), true);
            var biscoito = new ItemTributavel(bd("418.80"), "01", bd("6.91"), bd("31.83"), true);

            Comparativo c = regras.comparativoDoItem(LUCRO_REAL, SAIDA, arroz,
                            regras.tributos2027(arroz.valor(), reducao("100")))
                    .somar(regras.comparativoDoItem(LUCRO_REAL, SAIDA, biscoito,
                            regras.tributos2027(biscoito.valor(), ParametrosClassificacao.INTEGRAL)));

            assertThat(c.hoje().debito()).isEqualByComparingTo("38.74");
            assertThat(c.ano2027().debito()).isEqualByComparingTo("39.91"); // 39,49 + 0,21 + 0,21
        }
    }

    private static ParametrosClassificacao reducao(String pct) {
        return new ParametrosClassificacao(bd(pct), bd(pct), BigDecimal.ZERO);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}

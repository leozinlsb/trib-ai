package br.com.tribia.service.apuracao;

import br.com.tribia.config.AliquotasProperties;
import br.com.tribia.model.Regime;
import br.com.tribia.model.TipoNota;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Regras de débito, crédito e imposto líquido por regime (adendo, seção 3). Sem estado e sem banco:
 * recebe valores, devolve valores. Cada tributo é arredondado em 2 casas (HALF_EVEN) por item.
 *
 * Simplificações conhecidas (dizer no pitch):
 * - todo imposto destacado na compra é considerado pago (a LC 214 condiciona o crédito ao pagamento);
 * - compras de fornecedor do Simples Nacional não têm tratamento de crédito limitado;
 * - ICMS e ISS ficam fora do comparativo (não mudam em 2027).
 */
@Component
public class RegrasApuracao {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final AliquotasProperties aliquotas;

    public RegrasApuracao(AliquotasProperties aliquotas) {
        this.aliquotas = aliquotas;
    }

    /**
     * PIS + Cofins de hoje: débito na saída, crédito na entrada.
     * <ul>
     *   <li>Saída, qualquer regime: o PIS/Cofins destacado na própria nota. Na nota do Lucro Real ele vem a
     *       1,65% + 7,6%; na do Presumido, a 0,65% + 3%; em item de alíquota zero ou monofásico, zero.</li>
     *   <li>Entrada no Lucro Real: 1,65% + 7,6% sobre o valor do item, se ele for creditável e a nota de compra
     *       trouxer CST tributado (ver {@code tribia.aliquotas.hoje.cst-com-credito}).</li>
     *   <li>Entrada no Lucro Presumido: sem crédito (regime cumulativo).</li>
     * </ul>
     */
    public BigDecimal pisCofinsHoje(Regime regime, TipoNota tipo, ItemTributavel item) {
        if (tipo == TipoNota.SAIDA) {
            return nz(item.vPis()).add(nz(item.vCofins()));
        }
        boolean daCredito = regime == Regime.LUCRO_REAL
                && item.creditavel()
                && aliquotas.hoje().cstComCredito().contains(item.cstPisCofins());
        if (!daCredito) {
            return ZERO;
        }
        // O adendo também admite usar o PIS/Cofins destacado pelo fornecedor. Usamos a alíquota do comprador,
        // que é a regra do não cumulativo e dá o mesmo valor quando o fornecedor também é do Lucro Real.
        // TODO validar com especialista o caso de fornecedor do Presumido (destaca 0,65% + 3%).
        return percentual(item.valor(), aliquotas.hoje().pisNaoCumulativo())
                .add(percentual(item.valor(), aliquotas.hoje().cofinsNaoCumulativo()));
    }

    /**
     * Cálculo simplificado de CBS, IBS e IS de 2027 (plano B quando a calculadora oficial não responde).
     * Alíquota efetiva = alíquota de referência x (100 - redução) / 100.
     *
     * TODO base de cálculo: hoje é o valor do item. Conferir na LC 214 o que sai da base na transição
     * (ICMS, ISS, PIS/Cofins) e alinhar com o que a calculadora oficial faz.
     */
    public Tributos2027 tributos2027(BigDecimal base, ParametrosClassificacao p) {
        AliquotasProperties.Ano2027 a = aliquotas.ano2027();
        return new Tributos2027(
                comReducao(base, a.cbs(), p.reducaoCbs()),
                comReducao(base, a.ibsUf(), p.reducaoIbs()),
                comReducao(base, a.ibsMun(), p.reducaoIbs()),
                percentual(base, p.aliquotaIs()));
    }

    /**
     * Imposto de 2027 do item: na saída, débito de CBS + IBS + IS; na entrada, crédito de CBS + IBS
     * se o item for creditável. Vale para qualquer regime regular, inclusive o Presumido.
     */
    public BigDecimal imposto2027(TipoNota tipo, Tributos2027 tributos, boolean creditavel) {
        if (tipo == TipoNota.SAIDA) {
            return tributos.totalDebito();
        }
        return creditavel ? tributos.totalCredito() : ZERO;
    }

    /** Linha do comparativo de um item: hoje e 2027 já na coluna certa (débito ou crédito). */
    public Comparativo comparativoDoItem(Regime regime, TipoNota tipo, ItemTributavel item, Tributos2027 tributos2027) {
        BigDecimal hoje = pisCofinsHoje(regime, tipo, item);
        BigDecimal ano2027 = imposto2027(tipo, tributos2027, item.creditavel());
        return tipo == TipoNota.SAIDA
                ? new Comparativo(new Apuracao(hoje, ZERO), new Apuracao(ano2027, ZERO))
                : new Comparativo(new Apuracao(ZERO, hoje), new Apuracao(ZERO, ano2027));
    }

    private static BigDecimal comReducao(BigDecimal base, BigDecimal aliquota, BigDecimal reducao) {
        return base.multiply(aliquota).multiply(CEM.subtract(reducao))
                .divide(CEM.multiply(CEM), 2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal percentual(BigDecimal base, BigDecimal aliquota) {
        return base.multiply(aliquota).divide(CEM, 2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }
}

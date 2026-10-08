package br.com.tribia.service.apuracao;

import br.com.tribia.config.AliquotasProperties;
import br.com.tribia.model.Operacao;
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
     * PIS + Cofins de hoje para qualquer operação, em valor absoluto (o sinal do estorno é aplicado por quem chama).
     * <ul>
     *   <li>Venda e compra: as regras de {@link #pisCofinsHoje(Regime, TipoNota, ItemTributavel)}.</li>
     *   <li>Devolução de compra: estorna o crédito da compra, pela mesma regra do crédito (zero no Presumido).</li>
     *   <li>Devolução de venda: estorna o débito da venda. Se o próprio cliente emitiu a nota de devolução, usa o
     *       PIS/Cofins destacado nela; se foi o comprador, usa a alíquota do regime do cliente sobre itens
     *       tributados (Real: 1,65% + 7,6%; Presumido: 0,65% + 3%).</li>
     * </ul>
     */
    public BigDecimal pisCofinsHoje(Regime regime, Operacao operacao, ItemTributavel item, boolean emitidaPeloCliente) {
        return switch (operacao) {
            case VENDA -> pisCofinsHoje(regime, TipoNota.SAIDA, item);
            case COMPRA, DEVOLUCAO_DE_COMPRA -> pisCofinsHoje(regime, TipoNota.ENTRADA, item);
            case DEVOLUCAO_DE_VENDA -> {
                if (emitidaPeloCliente) {
                    yield nz(item.vPis()).add(nz(item.vCofins()));
                }
                if (!aliquotas.hoje().cstComCredito().contains(item.cstPisCofins())) {
                    yield ZERO;
                }
                boolean real = regime == Regime.LUCRO_REAL;
                yield percentual(item.valor(), real ? aliquotas.hoje().pisNaoCumulativo() : aliquotas.hoje().pisCumulativo())
                        .add(percentual(item.valor(),
                                real ? aliquotas.hoje().cofinsNaoCumulativo() : aliquotas.hoje().cofinsCumulativo()));
            }
        };
    }

    /**
     * Base do IBS/CBS de 2027: valor da operação menos os tributos que não a integram de 2026 a 2032
     * (LC 214, art. 12, § 2º, V: ICMS, ISS, PIS e Cofins incidentes na operação). Nunca negativa.
     */
    public static BigDecimal base2027(BigDecimal valorDaOperacao, BigDecimal vIcms, BigDecimal vPis, BigDecimal vCofins,
                                      boolean excluirTributos) {
        BigDecimal base = valorDaOperacao;
        if (excluirTributos) {
            base = base.subtract(nz(vIcms)).subtract(nz(vPis)).subtract(nz(vCofins));
        }
        return base.max(ZERO);
    }

    /**
     * Cálculo simplificado de CBS, IBS e IS de 2027 (plano B quando a calculadora oficial não responde).
     * Alíquota efetiva = alíquota de referência x (100 - redução) / 100.
     * O IS integra a base da CBS e do IBS (conferido contra a calculadora oficial 1.5.4).
     *
     * A base chega pronta (ver {@link #base2027}), igual à que a calculadora oficial recebe.
     */
    public Tributos2027 tributos2027(BigDecimal base, ParametrosClassificacao p) {
        AliquotasProperties.Ano2027 a = aliquotas.ano2027();
        return tributos2027(base, p, a.cbsEfetiva(), a.ibsUf(), a.ibsMun());
    }

    /** Mesmo cálculo, com alíquotas nominais informadas (ex.: cenário de CBS diferente da configurada). */
    public Tributos2027 tributos2027(BigDecimal base, ParametrosClassificacao p,
                                     BigDecimal cbs, BigDecimal ibsUf, BigDecimal ibsMun) {
        BigDecimal vIs = percentual(base, p.aliquotaIs());
        BigDecimal baseIbsCbs = base.add(vIs);
        return new Tributos2027(
                comReducao(baseIbsCbs, cbs, p.reducaoCbs()),
                comReducao(baseIbsCbs, ibsUf, p.reducaoIbs()),
                comReducao(baseIbsCbs, ibsMun, p.reducaoIbs()),
                vIs);
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

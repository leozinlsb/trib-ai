package br.com.tribia.client.calculadora;

import br.com.tribia.client.calculadora.OperacaoCalculo.AliquotasNominais;
import br.com.tribia.client.calculadora.OperacaoCalculo.ItemCalculo;
import br.com.tribia.client.calculadora.ResultadoCalculo.AliquotasAplicadas;
import br.com.tribia.client.calculadora.ResultadoCalculo.ItemCalculado;
import br.com.tribia.service.apuracao.ParametrosClassificacao;
import br.com.tribia.service.apuracao.RegrasApuracao;
import br.com.tribia.service.apuracao.Tributos2027;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaImpostoSeletivo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Plano B: calcula com as reduções da tabela oficial de cClassTrib e as alíquotas do IS por NCM, pelas mesmas
 * fórmulas da calculadora oficial (conferidas no CalculadoraOficialContratoTest). Não cobre regimes de alíquota
 * fixa ou uniforme: esses itens exigem a calculadora oficial.
 */
@Component
public class CalculadoraSimplificadaClient implements CalculadoraClient {

    private final RegrasApuracao regras;
    private final TabelaCClassTrib tabela;
    private final TabelaImpostoSeletivo tabelaIs;

    public CalculadoraSimplificadaClient(RegrasApuracao regras, TabelaCClassTrib tabela, TabelaImpostoSeletivo tabelaIs) {
        this.regras = regras;
        this.tabela = tabela;
        this.tabelaIs = tabelaIs;
    }

    @Override
    public ResultadoCalculo calcular(OperacaoCalculo op) {
        AliquotasNominais a = op.aliquotas();
        List<ItemCalculado> itens = new ArrayList<>();
        for (ItemCalculo i : op.itens()) {
            // IS só é cobrado no primeiro fornecimento (CST 000); na revenda (CST 200 / 200007) fica zero
            BigDecimal aliquotaIs = i.impostoSeletivo() == null ? null
                    : tabelaIs.aliquota(i.ncm(), op.dataFatoGerador().toLocalDate()).orElse(BigDecimal.ZERO);
            BigDecimal isCobrado = aliquotaIs != null && "000".equals(i.impostoSeletivo().cst())
                    ? aliquotaIs : BigDecimal.ZERO;

            ParametrosClassificacao p;
            try {
                p = tabela.parametros(i.cClassTrib(), isCobrado);
            } catch (IllegalArgumentException e) {
                throw new CalculadoraException(CalculadoraException.Tipo.REJEITADA,
                        "Item " + i.numero() + ": " + e.getMessage(), e);
            }
            Tributos2027 t = regras.tributos2027(i.baseCalculo(), p, a.cbs(), a.ibsUf(), a.ibsMun());
            itens.add(new ItemCalculado(i.numero(), t, new AliquotasAplicadas(a.cbs(), a.ibsUf(), a.ibsMun(),
                    semZero(p.reducaoCbs()), semZero(p.reducaoIbs()), aliquotaIs)));
        }
        return new ResultadoCalculo(OrigemCalculo.SIMPLIFICADA, true, itens, List.of());
    }

    /** Igual à oficial: sem redução, o campo não vem. */
    private static BigDecimal semZero(BigDecimal v) {
        return v.signum() == 0 ? null : v;
    }
}

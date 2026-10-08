package br.com.tribia.dto;

import br.com.tribia.model.Regime;
import br.com.tribia.model.RegimeTributario;

import java.math.BigDecimal;
import java.util.List;

/**
 * Painel do cliente (adendo, seção 6), já agregado: o front só desenha.
 * Prioridade 1: indicadores + comparativo. Prioridade 2: porMes e porRegime. Prioridade 3: topItens e
 * topFornecedores (o contador de pendentes está em indicadores).
 */
public record DashboardDto(
        ClienteResumoDto cliente,
        PeriodoDto periodo,
        IndicadoresDto indicadores,
        ComparativoDto comparativo,
        List<MesDto> porMes,
        List<FaixaRegimeDto> porRegime,
        List<ItemImpactoDto> topItens,
        List<FornecedorDto> topFornecedores,
        List<String> avisos
) {
    public record ClienteResumoDto(Long id, String nome, String cnpj, Regime regime) {
    }

    /** Competências AAAA-MM consideradas. Sem filtro, vão da primeira à última nota do cliente. */
    public record PeriodoDto(String de, String ate) {
    }

    /** Imposto líquido do mês, hoje x 2027 (só meses com nota). */
    public record MesDto(String competencia, BigDecimal faturamento, BigDecimal liquidoHoje, BigDecimal liquido2027) {
    }

    /**
     * Faturamento (soma dos itens vendidos) por efeito da classificação na CBS/IBS.
     * SUJEITO_IS reúne os produtos no campo do Imposto Seletivo (mesmo que, na revenda, o IS não seja cobrado).
     */
    public record FaixaRegimeDto(Faixa regime, BigDecimal valor, BigDecimal percentual, int itens) {
    }

    public enum Faixa {
        INTEGRAL, REDUZIDA, ALIQUOTA_ZERO, SEM_INCIDENCIA, SUJEITO_IS, OUTRO, SEM_CLASSIFICACAO;

        public static Faixa de(RegimeTributario r, boolean sujeitoIs) {
            if (sujeitoIs) {
                return SUJEITO_IS;
            }
            return r == null ? SEM_CLASSIFICACAO : Faixa.valueOf(r.name());
        }
    }

    /**
     * Efeito de um produto no imposto líquido: vendas somam débito, compras somam crédito (com sinal negativo).
     *
     * @param diferenca imposto2027 - impostoHoje; positivo = o produto aumenta o imposto do cliente
     */
    public record ItemImpactoDto(String descricao, String ncm, String cClassTrib, RegimeTributario regime,
                                 BigDecimal impostoHoje, BigDecimal imposto2027, BigDecimal diferenca) {
    }

    /** Fornecedor (contraparte das notas de entrada) e o crédito que gera. */
    public record FornecedorDto(String cnpj, String nome, BigDecimal compras, BigDecimal creditoHoje,
                                BigDecimal credito2027) {
    }
}

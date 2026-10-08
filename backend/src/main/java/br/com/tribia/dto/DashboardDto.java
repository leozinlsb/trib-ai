package br.com.tribia.dto;

import br.com.tribia.model.Regime;
import br.com.tribia.model.RegimeTributario;

import java.math.BigDecimal;
import java.util.List;

/**
 * Painel do cliente (adendo, seção 6), já agregado: o front só desenha.
 * Prioridade 1: indicadores + comparativo. Prioridade 2: porMes, porRegime (+ sujeitoIs). Prioridade 3: topItens e
 * topFornecedores (o contador de pendentes está em indicadores).
 * Devoluções entram com sinal negativo (estornam o faturamento, as compras, o débito ou o crédito).
 */
public record DashboardDto(
        ClienteResumoDto cliente,
        PeriodoDto periodo,
        IndicadoresDto indicadores,
        ComparativoDto comparativo,
        List<MesDto> porMes,
        List<FaixaRegimeDto> porRegime,
        SujeitoIsDto sujeitoIs,
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

    /** Faturamento (soma dos itens vendidos, menos devoluções) por efeito da classificação na CBS/IBS. */
    public record FaixaRegimeDto(Faixa regime, BigDecimal valor, BigDecimal percentual, int itens) {
    }

    /**
     * Parte do faturamento em produtos no campo do Imposto Seletivo. É um recorte do porRegime (esses produtos também
     * estão na faixa da sua classificação), não uma faixa a mais.
     */
    public record SujeitoIsDto(BigDecimal valor, BigDecimal percentual, int itens) {
    }

    public enum Faixa {
        INTEGRAL, REDUZIDA, ALIQUOTA_ZERO, SEM_INCIDENCIA, OUTRO, SEM_CLASSIFICACAO;

        public static Faixa de(RegimeTributario r) {
            return r == null ? SEM_CLASSIFICACAO : Faixa.valueOf(r.name());
        }
    }

    /** Como agrupar o ranking topItens. */
    public enum Agrupamento {
        /** NCM + descrição normalizada (padrão): cada produto. */
        PRODUTO,
        /** Só o NCM: junta o mesmo produto com descrições diferentes (ex.: a do fornecedor e a da venda). */
        NCM
    }

    /**
     * Efeito de um produto (ou de um NCM) no imposto líquido: vendas somam débito, compras somam crédito (negativo).
     *
     * @param produtos  quantas descrições diferentes foram somadas (1 no agrupamento por produto)
     * @param diferenca imposto2027 - impostoHoje; positivo = aumenta o imposto do cliente
     */
    public record ItemImpactoDto(String descricao, String ncm, String cClassTrib, RegimeTributario regime, int produtos,
                                 BigDecimal impostoHoje, BigDecimal imposto2027, BigDecimal diferenca) {
    }

    /** Fornecedor (contraparte das compras) e o crédito que gera, já descontadas as devoluções de compra. */
    public record FornecedorDto(String cnpj, String nome, BigDecimal compras, BigDecimal creditoHoje,
                                BigDecimal credito2027) {
    }
}

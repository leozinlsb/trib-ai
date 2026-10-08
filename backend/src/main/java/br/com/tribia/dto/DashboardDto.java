package br.com.tribia.dto;

import br.com.tribia.model.Regime;

import java.util.List;

/**
 * Painel do cliente (adendo, seção 6), já agregado: o front só desenha.
 * Prioridade 1: indicadores + comparativo. As prioridades 2 e 3 (por mês, por regime, top itens e fornecedores)
 * entram como campos novos, sem mudar estes.
 */
public record DashboardDto(
        ClienteResumoDto cliente,
        PeriodoDto periodo,
        IndicadoresDto indicadores,
        ComparativoDto comparativo,
        List<String> avisos
) {
    public record ClienteResumoDto(Long id, String nome, String cnpj, Regime regime) {
    }

    /** Competências AAAA-MM consideradas. Sem filtro, vão da primeira à última nota do cliente. */
    public record PeriodoDto(String de, String ate) {
    }
}

package br.com.tribia.dto;

import br.com.tribia.client.calculadora.OrigemCalculo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de POST /api/notas/{id}/calcular.
 *
 * @param origem          CALCULADORA (oficial) ou SIMPLIFICADA (plano B); null se nada foi calculado
 * @param aliquotaCbs     alíquota da CBS usada (configurada ou do cenário)
 * @param itensPendentes  itens sem classificação: ficam fora do cálculo até serem classificados
 * @param avisos          ex.: CBS estimada, calculadora fora do ar, NCM extinto
 */
public record CalculoNotaDto(
        Long notaId,
        OrigemCalculo origem,
        boolean simulado,
        BigDecimal aliquotaCbs,
        int itensCalculados,
        List<Integer> itensPendentes,
        List<String> avisos,
        ComparativoDto comparativo
) {
}

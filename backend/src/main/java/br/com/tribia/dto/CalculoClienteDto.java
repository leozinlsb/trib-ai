package br.com.tribia.dto;

import java.math.BigDecimal;
import java.util.List;

/** Resultado de POST /api/clientes/{id}/calcular: recalcula todas as notas do cliente (ex.: troca de cenário). */
public record CalculoClienteDto(
        Long clienteId,
        BigDecimal aliquotaCbs,
        int notas,
        int itensCalculados,
        int itensPendentes,
        List<String> avisos,
        ComparativoDto comparativo
) {
}

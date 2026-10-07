package br.com.tribia.client.calculadora;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Operação a calcular. A data define as regras aplicadas: para simular 2027, use uma data de 2027.
 *
 * @param codigoMunicipio código IBGE (7 dígitos) do local da operação
 */
public record OperacaoCalculo(String id, OffsetDateTime dataFatoGerador, String codigoMunicipio, String uf,
                              List<ItemCalculo> itens) {

    /**
     * @param numero         número do item na nota (nItem); volta igual no resultado
     * @param ncm            pode ser null; se a calculadora não conhecer o NCM, o item é recalculado sem ele
     * @param baseCalculo    valor do item
     * @param impostoSeletivo null quando o produto não está no campo do IS
     */
    public record ItemCalculo(int numero, String ncm, BigDecimal quantidade, String unidade, BigDecimal baseCalculo,
                              String cst, String cClassTrib, ImpostoSeletivo impostoSeletivo) {
    }

    /**
     * Classificação do item no Imposto Seletivo. O IS é monofásico: o fabricante usa CST 000 / 000001
     * (primeiro fornecimento); quem revende usa CST 200 / 200007 (tributado em operação anterior) e não paga IS.
     */
    public record ImpostoSeletivo(String cst, String cClassTrib) {

        public static final ImpostoSeletivo REVENDA = new ImpostoSeletivo("200", "200007");
    }
}

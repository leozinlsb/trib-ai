package br.com.tribia.client.calculadora;

/**
 * Cálculo de CBS, IBS e IS de 2027 para os itens de uma operação.
 * Implementações: oficial (Calculadora RTC da Receita, offline) e simplificada (plano B, alíquotas configuráveis).
 */
public interface CalculadoraClient {

    /**
     * @throws CalculadoraException se a calculadora estiver fora do ar ou recusar a operação
     */
    ResultadoCalculo calcular(OperacaoCalculo operacao);
}

package br.com.tribia.client.calculadora;

/** Falha ao calcular. Quem chama decide se cai para o cálculo simplificado. */
public class CalculadoraException extends RuntimeException {

    public enum Tipo {
        /** Fora do ar, timeout ou resposta ilegível: vale tentar o plano B. */
        INDISPONIVEL,
        /** A calculadora recusou os dados (ex.: cClassTrib inexistente): o problema está na entrada. */
        REJEITADA
    }

    private final Tipo tipo;

    public CalculadoraException(Tipo tipo, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.tipo = tipo;
    }

    public Tipo getTipo() {
        return tipo;
    }
}

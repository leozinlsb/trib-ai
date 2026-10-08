package br.com.tribia.client.llm;

public class LlmException extends RuntimeException {

    public enum Tipo {
        /** Sem chave de API (GEMINI_API_KEY) ou chave recusada. */
        NAO_CONFIGURADO,
        /** Fora do ar, sem cota, timeout ou sobrecarga em todos os modelos. */
        INDISPONIVEL,
        /** O modelo respondeu, mas sem conteúdo utilizável (vazio, bloqueado, cortado). */
        RESPOSTA_INVALIDA
    }

    private final Tipo tipo;

    public LlmException(Tipo tipo, String mensagem) {
        super(mensagem);
        this.tipo = tipo;
    }

    public LlmException(Tipo tipo, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.tipo = tipo;
    }

    public Tipo getTipo() {
        return tipo;
    }
}

package br.com.tribia.apipublica.seguranca;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Janela fixa de um minuto por chave (contagem atômica com {@link ConcurrentHashMap#compute}). Em memória: vale para
 * uma instância da API, como o limite de tentativas de login. Com várias instâncias, cada uma conta a sua parte
 * (o limite efetivo multiplica); aí o certo é um contador compartilhado (ver 10-PENDENCIAS-E-EVOLUCAO.md).
 */
public class LimitadorRequisicoes {

    private static final int LIMITE_DE_REGISTROS = 10_000;

    private record Janela(long minuto, int contagem) {
    }

    /**
     * @param permitido        se esta requisição cabe no limite
     * @param restante         quantas ainda cabem nesta janela
     * @param segundosParaNova segundos até a próxima janela (Retry-After)
     */
    public record Resultado(boolean permitido, int limite, int restante, long segundosParaNova) {
    }

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
    private final Clock relogio;

    public LimitadorRequisicoes(Clock relogio) {
        this.relogio = relogio;
    }

    /** Conta uma requisição para a chave e diz se ela cabe no limite por minuto. */
    public Resultado consumir(String chave, int limite) {
        long agora = relogio.millis();
        long minuto = agora / 60_000;
        limpar(minuto);
        Janela j = janelas.compute(chave, (k, atual) ->
                atual == null || atual.minuto() != minuto ? new Janela(minuto, 1) : new Janela(minuto, atual.contagem() + 1));
        long segundos = Math.max(1, ((minuto + 1) * 60_000 - agora + 999) / 1000);
        return new Resultado(j.contagem() <= limite, limite, Math.max(0, limite - j.contagem()), segundos);
    }

    /** Se a chave já passou do limite nesta janela, sem contar uma nova requisição. */
    public boolean excedido(String chave, int limite) {
        Janela j = janelas.get(chave);
        return j != null && j.minuto() == relogio.millis() / 60_000 && j.contagem() >= limite;
    }

    private void limpar(long minutoAtual) {
        if (janelas.size() >= LIMITE_DE_REGISTROS) {
            janelas.values().removeIf(j -> j.minuto() < minutoAtual);
        }
    }
}

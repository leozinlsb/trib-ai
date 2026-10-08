package br.com.tribia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JEV AI (Jev, da TypeSafe: https://docs.typesafe.ai/api), que pontua a compatibilidade entre a mercadoria e cada NCM
 * candidata da Inteligência Fiscal. A chave vem SEMPRE de variável de ambiente (JEV_API_KEY ou TYPESAFE_API_KEY, também
 * pelo .env da raiz): nunca vai para arquivo versionado nem para o navegador.
 *
 * @param modo            DESLIGADO (padrão: nenhuma chamada), HTTP (API real, cobra por token) ou SIMULADO (só
 *                        desenvolvimento/testes: pontuações fictícias marcadas como simulação; recusado no profile prod)
 * @param modelo          versão fixada (jev-1.13.0) para a mesma pergunta manter o mesmo significado; jev-latest muda sozinho
 * @param novasTentativas quantas vezes repetir em 429/529 (sobrecarga/limite), com espera crescente; outros erros não repetem
 * @param esperaInicial   primeira espera antes de repetir; dobra a cada tentativa, até esperaMaxima
 * @param maxCandidatas   NCMs por análise enviadas à JEV (uma pergunta sim/não por NCM)
 */
@ConfigurationProperties(prefix = "tribia.jev")
public record JevProperties(Modo modo, String url, String apiKey, String modelo, Duration timeoutConexao,
                            Duration timeoutResposta, int novasTentativas, Duration esperaInicial,
                            Duration esperaMaxima, int maxCandidatas) {

    public enum Modo {
        DESLIGADO, HTTP, SIMULADO
    }

    public JevProperties {
        if (modo == null) {
            modo = Modo.DESLIGADO;
        }
        if (url == null || url.isBlank()) {
            url = "https://api.typesafe.ai";
        }
        if (modelo == null || modelo.isBlank()) {
            modelo = "jev-1.13.0";
        }
        if (timeoutConexao == null) {
            timeoutConexao = Duration.ofSeconds(2);
        }
        if (timeoutResposta == null) {
            timeoutResposta = Duration.ofSeconds(15);
        }
        if (novasTentativas < 0) {
            novasTentativas = 0;
        }
        if (esperaInicial == null) {
            esperaInicial = Duration.ofMillis(500);
        }
        if (esperaMaxima == null) {
            esperaMaxima = Duration.ofSeconds(4);
        }
        if (maxCandidatas <= 0) {
            maxCandidatas = 10;
        }
    }

    public boolean chaveConfigurada() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Evita vazar a chave em logs. */
    @Override
    public String toString() {
        return "JevProperties[modo=" + modo + ", url=" + url + ", modelo=" + modelo + ", apiKey="
                + (chaveConfigurada() ? "***" : "(vazia)") + "]";
    }
}

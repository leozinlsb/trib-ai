package br.com.tribia.security;

import br.com.tribia.exception.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freia tentativa e erro de senha: depois de N falhas seguidas para o mesmo e-mail, o login desse e-mail fica
 * bloqueado por um tempo (429), mesmo com a senha certa, e sem gastar BCrypt. Um acerto zera a contagem.
 *
 * Em memória (uma instância da API): suficiente para a hospedagem de demonstração. O bloqueio por e-mail também
 * pode ser usado para travar o acesso de alguém de propósito; por isso dura pouco e é configurável.
 */
@Component
public class LimiteTentativasLogin {

    private static final int LIMITE_DE_REGISTROS = 10_000;

    private record Falhas(int quantidade, Instant ultima, Instant bloqueadoAte) {
    }

    private final Map<String, Falhas> porEmail = new ConcurrentHashMap<>();
    private final int maximo;
    private final Duration bloqueio;
    private final Clock relogio;

    @Autowired
    public LimiteTentativasLogin(@Value("${tribia.auth.max-tentativas:5}") int maximo,
                                 @Value("${tribia.auth.bloqueio:15m}") Duration bloqueio) {
        this(maximo, bloqueio, Clock.systemUTC());
    }

    LimiteTentativasLogin(int maximo, Duration bloqueio, Clock relogio) {
        this.maximo = maximo;
        this.bloqueio = bloqueio;
        this.relogio = relogio;
    }

    /** @throws ApiException 429 se o e-mail estiver bloqueado */
    public void verificar(String email) {
        Falhas f = porEmail.get(chave(email));
        if (f != null && f.bloqueadoAte() != null && relogio.instant().isBefore(f.bloqueadoAte())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas",
                    "Muitas tentativas de login com este e-mail. Aguarde alguns minutos e tente de novo.");
        }
    }

    public void falhou(String email) {
        Instant agora = relogio.instant();
        if (porEmail.size() >= LIMITE_DE_REGISTROS) {
            porEmail.values().removeIf(f -> f.ultima().plus(bloqueio).isBefore(agora));
        }
        porEmail.compute(chave(email), (k, f) -> {
            // falhas antigas (fora da janela) não contam
            int quantidade = f == null || f.ultima().plus(bloqueio).isBefore(agora) ? 1 : f.quantidade() + 1;
            return new Falhas(quantidade, agora, quantidade >= maximo ? agora.plus(bloqueio) : null);
        });
    }

    public void acertou(String email) {
        porEmail.remove(chave(email));
    }

    private static String chave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}

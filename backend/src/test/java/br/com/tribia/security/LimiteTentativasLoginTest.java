package br.com.tribia.security;

import br.com.tribia.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimiteTentativasLoginTest {

    /** Relógio que o teste avança. */
    static class Relogio extends Clock {
        Instant agora = Instant.parse("2026-10-08T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    final Relogio relogio = new Relogio();
    final LimiteTentativasLogin limite = new LimiteTentativasLogin(3, Duration.ofMinutes(15), relogio);

    @Test
    void bloqueiaDepoisDoMaximoDeFalhasMesmoComOutraGrafiaDoEmail() {
        limite.falhou("a@x.com");
        limite.falhou("A@X.com ");
        assertThatCode(() -> limite.verificar("a@x.com")).doesNotThrowAnyException();
        limite.falhou("a@x.com");
        assertThatThrownBy(() -> limite.verificar("a@X.COM"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(429));
        assertThatCode(() -> limite.verificar("outro@x.com")).doesNotThrowAnyException();
    }

    @Test
    void bloqueioExpiraEFalhasAntigasNaoContam() {
        for (int i = 0; i < 3; i++) limite.falhou("a@x.com");
        relogio.agora = relogio.agora.plus(Duration.ofMinutes(16));
        assertThatCode(() -> limite.verificar("a@x.com")).doesNotThrowAnyException();
        limite.falhou("a@x.com");
        assertThatCode(() -> limite.verificar("a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void acertoZeraAContagem() {
        limite.falhou("a@x.com");
        limite.falhou("a@x.com");
        limite.acertou("a@x.com");
        limite.falhou("a@x.com");
        assertThatCode(() -> limite.verificar("a@x.com")).doesNotThrowAnyException();
    }
}

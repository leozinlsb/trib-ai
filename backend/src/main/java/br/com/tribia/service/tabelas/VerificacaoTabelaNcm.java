package br.com.tribia.service.tabelas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Idade da tabela NCM embarcada (dados-oficiais/ncm-vigente.json). Avisa no log ao iniciar quando ela passou de
 * tribia.fiscal.ncm-dias-validade-tabela e informa o administrador pela API.
 *
 * Decisão: não atualiza sozinha. Trocar dados oficiais em produção sem revisão (e sem rodar os testes) muda resultados
 * de análises sem ninguém ver; a atualização é feita com ferramentas/atualizar_ncm.mjs e entra por commit.
 */
@Component
public class VerificacaoTabelaNcm {

    private static final Logger log = LoggerFactory.getLogger(VerificacaoTabelaNcm.class);
    static final String COMO_ATUALIZAR = "Na pasta backend: node ferramentas/atualizar_ncm.mjs; rode os testes e faça o commit da tabela.";

    public record Situacao(String fonte, String situacao, String ato, String extraidoEm, Long idadeDias, int limiteDias,
                           boolean desatualizada, String comoAtualizar) {
    }

    private final TabelaNcmVigente tabela;
    private final int limiteDias;

    public VerificacaoTabelaNcm(TabelaNcmVigente tabela,
                                @Value("${tribia.fiscal.ncm-dias-validade-tabela:120}") int limiteDias) {
        this.tabela = tabela;
        this.limiteDias = limiteDias;
    }

    public Situacao situacao(LocalDate hoje) {
        TabelaNcmVigente.Versao v = tabela.versao();
        Long idade = null;
        try {
            idade = ChronoUnit.DAYS.between(LocalDate.parse(v.extraidoEm()), hoje);
        } catch (DateTimeParseException | NullPointerException e) {
            // sem data de extração: tratada como desatualizada
        }
        boolean desatualizada = idade == null || idade > limiteDias;
        return new Situacao(v.fonte(), v.situacao(), v.ato(), v.extraidoEm(), idade, limiteDias, desatualizada, COMO_ATUALIZAR);
    }

    public Situacao situacao() {
        return situacao(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        Situacao s = situacao();
        if (s.desatualizada()) {
            log.warn("Tabela NCM possivelmente desatualizada: extraída em {} ({} dias; limite {}). {}", s.extraidoEm(),
                    s.idadeDias(), s.limiteDias(), COMO_ATUALIZAR);
        } else {
            log.info("Tabela NCM: {} ({}), extraída em {} ({} dias)", s.situacao(), s.ato(), s.extraidoEm(), s.idadeDias());
        }
    }
}

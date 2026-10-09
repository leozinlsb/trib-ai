package br.com.tribia.service.tabelas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class VerificacaoTabelaNcmTest {

    final TabelaNcmVigente tabela = new TabelaNcmVigente(new ObjectMapper());
    final VerificacaoTabelaNcm v = new VerificacaoTabelaNcm(tabela, 120);

    @Test
    void dentroDoPrazoNaoEstaDesatualizada() {
        LocalDate extracao = LocalDate.parse(tabela.versao().extraidoEm());
        var s = v.situacao(extracao.plusDays(30));
        assertThat(s.desatualizada()).isFalse();
        assertThat(s.idadeDias()).isEqualTo(30);
        assertThat(s.ato()).contains("Gecex");
        assertThat(s.comoAtualizar()).contains("atualizar_ncm.mjs");
    }

    @Test
    void depoisDoLimiteFicaDesatualizada() {
        LocalDate extracao = LocalDate.parse(tabela.versao().extraidoEm());
        assertThat(v.situacao(extracao.plusDays(121)).desatualizada()).isTrue();
        assertThat(v.situacao(extracao.plusDays(120)).desatualizada()).isFalse();
    }
}

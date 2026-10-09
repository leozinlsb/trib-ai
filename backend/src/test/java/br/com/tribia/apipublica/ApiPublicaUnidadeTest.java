package br.com.tribia.apipublica;

import br.com.tribia.apipublica.dto.ApiPublicaDtos.Mercadoria;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.SolicitacaoAnalise;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.StatusPublico;
import br.com.tribia.apipublica.model.EscopoApi;
import br.com.tribia.apipublica.seguranca.ChavesApi;
import br.com.tribia.apipublica.seguranca.LimitadorRequisicoes;
import br.com.tribia.apipublica.servico.ApiPublicaServiceTestes;
import br.com.tribia.apipublica.web.ApiPublicaExceptionHandler;
import br.com.tribia.model.StatusAnalise;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Partes da API pública que não precisam do Spring: chaves, limitador, estados públicos, normalização e erros. */
class ApiPublicaUnidadeTest {

    @Test
    void chaveGeradaTemFormatoEntropiaEHashEPrefixoSeparaveis() {
        Set<String> vistas = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            ChavesApi.ChaveGerada g = ChavesApi.gerar();
            assertThat(g.chaveCompleta()).matches("tribia_[0-9a-f]{12}_[A-Za-z0-9_-]{43}").hasSize(63);
            assertThat(ChavesApi.prefixo(g.chaveCompleta())).contains(g.prefixo());
            assertThat(g.hash()).hasSize(64).isEqualTo(ChavesApi.sha256(g.chaveCompleta())).doesNotContain(g.prefixo());
            assertThat(ChavesApi.confere(g.chaveCompleta(), g.hash())).isTrue();
            assertThat(vistas.add(g.chaveCompleta())).isTrue();
        }
    }

    @Test
    void chaveMalformadaNaoTemPrefixoESegredoErradoNaoConfere() {
        ChavesApi.ChaveGerada g = ChavesApi.gerar();
        assertThat(ChavesApi.prefixo(null)).isEmpty();
        assertThat(ChavesApi.prefixo("")).isEmpty();
        assertThat(ChavesApi.prefixo("tribia_xyz")).isEmpty();
        assertThat(ChavesApi.prefixo(g.chaveCompleta().toUpperCase())).isEmpty();
        assertThat(ChavesApi.prefixo(g.chaveCompleta() + "a")).isEmpty();
        String outra = ChavesApi.gerar().chaveCompleta();
        assertThat(ChavesApi.confere(outra, g.hash())).isFalse();
    }

    @Test
    void escoposConhecidosEDesconhecidos() {
        assertThat(EscopoApi.separar(" analises_ler , ANALISES_CRIAR"))
                .isEqualTo(EnumSet.of(EscopoApi.ANALISES_LER, EscopoApi.ANALISES_CRIAR));
        assertThat(EscopoApi.separar("notas_enviar,NOTAS_LER"))
                .isEqualTo(EnumSet.of(EscopoApi.NOTAS_ENVIAR, EscopoApi.NOTAS_LER));
        assertThat(EscopoApi.juntar(EnumSet.of(EscopoApi.ANALISES_LER))).isEqualTo("ANALISES_LER");
        assertThat(EscopoApi.separar("")).isEmpty();
        assertThatThrownBy(() -> EscopoApi.separar("ADMIN")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitadorContaPorJanelaDeUmMinutoEPorChave() {
        AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-10-09T12:00:10Z"));
        Clock relogio = new Clock() {
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            public Instant instant() {
                return agora.get();
            }
        };
        LimitadorRequisicoes l = new LimitadorRequisicoes(relogio);
        assertThat(l.consumir("a", 2).permitido()).isTrue();
        LimitadorRequisicoes.Resultado segunda = l.consumir("a", 2);
        assertThat(segunda.permitido()).isTrue();
        assertThat(segunda.restante()).isZero();
        assertThat(l.excedido("a", 2)).isTrue();
        LimitadorRequisicoes.Resultado terceira = l.consumir("a", 2);
        assertThat(terceira.permitido()).isFalse();
        assertThat(terceira.segundosParaNova()).isEqualTo(50);
        assertThat(l.consumir("b", 2).permitido()).isTrue(); // outra chave, outra conta
        agora.set(Instant.parse("2026-10-09T12:01:00Z"));
        assertThat(l.excedido("a", 2)).isFalse();
        assertThat(l.consumir("a", 2).permitido()).isTrue(); // janela nova
    }

    @Test
    void todoStatusInternoTemUmStatusPublico() {
        for (StatusAnalise s : StatusAnalise.values()) {
            StatusPublico p = StatusPublico.de(s);
            assertThat(p.finalizada()).as(s.name()).isEqualTo(!s.emAndamento());
        }
        assertThat(StatusPublico.de(StatusAnalise.AGUARDANDO)).isEqualTo(StatusPublico.RECEBIDA);
        assertThat(StatusPublico.de(StatusAnalise.VALIDANDO)).isEqualTo(StatusPublico.EM_PROCESSAMENTO);
        assertThat(StatusPublico.de(StatusAnalise.FALHA)).isEqualTo(StatusPublico.FALHOU);
        assertThat(StatusPublico.de(StatusAnalise.AGUARDANDO_REVISAO)).isEqualTo(StatusPublico.AGUARDANDO_REVISAO);
    }

    @Test
    void normalizacaoDoCorpoParaIdempotencia() {
        SolicitacaoAnalise a = new SolicitacaoAnalise(" REF-1 ", new Mercadoria(" Sabonete ", " descrição longa o bastante ",
                "", null, "  ", "3401.11.90"));
        SolicitacaoAnalise b = new SolicitacaoAnalise("REF-1", new Mercadoria("Sabonete", "descrição longa o bastante",
                null, null, null, "34011190"));
        assertThat(ApiPublicaServiceTestes.normalizar(a)).isEqualTo(ApiPublicaServiceTestes.normalizar(b));
        assertThat(ApiPublicaServiceTestes.normalizar(a).mercadoria().ncmInformada()).isEqualTo("34011190");
    }

    @Test
    void erroInesperadoNaoVazaDetalheTecnico() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/analises/x");
        ProblemDetail pd = new ApiPublicaExceptionHandler()
                .inesperado(new IllegalStateException("senha=123 em jdbc:h2:file:/segredo"), req);
        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getProperties()).containsEntry("codigo", "ERRO_INTERNO");
        assertThat(pd.toString()).doesNotContain("senha").doesNotContain("jdbc").doesNotContain("IllegalState");
    }
}

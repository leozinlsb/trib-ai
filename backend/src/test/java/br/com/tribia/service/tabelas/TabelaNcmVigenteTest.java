package br.com.tribia.service.tabelas;

import br.com.tribia.service.tabelas.TabelaNcmVigente.Situacao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** NCM vigente extraída do Portal Único Siscomex (dados-oficiais/ncm-vigente.csv), sem simular a tabela. */
class TabelaNcmVigenteTest {

    static final TabelaNcmVigente NCM = new TabelaNcmVigente(new ObjectMapper());
    static final LocalDate HOJE = LocalDate.of(2026, 10, 8);

    @Test
    void carregaATabelaOficialComVersao() {
        assertThat(NCM.tamanho()).isGreaterThan(15_000);
        assertThat(NCM.versao().ato()).contains("Gecex");
        assertThat(NCM.versao().url()).startsWith("https://portalunico.siscomex.gov.br/");
        assertThat(NCM.versao().extraidoEm()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void codigoVigenteTrazInicioAtoETextoOficialPelaHierarquia() {
        var c = NCM.consultar("3401.11.90", HOJE);
        assertThat(c.situacao()).isEqualTo(Situacao.VIGENTE);
        assertThat(c.codigo().inicio()).isEqualTo(LocalDate.of(2022, 4, 1));
        assertThat(c.codigo().fim()).isNull();
        assertThat(c.codigo().ato()).isEqualTo("Res Gecex nº 272/2021");
        // o subitem sozinho é só "Outros": o texto completo vem dos níveis acima
        assertThat(c.codigo().descricao()).isEqualTo("Outros");
        assertThat(c.descricaoCompleta()).startsWith("Sabões;").contains("De toucador").endsWith("Outros");
        assertThat(c.descricaoCompleta()).doesNotContain("<i>");
    }

    @Test
    void codigoQueEntrouEmVigorDepoisDaDataNaoEstaVigenteNaquelaData() {
        // quitosana: subitem criado pela Res. Gecex 926/2026, a partir de 01/10/2026
        assertThat(NCM.consultar("39139050", LocalDate.of(2026, 9, 30)).situacao()).isEqualTo(Situacao.NAO_VIGENTE_NA_DATA);
        assertThat(NCM.consultar("39139050", LocalDate.of(2026, 10, 1)).situacao()).isEqualTo(Situacao.VIGENTE);
        assertThat(NCM.consultar("39139050", HOJE).codigo().ato()).contains("926/2026");
    }

    @Test
    void codigoExtintoOuInexistenteNaoConsta() {
        // 3402.20.00 foi extinto na NCM 2022 (detergente da nota do hackathon, ver PENDENCIAS O2)
        assertThat(NCM.consultar("34022000", HOJE).situacao()).isEqualTo(Situacao.NAO_CONSTA);
        assertThat(NCM.consultar("99999999", HOJE).situacao()).isEqualTo(Situacao.NAO_CONSTA);
        assertThat(NCM.consultar("34022000", HOJE).codigo()).isNull();
    }

    @Test
    void formatoInvalidoNaoConsultaATabela() {
        assertThat(NCM.consultar("3401", HOJE).situacao()).isEqualTo(Situacao.FORMATO_INVALIDO);
        assertThat(NCM.consultar(null, HOJE).situacao()).isEqualTo(Situacao.FORMATO_INVALIDO);
        assertThat(NCM.consultar("340111900", HOJE).situacao()).isEqualTo(Situacao.FORMATO_INVALIDO);
    }

    @Test
    void aspasEPontoEVirgulaDentroDoTexto() {
        assertThat(TabelaNcmVigente.campos("01;\"a; b \"\"c\"\"\";2022-04-01;;\"\""))
                .containsExactly("01", "a; b \"c\"", "2022-04-01", "", "");
    }
}

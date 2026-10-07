package br.com.tribia.service.apuracao;

import br.com.tribia.Fixtures;
import br.com.tribia.model.IbsCbsDestacado;
import br.com.tribia.service.ParserNfeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificacaoXmlTest {

    private final ParserNfeService parser = new ParserNfeService();

    @Test
    void aproveitaCstECClassTribDoGrupoDestacado() {
        IbsCbsDestacado g = parser.ler(Fixtures.bytes(Fixtures.NFE_ENTRADA_IBSCBS)).itens().get(0).ibsCbs();

        assertThat(ClassificacaoXml.de(g)).contains(new ClassificacaoXml("000", "000001"));
    }

    @Test
    void semGrupoOuSemCodigosNaoHaClassificacao() {
        IbsCbsDestacado semGrupo = parser.ler(Fixtures.bytes(Fixtures.NFE_SAIDA_HACKATHON)).itens().get(0).ibsCbs();
        IbsCbsDestacado semCClassTrib = new IbsCbsDestacado("000", " ", null, null, null, null, null, null, null, null);

        assertThat(ClassificacaoXml.de(semGrupo)).isEmpty();
        assertThat(ClassificacaoXml.de(semCClassTrib)).isEmpty();
    }
}

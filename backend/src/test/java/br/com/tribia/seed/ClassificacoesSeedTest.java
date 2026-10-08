package br.com.tribia.seed;

import br.com.tribia.service.tabelas.TabelaCClassTrib;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificacoesSeedTest {

    @Test
    void todaClassificacaoDoSeedExisteNaTabelaOficialComOCstCerto() {
        TabelaCClassTrib tabela = new TabelaCClassTrib();
        ClassificacoesSeed.POR_NCM.forEach((ncm, r) -> assertThat(tabela.validoParaNfe(r.cst(), r.cClassTrib()))
                .as("NCM %s -> %s/%s", ncm, r.cst(), r.cClassTrib()).isTrue());
    }

    @Test
    void cobreTodosOsProdutosDoSeedComConfiancaEntre0e1() {
        var entradas = ClassificacoesSeed.entradasDoSeed(); // falha se algum NCM do catálogo não tiver regra
        assertThat(entradas).isNotEmpty();
        entradas.forEach(e -> assertThat(e.confianca()).isBetween(BigDecimal.ZERO, BigDecimal.ONE));
    }

    @Test
    void itensAmbiguosFicamAbaixoDe07ParaCairNaRevisao() {
        assertThat(ClassificacoesSeed.POR_NCM.get("30049069").confianca()).isLessThan(new BigDecimal("0.7"));
        assertThat(ClassificacoesSeed.POR_NCM.get("10063021").confianca()).isGreaterThanOrEqualTo(new BigDecimal("0.7"));
    }
}

package br.com.tribia.service.classificacao;

import br.com.tribia.model.Classificacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Quando um item precisa de revisão (adendo: "confiança baixa ou não aceitos"). Usado no contador do painel e na
 * aba de revisão, para os dois mostrarem sempre o mesmo número.
 */
@Component
public class CriterioRevisao {

    private final BigDecimal confiancaMinima;

    public CriterioRevisao(@Value("${tribia.revisao.confianca-minima:0.70}") BigDecimal confiancaMinima) {
        this.confiancaMinima = confiancaMinima;
    }

    /** Sem classificação, não aceita ou com confiança abaixo do mínimo. */
    public boolean precisaRevisao(Classificacao c) {
        return c == null || !c.isAceita() || c.getConfianca().compareTo(confiancaMinima) < 0;
    }

    public BigDecimal confiancaMinima() {
        return confiancaMinima;
    }
}

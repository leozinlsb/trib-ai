package br.com.tribia.service.classificacao;

import br.com.tribia.model.Classificacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Quando um item precisa de revisão (adendo: "confiança baixa ou não aceitos"). Usado no contador do painel e na
 * aba de revisão, para os dois mostrarem sempre o mesmo número. Item já conferido por uma pessoa sai da fila.
 */
@Component
public class CriterioRevisao {

    public enum Motivo {
        SEM_CLASSIFICACAO,
        NAO_ACEITA,
        CONFIANCA_BAIXA
    }

    private final BigDecimal confiancaMinima;

    public CriterioRevisao(@Value("${tribia.revisao.confianca-minima:0.70}") BigDecimal confiancaMinima) {
        this.confiancaMinima = confiancaMinima;
    }

    public boolean precisaRevisao(Classificacao c) {
        return !motivos(c).isEmpty();
    }

    /** Vazio quando o item está ok (ou já foi revisado por uma pessoa). */
    public List<Motivo> motivos(Classificacao c) {
        List<Motivo> m = new ArrayList<>();
        if (c == null) {
            m.add(Motivo.SEM_CLASSIFICACAO);
            return m;
        }
        if (c.isRevisada()) {
            return m;
        }
        if (!c.isAceita()) {
            m.add(Motivo.NAO_ACEITA);
        }
        if (c.getConfianca().compareTo(confiancaMinima) < 0) {
            m.add(Motivo.CONFIANCA_BAIXA);
        }
        return m;
    }

    public BigDecimal confiancaMinima() {
        return confiancaMinima;
    }
}

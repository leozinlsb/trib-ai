package br.com.tribia.service.classificacao;

import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaNcmAplicavel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Último recurso quando a IA não responde e não há resposta gravada: sugere pela regra oficial mais específica do
 * NCM (ex.: arroz 1006.30 → 200003) ou, sem regra, tributação integral. A confiança é baixa de propósito: o item
 * sempre vai para a revisão. Melhor um palpite sinalizado do que um item fora do cálculo.
 */
@Component
public class ClassificadorPorRegra {

    static final BigDecimal CONFIANCA = new BigDecimal("0.40");

    public record Sugestao(String cst, String cClassTrib, String justificativa, BigDecimal confianca) {
    }

    private final TabelaNcmAplicavel regrasNcm;
    private final TabelaCClassTrib tabela;
    private final OpcoesClassificacao opcoes;

    public ClassificadorPorRegra(TabelaNcmAplicavel regrasNcm, TabelaCClassTrib tabela, OpcoesClassificacao opcoes) {
        this.regrasNcm = regrasNcm;
        this.tabela = tabela;
        this.opcoes = opcoes;
    }

    public Sugestao sugerir(String ncm) {
        return regrasNcm.regrasPara(ncm).stream()
                .filter(r -> opcoes.permitida(r.cClassTrib()))
                .findFirst()
                .map(r -> {
                    var c = tabela.buscar(r.cClassTrib()).orElseThrow();
                    return new Sugestao(c.cst(), c.codigo(), "Sugestão automática (IA indisponível): a regra oficial do NCM "
                            + ncm + " aponta " + r.descricao() + " – " + c.descricaoRegime()
                            + ". Confirme na revisão.", CONFIANCA);
                })
                .orElseGet(() -> new Sugestao("000", "000001", "Sugestão automática (IA indisponível): o NCM "
                        + (ncm == null ? "(vazio)" : ncm) + " não tem regra oficial de redução; tributação integral. "
                        + "Confirme na revisão (medicamentos, por exemplo, não têm regra por NCM).", CONFIANCA));
    }
}

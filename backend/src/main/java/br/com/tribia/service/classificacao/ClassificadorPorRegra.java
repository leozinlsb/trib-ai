package br.com.tribia.service.classificacao;

import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaNcmAplicavel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Último recurso: somente uma associação inequívoca na tabela pode virar sugestão não aceita.
 * Ausência, ambiguidade ou dependência do adquirente não autorizam inferir tributação integral.
 * NCM é uma pista, não comprovação do enquadramento da operação; revisão humana continua obrigatória.
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

    public Optional<Sugestao> sugerir(String ncm) {
        if (ncm == null || !ncm.matches("\\d{8}")) return Optional.empty();
        var candidatas = regrasNcm.regrasPara(ncm).stream()
                .filter(r -> opcoes.permitida(r.cClassTrib()))
                .toList();
        if (candidatas.stream().map(TabelaNcmAplicavel.Regra::cClassTrib).distinct().count() != 1) {
            return Optional.empty();
        }
        return candidatas.stream().findFirst()
                .map(r -> {
                    var c = tabela.buscar(r.cClassTrib()).orElseThrow();
                    return new Sugestao(c.cst(), c.codigo(), "Sugestão automática (IA indisponível): a regra oficial do NCM "
                            + ncm + " aponta " + r.descricao() + " – " + c.descricaoRegime()
                            + ". Confirme na revisão.", CONFIANCA);
                });
    }
}

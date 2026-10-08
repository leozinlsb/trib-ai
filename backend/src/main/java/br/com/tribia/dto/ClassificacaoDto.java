package br.com.tribia.dto;

import br.com.tribia.model.Classificacao;
import br.com.tribia.model.OrigemClassificacao;
import br.com.tribia.model.RegimeTributario;
import br.com.tribia.service.tabelas.TabelaCClassTrib;

import java.math.BigDecimal;

/**
 * @param nomeCClassTrib  nome oficial do cClassTrib (tabela da Receita)
 * @param descricaoRegime ex.: "Redução de 60%"
 */
public record ClassificacaoDto(
        String cst,
        String cClassTrib,
        String nomeCClassTrib,
        RegimeTributario regime,
        String descricaoRegime,
        String justificativa,
        BigDecimal confianca,
        OrigemClassificacao origem,
        boolean aceita,
        boolean revisada
) {
    public static ClassificacaoDto de(Classificacao c, TabelaCClassTrib tabela) {
        if (c == null) {
            return null;
        }
        var oficial = tabela.buscar(c.getCClassTrib());
        return new ClassificacaoDto(c.getCst(), c.getCClassTrib(),
                oficial.map(TabelaCClassTrib.CClassTrib::nome).orElse(null), c.getRegime(),
                oficial.map(TabelaCClassTrib.CClassTrib::descricaoRegime).orElse(null),
                c.getJustificativa(), c.getConfianca(), c.getOrigem(), c.isAceita(), c.isRevisada());
    }
}

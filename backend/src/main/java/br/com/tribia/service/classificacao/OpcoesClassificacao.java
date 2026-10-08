package br.com.tribia.service.classificacao;

import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaCClassTrib.CClassTrib;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Opções que a classificação AUTOMÁTICA (IA ou regra) pode usar: as opções de NF-e da tabela oficial, menos os
 * códigos que dependem de quem compra (produtor rural, administração pública, Zona Franca...), configurados em
 * tribia.classificacao.codigos-por-adquirente. Esses só a revisão manual aplica, porque a nota sozinha não diz
 * quem é o adquirente (ex.: 200038, insumo agropecuário, valeria para o azeite só se o comprador fosse produtor).
 */
@Component
public class OpcoesClassificacao {

    private final List<CClassTrib> opcoes;
    private final Set<String> codigos;

    public OpcoesClassificacao(TabelaCClassTrib tabela,
                               @Value("${tribia.classificacao.codigos-por-adquirente:}") String[] porAdquirente) {
        Set<String> excluidos = Arrays.stream(porAdquirente).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.opcoes = tabela.opcoesNfe().stream().filter(c -> !excluidos.contains(c.codigo())).toList();
        this.codigos = opcoes.stream().map(CClassTrib::codigo).collect(Collectors.toUnmodifiableSet());
    }

    public List<CClassTrib> todas() {
        return opcoes;
    }

    public boolean permitida(String codigo) {
        return codigos.contains(codigo);
    }
}

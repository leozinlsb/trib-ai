package br.com.tribia.service.fiscal;

import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Pontuacao;

import java.util.List;
import java.util.Map;

/**
 * Enquanto a JEV AI não for integrada: nenhuma pontuação (as alternativas aparecem sem ela). Não é bean: o
 * processador usa esta classe só quando não existe nenhum {@link AvaliadorJev} registrado.
 */
public class JevIndisponivel implements AvaliadorJev {

    @Override
    public boolean disponivel() {
        return false;
    }

    @Override
    public Map<String, Pontuacao> avaliar(MercadoriaParaJev mercadoria, List<Candidata> candidatas) {
        return Map.of();
    }
}

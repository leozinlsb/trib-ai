package br.com.tribia.apipublica.servico;

import br.com.tribia.apipublica.dto.ApiPublicaDtos.SolicitacaoAnalise;

/** Acesso dos testes a métodos de pacote do ApiPublicaService. */
public final class ApiPublicaServiceTestes {

    private ApiPublicaServiceTestes() {
    }

    public static SolicitacaoAnalise normalizar(SolicitacaoAnalise s) {
        return ApiPublicaService.normalizar(s);
    }
}

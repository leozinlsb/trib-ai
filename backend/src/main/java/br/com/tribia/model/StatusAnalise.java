package br.com.tribia.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Situação de uma análise fiscal de mercadoria (Inteligência Fiscal). O fluxo normal segue a ordem declarada até
 * CONCLUIDA; FALHA, INFORMACOES_INSUFICIENTES e AGUARDANDO_REVISAO encerram o processamento em outra situação.
 */
public enum StatusAnalise {
    AGUARDANDO,
    INTERPRETANDO,
    PESQUISANDO_NCM,
    AVALIANDO,
    VALIDANDO,
    GERANDO_RELATORIO,
    CONCLUIDA,
    FALHA,
    INFORMACOES_INSUFICIENTES,
    AGUARDANDO_REVISAO;

    public static final Set<StatusAnalise> EM_ANDAMENTO =
            EnumSet.of(AGUARDANDO, INTERPRETANDO, PESQUISANDO_NCM, AVALIANDO, VALIDANDO, GERANDO_RELATORIO);

    public boolean emAndamento() {
        return EM_ANDAMENTO.contains(this);
    }
}

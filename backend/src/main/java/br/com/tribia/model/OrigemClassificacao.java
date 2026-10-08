package br.com.tribia.model;

/** De onde veio o CST/cClassTrib de um item. */
public enum OrigemClassificacao {
    /** Grupo IBS/CBS já destacado no XML pelo emitente (só CST e cClassTrib são aproveitados). */
    XML,
    /** Cache global por NCM + descrição: item já classificado antes, para qualquer cliente. */
    CACHE,
    /** Sugestão da IA. */
    IA,
    /**
     * Sugestão automática pela regra oficial do NCM, quando a IA não está disponível: confiança baixa, sempre vai
     * para a revisão.
     */
    REGRA,
    /** Informada ou corrigida pelo usuário na revisão. */
    MANUAL
}

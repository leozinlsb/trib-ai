package br.com.tribia.model;

/** Papel do item na apuração: a saída gera débito; a entrada, crédito. */
public enum Natureza {
    DEBITO,
    CREDITO;

    public static Natureza de(TipoNota tipo) {
        return tipo == TipoNota.SAIDA ? DEBITO : CREDITO;
    }
}

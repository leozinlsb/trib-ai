package br.com.tribia.model;

/** Tipo da nota do ponto de vista do cliente. */
public enum TipoNota {
    /** Compra: o cliente é o destinatário. Itens geram crédito. */
    ENTRADA,
    /** Venda: o cliente é o emitente. Itens geram débito. */
    SAIDA
}

package br.com.tribia.model;

/**
 * O que a nota representa para o cliente. Define o lado da apuração (débito ou crédito) e o sinal:
 * devolução de venda estorna débito; devolução de compra estorna crédito.
 */
public enum Operacao {
    /** Cliente vende. Débito. */
    VENDA(TipoNota.SAIDA, Natureza.DEBITO, 1),
    /** Cliente compra (inclusive por nota de entrada própria, tpNF=0: importação, produtor rural). Crédito. */
    COMPRA(TipoNota.ENTRADA, Natureza.CREDITO, 1),
    /** Mercadoria vendida volta para o cliente: estorna o débito da venda. */
    DEVOLUCAO_DE_VENDA(TipoNota.ENTRADA, Natureza.DEBITO, -1),
    /** Cliente devolve ao fornecedor: estorna o crédito da compra. */
    DEVOLUCAO_DE_COMPRA(TipoNota.SAIDA, Natureza.CREDITO, -1);

    private final TipoNota tipo;
    private final Natureza natureza;
    private final int sinal;

    Operacao(TipoNota tipo, Natureza natureza, int sinal) {
        this.tipo = tipo;
        this.natureza = natureza;
        this.sinal = sinal;
    }

    /** Sentido da mercadoria em relação ao cliente (SAIDA ou ENTRADA). */
    public TipoNota tipo() {
        return tipo;
    }

    public Natureza natureza() {
        return natureza;
    }

    /** 1 ou -1 (estorno). */
    public int sinal() {
        return sinal;
    }

    public boolean devolucao() {
        return sinal < 0;
    }

    /**
     * @param emitidaPeloCliente o cliente é o emitente (senão, é o destinatário)
     * @param tpNF               0 entrada, 1 saída, do ponto de vista do emitente
     * @param devolucao          finNFe = 4
     */
    public static Operacao de(boolean emitidaPeloCliente, int tpNF, boolean devolucao) {
        // A mercadoria sai do cliente quando ele emite uma nota de saída ou quando um terceiro emite uma nota de
        // entrada (tpNF=0) recebendo dele; nos outros casos, ela entra no cliente.
        boolean saiDoCliente = emitidaPeloCliente == (tpNF == 1);
        if (saiDoCliente) {
            return devolucao ? DEVOLUCAO_DE_COMPRA : VENDA;
        }
        return devolucao ? DEVOLUCAO_DE_VENDA : COMPRA;
    }
}

package br.com.tribia.model;

/**
 * Regime de apuração do cliente. Define as regras de PIS/Cofins "hoje".
 * Simples Nacional fica fora do MVP (recolhe pelo DAS e tem regras próprias de crédito).
 */
public enum Regime {
    /** Não cumulativo: 1,65% + 7,6%, com crédito nas entradas. */
    LUCRO_REAL,
    /** Cumulativo: 0,65% + 3%, sem crédito nas entradas. */
    LUCRO_PRESUMIDO
}

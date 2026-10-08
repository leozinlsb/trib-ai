/** localStorage com tolerância a falhas (aba anônima, armazenamento bloqueado). */
export function lerLocal<T>(chave: string, padrao: T): T {
  try {
    const bruto = localStorage.getItem(chave)
    return bruto == null ? padrao : (JSON.parse(bruto) as T)
  } catch {
    return padrao
  }
}

export function gravarLocal(chave: string, valor: unknown) {
  try {
    localStorage.setItem(chave, JSON.stringify(valor))
  } catch {
    // sem armazenamento local: a preferência vale só nesta sessão
  }
}

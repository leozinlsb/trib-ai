/** Mesmo algoritmo do backend (CnpjUtil): dígitos verificadores módulo 11. */
export function cnpjValido(cnpj: string) {
  const d = cnpj.replace(/\D/g, '')
  if (d.length !== 14 || /^(\d)\1+$/.test(d)) return false
  const dv = (base: string, pesos: number[]) => {
    const r = pesos.reduce((s, p, i) => s + Number(base[i]) * p, 0) % 11
    return r < 2 ? 0 : 11 - r
  }
  const d1 = dv(d.slice(0, 12), [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2])
  const d2 = dv(d.slice(0, 12) + d1, [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2])
  return d.endsWith(`${d1}${d2}`)
}

/** Máscara progressiva 00.000.000/0000-00 enquanto digita. */
export function mascararCnpj(v: string) {
  const d = v.replace(/\D/g, '').slice(0, 14)
  return d
    .replace(/^(\d{2})(\d)/, '$1.$2')
    .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1/$2')
    .replace(/(\d{4})(\d)/, '$1-$2')
}

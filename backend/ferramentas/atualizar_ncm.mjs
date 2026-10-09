// Baixa a Nomenclatura Comum do Mercosul (NCM) vigente do Portal Único Siscomex (API pública, sem autenticação)
// e grava src/main/resources/dados-oficiais/ncm-vigente.csv + ncm-vigente.json (metadados da versão).
//
//   node ferramentas/atualizar_ncm.mjs            (na pasta backend; Node 18+)
//
// Fonte: https://portalunico.siscomex.gov.br/classif/api/publico/nomenclatura/download/json
// A tabela traz só os códigos VIGENTES na data da extração, com a data de início e o ato que os criou.
// Códigos extintos não aparecem: ausência = "não consta da NCM vigente", não prova de que nunca existiu.
// NCM não é TIPI: a TIPI (Decreto 11.158/2022) dá a alíquota do IPI para os códigos da NCM.

import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const URL_FONTE = 'https://portalunico.siscomex.gov.br/classif/api/publico/nomenclatura/download/json?perfil=PUBLICO'
const pasta = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'main', 'resources', 'dados-oficiais')

const resposta = await fetch(URL_FONTE, { signal: AbortSignal.timeout(120_000) })
if (!resposta.ok) throw new Error(`Siscomex respondeu HTTP ${resposta.status}`)
const dados = await resposta.json()
const itens = dados.Nomenclaturas
if (!Array.isArray(itens) || itens.length < 10_000) throw new Error('Resposta inesperada: lista de nomenclaturas ausente ou curta')

const iso = (d) => {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(d ?? '')
  if (!m) throw new Error(`Data inválida: ${d}`)
  return m[3] === '9999' ? '' : `${m[3]}-${m[2]}-${m[1]}`
}
// texto entre aspas (a descrição oficial contém ";"); marcações HTML de itálico (<i>ouates</i>) removidas
const campo = (s) => `"${String(s ?? '').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim().replace(/"/g, '""')}"`

const linhas = ['codigo;descricao;data_inicio;data_fim;ato']
let oitoDigitos = 0
for (const n of itens) {
  const codigo = String(n.Codigo).replace(/\D/g, '')
  if (!codigo) continue
  if (codigo.length === 8) oitoDigitos++
  const ato = [n.Tipo_Ato_Ini, n.Numero_Ato_Ini && `nº ${n.Numero_Ato_Ini}`, n.Ano_Ato_Ini && `/${n.Ano_Ato_Ini}`]
    .filter(Boolean).join(' ').replace(' /', '/')
  linhas.push([codigo, campo(n.Descricao), iso(n.Data_Inicio), iso(n.Data_Fim), campo(ato)].join(';'))
}

mkdirSync(pasta, { recursive: true })
writeFileSync(join(pasta, 'ncm-vigente.csv'), linhas.join('\n') + '\n', 'utf8')
const meta = {
  fonte: 'Portal Único Siscomex — Nomenclatura Comum do Mercosul (API pública de classificação)',
  url: URL_FONTE,
  situacao: dados.Data_Ultima_Atualizacao_NCM,
  ato: dados.Ato,
  extraidoEm: new Date().toISOString().slice(0, 10),
  registros: linhas.length - 1,
  codigos8Digitos: oitoDigitos,
}
writeFileSync(join(pasta, 'ncm-vigente.json'), JSON.stringify(meta, null, 2) + '\n', 'utf8')
console.log(`ncm-vigente.csv: ${meta.registros} registros (${oitoDigitos} com 8 dígitos) — ${meta.situacao}, ${meta.ato}`)

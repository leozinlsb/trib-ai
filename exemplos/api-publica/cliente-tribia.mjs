#!/usr/bin/env node
// Cliente de exemplo da API pública v1 do TribIA: o que um ERP faria para pedir a análise fiscal de um produto.
// Node 18+ (fetch nativo), sem dependências.
//
// Uso:
//   TRIBIA_API_KEY=tribia_... node exemplos/api-publica/cliente-tribia.mjs [produto.json]
// Variáveis:
//   TRIBIA_API_KEY            obrigatória (nunca coloque a chave no código nem no Git)
//   TRIBIA_API_URL            padrão http://localhost:8090
//   TRIBIA_TIMEOUT_SEGUNDOS   quanto esperar a análise terminar (padrão 120)
//   TRIBIA_IDEMPOTENCY_KEY    para reenviar o MESMO pedido sem duplicar (padrão: um UUID novo por execução)
//   TRIBIA_SAIDA_JSON=1       imprime o JSON completo do resultado
//
// Saída: código 0 = análise com resultado (CONCLUIDA ou AGUARDANDO_REVISAO); 2 = terminou sem resultado
// (FALHOU / INFORMACOES_INSUFICIENTES); 1 = erro de requisição, autenticação ou tempo esgotado.

import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'

const URL_API = (process.env.TRIBIA_API_URL ?? 'http://localhost:8090').replace(/\/+$/, '')
const CHAVE = process.env.TRIBIA_API_KEY
const TIMEOUT_MS = Number(process.env.TRIBIA_TIMEOUT_SEGUNDOS ?? 120) * 1000
const IDEMPOTENCY_KEY = process.env.TRIBIA_IDEMPOTENCY_KEY ?? randomUUID()

// Produto fictício (nenhum dado real de empresa).
const PRODUTO_PADRAO = {
  referenciaExterna: 'DEMO-SKU-0001',
  mercadoria: {
    nome: 'Sabonete de glicerina 90 g',
    descricao: 'Sabonete em barra de glicerina para higiene pessoal, embalado individualmente em papel.',
    composicao: 'glicerina vegetal, óleo de coco, essência',
    finalidade: 'higiene pessoal',
    caracteristicas: 'barra de 90 g, embalagem individual',
    ncmInformada: '3401.11.90',
  },
}

class ErroApi extends Error {
  constructor(status, corpo, requestId) {
    const p = corpo && typeof corpo === 'object' ? corpo : {}
    super(`${status} ${p.codigo ?? ''} ${p.detail ?? p.title ?? (typeof corpo === 'string' ? corpo : '')}`.trim())
    this.status = status
    this.codigo = p.codigo
    this.requestId = p.requestId ?? requestId
    this.campos = p.campos
  }
}

async function chamar(metodo, caminho, corpo, cabecalhos = {}) {
  const r = await fetch(URL_API + caminho, {
    method: metodo,
    headers: {
      'X-API-Key': CHAVE,
      Accept: 'application/json',
      ...(corpo ? { 'Content-Type': 'application/json' } : {}),
      ...cabecalhos,
    },
    body: corpo ? JSON.stringify(corpo) : undefined,
  })
  const texto = await r.text()
  let dados = null
  try {
    dados = texto ? JSON.parse(texto) : null
  } catch {
    dados = texto
  }
  if (!r.ok) {
    const erro = new ErroApi(r.status, dados, r.headers.get('x-request-id'))
    erro.retryAfter = Number(r.headers.get('retry-after') ?? 0)
    throw erro
  }
  return { status: r.status, dados, cabecalhos: r.headers }
}

const espera = (ms) => new Promise((ok) => setTimeout(ok, ms))

async function main() {
  if (!CHAVE) {
    console.error('Defina TRIBIA_API_KEY com a chave emitida pelo administrador do TribIA.')
    process.exit(1)
  }
  const arquivo = process.argv[2]
  const produto = arquivo ? JSON.parse(await readFile(arquivo, 'utf8')) : PRODUTO_PADRAO

  console.log(`API: ${URL_API}`)
  console.log(`Chave: ${CHAVE.slice(0, 19)}… (só o prefixo é exibido)`)
  const uso = await chamar('GET', '/api/v1/uso')
  console.log(`Empresa da chave: ${uso.dados.empresa.razaoSocial} — análises hoje: ` +
    `${uso.dados.consumo.analisesCriadasHoje}/${uso.dados.limites.cotaDiariaAnalises}`)

  // 1. Envia a mercadoria (202 + id). A Idempotency-Key protege contra duplicidade se for preciso reenviar.
  let criada
  for (let tentativa = 1; ; tentativa++) {
    try {
      criada = await chamar('POST', '/api/v1/analises', produto, { 'Idempotency-Key': IDEMPOTENCY_KEY })
      break
    } catch (e) {
      // 429/503 com Retry-After: espera e reenvia com a MESMA Idempotency-Key (não duplica a análise)
      if ((e.status === 429 || e.status === 503) && tentativa < 3 && e.codigo !== 'COTA_DIARIA_EXCEDIDA') {
        const s = Math.min(e.retryAfter || 5, 30)
        console.warn(`${e.message} — nova tentativa em ${s}s`)
        await espera(s * 1000)
        continue
      }
      throw e
    }
  }
  const id = criada.dados.id
  const repetida = criada.cabecalhos.get('idempotent-replayed') === 'true'
  console.log(`Análise ${id} ${repetida ? '(repetição idempotente: mesma análise de antes)' : 'criada'} — ` +
    `status ${criada.dados.status} (Idempotency-Key ${IDEMPOTENCY_KEY})`)

  // 2. Consulta até a análise terminar (finalizada = true), com intervalo crescente e tempo máximo.
  const limite = Date.now() + TIMEOUT_MS
  let analise = criada.dados
  let intervalo = 1000
  let ultimoStatus = ''
  while (!analise.finalizada) {
    if (Date.now() > limite) {
      console.error(`Tempo esgotado (${TIMEOUT_MS / 1000}s). A análise continua no servidor: consulte depois ` +
        `GET /api/v1/analises/${id}`)
      process.exit(1)
    }
    await espera(intervalo)
    intervalo = Math.min(intervalo * 1.5, 5000)
    try {
      analise = (await chamar('GET', `/api/v1/analises/${id}`)).dados
    } catch (e) {
      if (e.status === 429) {
        await espera((e.retryAfter || 5) * 1000)
        continue
      }
      throw e
    }
    const s = `${analise.status} / ${analise.etapa}`
    if (s !== ultimoStatus) {
      console.log(`  … ${s}`)
      ultimoStatus = s
    }
  }

  // 3. Mostra o resultado.
  console.log('')
  console.log(`Status final: ${analise.status}`)
  if (analise.resultado) {
    const r = analise.resultado
    console.log(`NCM sugerida: ${r.ncmSugeridaFormatada} — ${r.descricaoOficial}`)
    console.log(`Natureza: ${r.natureza} | validação: ${r.situacaoValidacao}`)
    console.log(`Revisão humana: ${analise.revisaoHumana.situacao}` +
      (analise.revisaoHumana.ncmDecidida ? ` (NCM decidida ${analise.revisaoHumana.ncmDecidida})` : ''))
    for (const a of r.alternativas ?? []) {
      const p = a.pontuacaoCompatibilidade
      console.log(`  alternativa ${a.ncmFormatada}: ${a.descricao}` + (p ? ` [JEV ${p.valor} ${p.escala}]` : ''))
    }
    if (analise.mensagem) console.log(`Pontos a conferir: ${analise.mensagem}`)
  } else if (analise.erro) {
    console.log(`Sem resultado (${analise.erro.codigo}): ${analise.erro.mensagem}`)
  }
  for (const aviso of analise.avisos ?? []) console.log(`Aviso: ${aviso}`)
  if (process.env.TRIBIA_SAIDA_JSON === '1') console.log(JSON.stringify(analise, null, 2))
  process.exit(analise.resultado ? 0 : 2)
}

main().catch((e) => {
  if (e instanceof ErroApi) {
    console.error(`Erro da API: ${e.message}` + (e.requestId ? ` (requestId ${e.requestId})` : ''))
    if (e.campos) console.error(e.campos)
  } else {
    console.error(`Falha: ${e.message}` + (e.cause?.code === 'ECONNREFUSED' ? ' — o backend está no ar?' : ''))
  }
  process.exit(1)
})

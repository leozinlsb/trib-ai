/**
 * Dublês LOCAIS das APIs externas, só para o E2E (nunca para demonstração ou produção): Gemini (generateContent) e
 * JEV/TypeSafe (/v1/models, /v1/systemone), nos formatos documentados. Permitem exercitar o código real do backend
 * (GeminiClient, JevHttp) de ponta a ponta sem gastar créditos. As respostas são fixas e servem só para testar o fluxo.
 *
 *   node scripts/stubs-ia-e2e.mjs            (porta 18995, só em 127.0.0.1)
 *
 * Gemini: pedidos da Inteligência Fiscal (texto com <dados_...>) recebem candidatas de sabonete; outros pedidos
 * (classificação de notas) recebem 503, e o backend segue o plano B dele. Mercadoria com "divergente" no nome faz a
 * JEV preferir a alternativa, para testar a divergência.
 */
import { createServer } from 'node:http'

const PORTA = Number(process.env.STUB_PORTA ?? 18995)
const CHAVE_JEV = 'chave-falsa-jev-e2e'

const ncm = (suficiente = true) => ({
  suficiente,
  faltando: [],
  caracteristicas: ['sabão em barra', 'uso para higiene pessoal'],
  candidatas: [
    { ncm: '34011190', descricao: 'Sabões de toucador em barras - outros', motivos: ['Barra de sabão para higiene pessoal (RGI 1, posição 34.01)'], avaliacao: 'Descrição compatível com sabão de toucador.', confianca: 0.9 },
    { ncm: '34011900', descricao: 'Outros sabões em barras', motivos: ['Sabão em barra não de toucador'], avaliacao: 'Menos provável.', confianca: 0.55 },
  ],
  regrasConsideradas: ['RGI 1', 'RGI 6'],
  observacoes: ['Resposta do dublê local do E2E (não é a IA real).'],
})

function json(res, status, corpo) {
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify(corpo))
}

createServer(async (req, res) => {
  let bruto = ''
  for await (const parte of req) bruto += parte
  const corpo = bruto ? JSON.parse(bruto) : {}

  if (req.method === 'POST' && /^\/v1beta\/models\/[^/]+:generateContent$/.test(req.url)) {
    const texto = JSON.stringify(corpo.contents ?? '')
    if (!texto.includes('<dados_')) return json(res, 503, { error: { message: 'dublê: só Inteligência Fiscal' } })
    return json(res, 200, { candidates: [{ content: { role: 'model', parts: [{ text: JSON.stringify(ncm()) }] }, finishReason: 'STOP' }] })
  }

  const auth = req.headers.authorization
  if (req.url.startsWith('/v1/') && auth !== `Bearer ${CHAVE_JEV}`) return json(res, 401, { error: 'invalid api key' })
  if (req.method === 'GET' && req.url === '/v1/models') {
    return json(res, 200, { models: [{ name: 'jev-1.13.0', description: 'dublê', release_date: '2026-09-15' }] })
  }
  if (req.method === 'POST' && req.url === '/v1/systemone') {
    const divergente = String(corpo.state?.mercadoria ?? '').toLowerCase().includes('divergente')
    const answers = {}
    for (const chave of Object.keys(corpo.questions ?? {})) {
      const n = chave.replace('ncm_', '')
      const nota = divergente ? (n === '34011900' ? 0.9 : 0.25) : n === '34011190' ? 0.93 : 0.12
      answers[chave] = { type: 'noul', noul: nota }
    }
    return json(res, 200, { model: 'jev-1.13.0', answers, usage: { input_tokens: 320, output_tokens: 10 } })
  }
  json(res, 404, { error: 'rota desconhecida no dublê' })
}).listen(PORTA, '127.0.0.1', () => console.log(`dublês de IA em http://127.0.0.1:${PORTA}`))

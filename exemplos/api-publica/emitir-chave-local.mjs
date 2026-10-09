#!/usr/bin/env node
// Emite uma chave da API pública num TribIA LOCAL, entrando como administrador (sessão + CSRF, como o front faz).
// É a mesma rota que o administrador usaria por qualquer cliente HTTP: POST /api/admin/chaves-api.
//
// Uso:
//   TRIBIA_ADMIN_SENHA=... node exemplos/api-publica/emitir-chave-local.mjs [clienteId] [nomeIntegrador]
// Variáveis: TRIBIA_API_URL (padrão http://localhost:8090), TRIBIA_ADMIN_EMAIL (padrão admin@tribia.local),
//            TRIBIA_ADMIN_SENHA (obrigatória), TRIBIA_ESCOPOS (ex.: ANALISES_LER), TRIBIA_VALIDADE_DIAS.
//
// A chave aparece UMA vez na saída. Copie para uma variável de ambiente; não grave em arquivo versionado.

const URL_API = (process.env.TRIBIA_API_URL ?? 'http://localhost:8090').replace(/\/+$/, '')
const EMAIL = process.env.TRIBIA_ADMIN_EMAIL ?? 'admin@tribia.local'
const SENHA = process.env.TRIBIA_ADMIN_SENHA
const clienteId = Number(process.argv[2] ?? 1)
const nomeIntegrador = process.argv[3] ?? 'ERP de demonstração'

if (!SENHA) {
  console.error('Defina TRIBIA_ADMIN_SENHA (senha do administrador do backend local).')
  process.exit(1)
}

const cookies = new Map()
function guardarCookies(r) {
  for (const c of r.headers.getSetCookie?.() ?? []) {
    const [par] = c.split(';')
    const i = par.indexOf('=')
    cookies.set(par.slice(0, i), par.slice(i + 1))
  }
}
async function chamar(metodo, caminho, corpo) {
  const headers = { Cookie: [...cookies].map(([k, v]) => `${k}=${v}`).join('; ') }
  if (cookies.has('XSRF-TOKEN')) headers['X-XSRF-TOKEN'] = decodeURIComponent(cookies.get('XSRF-TOKEN'))
  if (corpo) headers['Content-Type'] = 'application/json'
  const r = await fetch(URL_API + caminho, { method: metodo, headers, body: corpo ? JSON.stringify(corpo) : undefined })
  guardarCookies(r)
  const texto = await r.text()
  const dados = texto ? JSON.parse(texto) : null
  if (!r.ok) throw new Error(`${metodo} ${caminho}: ${r.status} ${dados?.detail ?? texto}`)
  return dados
}

try {
  await chamar('GET', '/api/auth/csrf')
  await chamar('POST', '/api/auth/login', { email: EMAIL, senha: SENHA })
  await chamar('GET', '/api/auth/csrf') // o token CSRF muda com a sessão nova
  const form = { clienteId, nomeIntegrador }
  if (process.env.TRIBIA_ESCOPOS) form.escopos = process.env.TRIBIA_ESCOPOS.split(',')
  if (process.env.TRIBIA_VALIDADE_DIAS) form.validadeDias = Number(process.env.TRIBIA_VALIDADE_DIAS)
  const r = await chamar('POST', '/api/admin/chaves-api', form)
  console.error(`Chave ${r.dados.prefixo} emitida para "${r.dados.empresa}" (empresa ${r.dados.clienteId}), ` +
    `escopos ${r.dados.escopos.join(', ')}, expira em ${r.dados.expiraEm ?? 'nunca'}.`)
  console.error(r.aviso)
  console.log(r.chave) // só a chave no stdout: dá para capturar numa variável sem gravar em arquivo
} catch (e) {
  console.error(e.message)
  process.exit(1)
}

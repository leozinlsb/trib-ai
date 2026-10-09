/**
 * E2E das integrações novas (indicadores da Inteligência Fiscal no início da empresa, pagamento de compra,
 * recálculo da nota e da empresa, relatório PDF sem resultado, isolamento entre empresas nas rotas novas).
 *
 * Exige backend ISOLADO, sem chave de IA (nenhuma chamada paga) e sem o .env da raiz:
 *   backend: mvnw spring-boot:run -Dspring-boot.run.profiles=demo "-Dspring-boot.run.arguments=--server.port=8190
 *            --tribia.arquivo-local=x --tribia.arquivo-env-raiz=x --tribia.arquivo-env-backend=x --spring.datasource.url=jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1
 *            --tribia.admin.senha=<senha> --tribia.llm.api-key= --tribia.calculo.modo=SIMPLIFICADA"
 *   front:   npm run build; TRIBIA_BACKEND_URL=http://127.0.0.1:8190 npx vite preview --port 15173 --host 127.0.0.1
 *   teste:   TRIBIA_E2E_ISOLADO=1 TRIBIA_E2E_SENHA=<senha> TRIBIA_PLAYWRIGHT_MODULE=<pasta>/node_modules/playwright \
 *            node scripts/etapa5-e2e.mjs
 * Cria uma análise (que termina em FALHA, sem IA) e um usuário sintético; muda o pagamento de uma compra do seed e
 * volta ao estado original. Nunca aponte para dados reais.
 */
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'
import { join, resolve } from 'node:path'

assert.equal(process.env.TRIBIA_E2E_ISOLADO, '1', 'Confirme backend isolado; nunca apontar este teste a dados reais.')
assert.ok(process.env.TRIBIA_E2E_SENHA, 'Informe a senha do administrador em TRIBIA_E2E_SENHA.')
assert.ok(process.env.TRIBIA_PLAYWRIGHT_MODULE, 'Informe TRIBIA_PLAYWRIGHT_MODULE (pasta do módulo playwright).')
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.TRIBIA_PLAYWRIGHT_MODULE)
const raiz = resolve(import.meta.dirname, '../..')
const saida = join(raiz, 'backend/target/etapa5-e2e')
await mkdir(saida, { recursive: true })
const base = process.env.TRIBIA_E2E_BASE ?? 'http://127.0.0.1:15173'
const erros = []
const aprovados = []

const browser = await chromium.launch({
  executablePath: process.env.TRIBIA_CHROME_PATH ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true,
})

async function novaSessao(email, senha) {
  const ctx = await browser.newContext({ baseURL: base, viewport: { width: 1440, height: 1000 } })
  const page = await ctx.newPage()
  page.on('pageerror', (e) => erros.push(`${email}: ${e.message}`))
  await page.goto('/login')
  await page.locator('#login-email').fill(email)
  await page.locator('#login-senha').fill(senha)
  await page.getByRole('button', { name: /entrar/i }).click()
  await page.waitForURL(/\/dashboard/)
  return page
}

/** fetch no contexto da página (cookie de sessão + header CSRF), como o front faz. */
async function api(page, metodo, caminho, corpo) {
  return page.evaluate(async ({ metodo, caminho, corpo }) => {
    const xsrf = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))?.split('=')[1]
    const init = { method: metodo, credentials: 'same-origin', headers: {} }
    if (xsrf) init.headers['X-XSRF-TOKEN'] = decodeURIComponent(xsrf)
    if (corpo instanceof Object && corpo.multipartDados) {
      const f = new FormData()
      f.append('dados', new Blob([JSON.stringify(corpo.multipartDados)], { type: 'application/json' }))
      init.body = f
    } else if (corpo) {
      init.headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(corpo)
    }
    const r = await fetch(caminho, init)
    const tipo = r.headers.get('content-type') ?? ''
    return { status: r.status, tipo, corpo: tipo.includes('json') ? await r.json() : null }
  }, { metodo, caminho, corpo })
}

async function passo(nome, fn) {
  try {
    await fn()
    aprovados.push(nome)
    console.log(`ok  ${nome}`)
  } catch (e) {
    console.error(`FALHOU  ${nome}\n  ${e.message}`)
    throw e
  }
}

const admin = await novaSessao('admin@tribia.local', process.env.TRIBIA_E2E_SENHA)
await api(admin, 'GET', '/api/auth/csrf')

await passo('1. Início da empresa mostra o card da Inteligência Fiscal sem análises (dado real, vazio)', async () => {
  await admin.goto('/dashboard/empresas/1')
  const card = admin.locator('section.card', { has: admin.getByRole('heading', { name: 'Inteligência Fiscal' }) })
  await card.getByText('Nenhuma análise de mercadoria ainda.').waitFor()
  await card.screenshot({ path: join(saida, '1-indicadores-vazio.png') })
})

let analiseId
await passo('2. Análise sem IA configurada termina em FALHA e o card passa a contar a falha', async () => {
  const r = await api(admin, 'POST', '/api/clientes/1/analises-fiscais', {
    multipartDados: { nome: 'Sabonete de glicerina 90 g', descricao: 'Sabonete em barra de glicerina para higiene pessoal, embalado.' },
  })
  assert.equal(r.status, 202, JSON.stringify(r))
  analiseId = r.corpo.id
  for (let i = 0; i < 20; i++) {
    const d = await api(admin, 'GET', `/api/analises-fiscais/${analiseId}`)
    if (d.corpo.status === 'FALHA') break
    await new Promise((ok) => setTimeout(ok, 250))
  }
  const ind = await api(admin, 'GET', '/api/clientes/1/analises-fiscais/indicadores')
  assert.equal(ind.corpo.falhas, 1, JSON.stringify(ind.corpo))
  await admin.goto('/dashboard/empresas/1')
  const card = admin.locator('section.card', { has: admin.getByRole('heading', { name: 'Inteligência Fiscal' }) })
  await card.getByText('1 análise(s) de NCM').waitFor()
  const linha = card.locator('.resumo-periodo__linha', { hasText: 'Com falha' })
  assert.equal((await linha.locator('b').innerText()).trim(), '1')
  await card.screenshot({ path: join(saida, '2-indicadores-com-falha.png') })
})

await passo('3. Relatório PDF de análise sem resultado responde 409 e o detalhe não oferece download', async () => {
  const pdf = await api(admin, 'GET', `/api/analises-fiscais/${analiseId}/relatorio`)
  assert.equal(pdf.status, 409)
  const d = await api(admin, 'GET', `/api/analises-fiscais/${analiseId}`)
  assert.equal(d.corpo.relatorio.disponivel, false)
  await admin.goto(`/dashboard/empresas/1/inteligencia-fiscal/${analiseId}`)
  await admin.getByText(/IA não está configurada/).first().waitFor()
  assert.equal(await admin.getByRole('link', { name: 'Baixar relatório' }).count(), 0)
})

let compra
await passo('4. Compra: desmarcar e confirmar o pagamento pela tela, com confirmação e recálculo', async () => {
  const credito = (n) => n.itens.reduce((s, i) => s + (i.calculo?.imposto2027 ?? 0), 0)
  // compras de itens com alíquota zero não têm crédito: escolhe uma que tenha, para o efeito ser visível
  const notas = await api(admin, 'GET', '/api/clientes/1/notas?tipo=ENTRADA')
  let antes
  for (const n of notas.corpo) {
    const d = await api(admin, 'GET', `/api/notas/${n.id}`)
    if (d.corpo.operacao === 'COMPRA' && credito(d.corpo) > 0) {
      compra = n.id
      antes = d
      break
    }
  }
  assert.ok(antes, 'o seed deveria ter uma compra com crédito de 2027 calculado')
  assert.equal(antes.corpo.pagamentoConfirmado, true)

  await admin.goto(`/dashboard/empresas/1/documentos/${compra}`)
  await admin.getByText('Pagamento ao fornecedor').waitFor()
  await admin.getByRole('button', { name: 'Marcar como não pago' }).click()
  const modal = admin.getByRole('dialog')
  await modal.getByText(/art\. 47/).waitFor()
  await modal.screenshot({ path: join(saida, '4-confirmacao-pagamento.png') })
  await modal.getByRole('button', { name: 'Marcar como não pago' }).click()
  await admin.getByText('Não confirmado', { exact: true }).waitFor()
  const depois = await api(admin, 'GET', `/api/notas/${compra}`)
  assert.equal(depois.corpo.pagamentoConfirmado, false)
  assert.equal(credito(depois.corpo), 0, 'sem pagamento confirmado a compra não gera crédito de 2027')

  await admin.getByRole('button', { name: 'Confirmar pagamento' }).click()
  await admin.getByRole('dialog').getByRole('button', { name: 'Confirmar pagamento' }).click()
  await admin.getByText('Confirmado', { exact: true }).waitFor()
  const volta = await api(admin, 'GET', `/api/notas/${compra}`)
  assert.equal(volta.corpo.pagamentoConfirmado, true)
  assert.equal(credito(volta.corpo).toFixed(2), credito(antes.corpo).toFixed(2), 'confirmar de novo devolve o mesmo crédito')
})

await passo('5. Recalcular 2027 de uma nota já processada', async () => {
  await admin.goto(`/dashboard/empresas/1/documentos/${compra}`)
  const botao = admin.getByRole('button', { name: 'Recalcular 2027' })
  await botao.click()
  await admin.getByText(/Nota recalculada/).waitFor()
  await admin.screenshot({ path: join(saida, '5-nota-recalculada.png') })
})

await passo('6. Recalcular todas as notas da empresa, com confirmação', async () => {
  await admin.goto('/dashboard/empresas/1/configuracoes')
  await admin.getByRole('button', { name: 'Recalcular todas as notas' }).click()
  const modal = admin.getByRole('dialog')
  await modal.getByText(/serão substituídos/).waitFor()
  await modal.getByRole('button', { name: 'Recalcular', exact: true }).click()
  await admin.getByText(/nota\(s\) recalculada\(s\)/).waitFor({ timeout: 30_000 })
  await admin.screenshot({ path: join(saida, '6-empresa-recalculada.png') })
})

await passo('7. Usuário de outra empresa não vê nem altera nada da empresa 1 pelas rotas novas', async () => {
  const email = `e2e-farmacia-${Date.now()}@test.local`
  const criado = await api(admin, 'POST', '/api/clientes/2/usuarios', { nome: 'Farmácia E2E', email, senha: 'senha-apenas-e2e-2026' })
  assert.equal(criado.status, 201, JSON.stringify(criado))
  const farmacia = await novaSessao(email, 'senha-apenas-e2e-2026')
  await api(farmacia, 'GET', '/api/auth/csrf')
  assert.equal((await api(farmacia, 'GET', '/api/clientes/1/analises-fiscais/indicadores')).status, 404)
  assert.equal((await api(farmacia, 'GET', `/api/analises-fiscais/${analiseId}/relatorio`)).status, 404)
  assert.equal((await api(farmacia, 'PUT', `/api/notas/${compra}/pagamento?confirmado=false`)).status, 404)
  assert.equal((await api(farmacia, 'POST', `/api/notas/${compra}/calcular`)).status, 404)
  assert.equal((await api(farmacia, 'POST', '/api/clientes/1/calcular')).status, 404)
  const intacta = await api(admin, 'GET', `/api/notas/${compra}`)
  assert.equal(intacta.corpo.pagamentoConfirmado, true, 'a tentativa negada não pode ter alterado a nota')
  // a própria empresa funciona: o card aparece no início dela
  await farmacia.goto('/dashboard/empresas/2')
  await farmacia.getByRole('heading', { name: 'Inteligência Fiscal' }).waitFor()
  await farmacia.screenshot({ path: join(saida, '7-farmacia-inicio.png') })
})

await browser.close()
assert.deepEqual(erros, [], `Erros de JavaScript na página: ${erros.join(' | ')}`)
console.log(`\n${aprovados.length} fluxos aprovados, 0 erros de JavaScript. Capturas em ${saida}`)

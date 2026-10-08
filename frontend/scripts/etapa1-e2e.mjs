/** E2E real com Chrome descartável. Exige backend explicitamente isolado em H2 memória na porta 18990. */
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFile, mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

assert.equal(process.env.TRIBIA_E2E_ISOLADO, '1', 'Confirme backend isolado; nunca apontar este teste a dados reais.')
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.TRIBIA_PLAYWRIGHT_MODULE ??
  join(tmpdir(), 'tribia-etapa1-playwright-20261008/node_modules/playwright'))
const raiz = resolve(import.meta.dirname, '../..')
const saida = join(raiz, 'backend/target/etapa1-e2e')
await mkdir(saida, { recursive: true })
const base = 'http://127.0.0.1:15173'
const senha = 'senha-apenas-e2e-2026'
const erros = []
const aprovados = []
const browser = await chromium.launch({ executablePath: process.env.TRIBIA_CHROME_PATH ??
  'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const admin = await browser.newContext({ baseURL: base, viewport: { width: 1440, height: 1000 } })
const page = await admin.newPage()
page.on('pageerror', e => erros.push(e.message))

async function passo(nome, fn) {
  try {
    await fn()
    aprovados.push(nome)
    console.log(`PASSOU: ${nome}`)
  } catch (e) {
    await page.screenshot({ path: join(saida, 'falha.png'), fullPage: true }).catch(() => {})
    console.error(`FALHOU: ${nome}: ${e.message}`)
    throw e
  }
}
async function login(p, email, password = senha) {
  await p.goto(`${base}/login`)
  await p.getByLabel('E-mail', { exact: true }).fill(email)
  await p.getByLabel('Senha', { exact: true }).fill(password)
  await p.getByRole('button', { name: 'Entrar', exact: true }).click()
  await p.waitForURL(/\/dashboard/)
}
async function api(context, method, url, data) {
  if (method !== 'GET') await context.request.get('/api/auth/csrf')
  const cookie = (await context.cookies()).find(c => c.name === 'XSRF-TOKEN')
  return context.request.fetch(url, { method, data, headers: cookie ? { 'X-XSRF-TOKEN': decodeURIComponent(cookie.value) } : {} })
}
function novaChave(xml, numero) {
  const velha = xml.match(/Id="NFe(\d{44})"/)[1]
  const baseChave = velha.slice(0, 25) + String(numero).padStart(9, '0') + velha.slice(34, 43)
  let soma = 0, peso = 2
  for (let i = 42; i >= 0; i--) { soma += Number(baseChave[i]) * peso; peso = peso === 9 ? 2 : peso + 1 }
  const dv = soma % 11 < 2 ? 0 : 11 - soma % 11
  return xml.replace(/Id="NFe\d{44}"/, `Id="NFe${baseChave}${dv}"`)
    .replace(/<nNF>\d+<\/nNF>/, `<nNF>${numero}</nNF>`).replace(/<cDV>\d<\/cDV>/, `<cDV>${dv}</cDV>`)
}
const numero = 60000000 + Date.now() % 10000000
let notaA, notaB, empresaA, empresaB
try {
  await passo('login inválido e estado de carregamento', async () => {
    await page.goto(`${base}/login`)
    await page.route('**/api/auth/login', async route => { await new Promise(r => setTimeout(r, 350)); await route.continue() })
    await page.getByLabel('E-mail', { exact: true }).fill('admin@tribia.local')
    await page.getByLabel('Senha', { exact: true }).fill('incorreta-apenas-teste')
    await page.getByRole('button', { name: 'Entrar', exact: true }).click()
    assert.equal(await page.getByLabel('Senha', { exact: true }).isDisabled(), true)
    await page.getByRole('alert').waitFor()
    await page.getByRole('button', { name: 'Entrar', exact: true }).waitFor()
    await page.unroute('**/api/auth/login')
  })
  await passo('login ADMIN e listagem de empresas ativas', async () => {
    await login(page, 'admin@tribia.local')
    await page.goto(`${base}/dashboard/empresas`)
    await page.locator('tbody tr').first().waitFor()
    assert.equal(await page.locator('tbody tr').count(), 3)
    assert.equal(await page.getByRole('note', { name: 'Limitação dos resultados tributários' }).isVisible(), true)
    assert.equal((await api(admin, 'GET', '/api/clientes')).status(), 200)
    await page.screenshot({ path: join(saida, 'empresas-ativas.png'), fullPage: true })
  })
  await passo('seleção de empresa pela interface', async () => {
    await page.locator('.seletor__botao').click()
    await page.getByRole('option', { name: /Distribuidora/ }).click()
    await page.waitForURL(/\/empresas\/1$/)
  })
  await passo('upload XML, carregamento e atualização da lista', async () => {
    await page.goto(`${base}/dashboard/empresas/1/documentos`)
    await page.getByRole('button', { name: 'Enviar notas', exact: true }).click()
    await page.locator('input[type=file]').setInputFiles({ name: 'sintetica-a.xml', mimeType: 'application/xml',
      buffer: Buffer.from(novaChave(await readFile(join(raiz, 'backend/src/test/resources/nfe/nfe_teste_hackathon.xml'), 'utf8'), numero)) })
    await page.route('**/api/clientes/1/notas', async route => {
      if (route.request().method() === 'POST') await new Promise(r => setTimeout(r, 500))
      await route.continue()
    })
    await page.getByRole('button', { name: 'Enviar 1 arquivo', exact: true }).click()
    await page.getByRole('button', { name: 'Processando...', exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Processando...', exact: true }).isDisabled(), true)
    await page.getByRole('dialog').getByText('1 nota(s) importada(s)', { exact: true }).waitFor()
    await page.getByRole('dialog').getByRole('link', { name: `NF-e ${numero}`, exact: true }).click()
    await page.waitForURL(/\/empresas\/1\/documentos\/\d+$/)
    notaA = Number(page.url().split('/').at(-1))
    const detalhe = await (await api(admin, 'GET', `/api/notas/${notaA}`)).json()
    assert.equal(detalhe.itens.length, 8)
    await page.getByText(/^arroz tipo 1 5kg$/i).first().waitFor()
    await page.screenshot({ path: join(saida, 'nota-a.png'), fullPage: true })
    await page.unroute('**/api/clientes/1/notas')
  })
  await passo('erro de XML inválido exibido e upload recuperável', async () => {
    await page.goto(`${base}/dashboard/empresas/1/documentos`)
    await page.getByRole('button', { name: 'Enviar notas', exact: true }).click()
    await page.locator('input[type=file]').setInputFiles({ name: 'invalido.xml', mimeType: 'application/xml', buffer: Buffer.from('<NFe>') })
    await page.getByRole('button', { name: 'Enviar 1 arquivo', exact: true }).click()
    await page.getByRole('dialog').getByText('1 arquivo(s) recusado(s)', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Enviar 1 arquivo', exact: true }).isEnabled(), true)
    await page.getByRole('button', { name: 'Fechar', exact: true }).last().click()
  })
  await passo('preparação sintética de duas contas e nota B por HTTP', async () => {
    for (const id of [1, 2]) {
      const r = await api(admin, 'POST', `/api/clientes/${id}/usuarios`, { nome: `E2E empresa ${id}`, email: `e2e${id}-${numero}@test.local`, senha })
      assert.equal(r.status(), 201)
    }
    const xmlB = novaChave((await readFile(join(raiz, 'backend/src/test/resources/nfe/nfe_entrada_ibscbs.xml'), 'utf8'))
      .replaceAll('10433218000193', '45723174000110'), numero + 1)
    await admin.request.get('/api/auth/csrf')
    const cookie = (await admin.cookies()).find(c => c.name === 'XSRF-TOKEN')
    const r = await admin.request.post('/api/clientes/2/notas', { headers: { 'X-XSRF-TOKEN': decodeURIComponent(cookie.value) },
      multipart: { arquivos: { name: 'sintetica-b.xml', mimeType: 'application/xml', buffer: Buffer.from(xmlB) } } })
    assert.equal(r.status(), 201)
    notaB = (await r.json()).importadas[0].id
    empresaA = await browser.newContext({ baseURL: base })
    empresaB = await browser.newContext({ baseURL: base })
  })
  await passo('duas empresas: login próprio, negativas cruzadas e navegação protegida', async () => {
    const pa = await empresaA.newPage(), pb = await empresaB.newPage()
    pa.on('pageerror', e => erros.push(e.message))
    pb.on('pageerror', e => erros.push(e.message))
    await login(pa, `e2e1-${numero}@test.local`)
    await login(pb, `e2e2-${numero}@test.local`)
    const lista = await (await api(empresaA, 'GET', '/api/clientes')).json()
    assert.deepEqual(lista.map(c => c.id), [1])
    const antes = await (await api(empresaB, 'GET', `/api/notas/${notaB}`)).json()
    for (const [quem, alheia, outra] of [[empresaA, 2, notaB], [empresaB, 1, notaA]]) {
      for (const url of [`/api/clientes/${alheia}/notas`, `/api/notas/${outra}`, `/api/notas/${outra}/export.csv`])
        assert.equal((await api(quem, 'GET', url)).status(), 404)
      for (const acao of ['classificar', 'calcular'])
        assert.equal((await api(quem, 'POST', `/api/notas/${outra}/${acao}`)).status(), 404)
    }
    assert.deepEqual(await (await api(empresaB, 'GET', `/api/notas/${notaB}`)).json(), antes)
    await pa.goto(`${base}/dashboard/empresas/2/documentos/${notaB}`)
    await pa.waitForURL(/\/empresas\/1$/)
    await pa.goto(`${base}/dashboard/empresas`)
    await pa.waitForURL(/\/empresas\/1$/)
    await pa.screenshot({ path: join(saida, 'empresa-a-protegida.png'), fullPage: true })
  })
  await passo('desativação, sessão antiga negada e upload bloqueado na UI', async () => {
    await page.goto(`${base}/dashboard/empresas`)
    await page.getByRole('button', { name: /Desativar Farma/ }).click()
    await page.getByRole('button', { name: 'Desativar empresa', exact: true }).click()
    await page.getByRole('button', { name: /Desativadas/ }).click()
    await page.getByRole('button', { name: /Reativar Farma/ }).waitFor()
    assert.equal((await api(empresaB, 'GET', `/api/notas/${notaB}`)).status(), 403)
    await page.goto(`${base}/dashboard/empresas/2/documentos`)
    await page.getByRole('button', { name: 'Enviar notas', exact: true }).waitFor()
    await page.locator('button[title="Empresa desativada: reative-a para enviar notas"]').waitFor()
    await page.locator('tbody tr').first().waitFor()
    assert.equal(await page.getByRole('button', { name: 'Enviar notas', exact: true }).isDisabled(), true)
    await page.screenshot({ path: join(saida, 'empresa-inativa.png'), fullPage: true })
  })
  await passo('reativação preserva nota e atualiza estado da interface', async () => {
    await page.goto(`${base}/dashboard/empresas`)
    await page.getByRole('button', { name: /Desativadas/ }).click()
    await page.getByRole('button', { name: /Reativar Farma/ }).click()
    await page.getByRole('button', { name: /Ativas/, exact: true }).click()
    await page.getByRole('button', { name: /Desativar Farma/ }).waitFor()
    assert.equal((await api(empresaB, 'GET', `/api/notas/${notaB}`)).status(), 200)
    await page.goto(`${base}/dashboard/empresas/2/documentos`)
    // click espera habilitação: a presença do botão não significa que o GET da empresa terminou.
    await page.getByRole('button', { name: 'Enviar notas', exact: true }).click()
    await page.getByRole('dialog').waitFor()
    assert.equal(await page.getByRole('button', { name: 'Enviar notas', exact: true }).isEnabled(), true)
    await page.getByRole('button', { name: 'Fechar', exact: true }).last().click()
  })
  await passo('logout real invalida sessão e protege rota', async () => {
    await page.getByRole('button', { name: /^Conta de/ }).click()
    await page.getByRole('menuitem', { name: 'Sair', exact: true }).click()
    await page.waitForURL(/\/login$/)
    assert.equal((await api(admin, 'GET', '/api/clientes')).status(), 401)
    await page.goto(`${base}/dashboard/empresas`)
    await page.waitForURL(/\/login$/)
    await page.screenshot({ path: join(saida, 'logout.png'), fullPage: true })
  })
  assert.deepEqual(erros, [], 'Sem erros JavaScript inesperados')
  console.log(`E2E: ${aprovados.length} fluxos aprovados; erros JavaScript: ${erros.length}; capturas: ${saida}`)
} finally {
  await browser.close()
}

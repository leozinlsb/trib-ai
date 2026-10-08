/**
 * E2E da Etapa 4 (fluxo de ponta a ponta no navegador): login → empresa → upload de XML → classificação e cálculo →
 * nota → revisão → alertas → relatório/CSV → perfil de empresa → logout. Cobre os fluxos das Etapas 2 e 3.
 *
 * Exige backend isolado (profile demo, H2 em memória) e o front servido na porta 15173 apontando para ele:
 *   backend: TRIBIA_ADMIN_SENHA=... mvnw spring-boot:run -Dspring-boot.run.profiles=demo
 *            -Dspring-boot.run.arguments="--server.port=8190 --spring.datasource.url=jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1"
 *   front:   npm run build; TRIBIA_BACKEND_URL=http://localhost:8190 npx vite preview --port 15173 --host 127.0.0.1
 *   teste:   TRIBIA_E2E_ISOLADO=1 TRIBIA_E2E_SENHA=... TRIBIA_PLAYWRIGHT_MODULE=<pasta>/node_modules/playwright \
 *            node scripts/etapa4-e2e.mjs
 * Reinicia os dados de demonstração (POST /api/demo/reiniciar): nunca aponte para dados reais.
 * Com GEMINI_API_KEY configurada no backend a classificação usa a IA de verdade (poucas chamadas, ~12 s).
 */
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFile, mkdir } from 'node:fs/promises'
import { join, resolve } from 'node:path'

assert.equal(process.env.TRIBIA_E2E_ISOLADO, '1', 'Confirme backend isolado; nunca apontar este teste a dados reais.')
assert.ok(process.env.TRIBIA_E2E_SENHA, 'Informe a senha do administrador em TRIBIA_E2E_SENHA.')
assert.ok(process.env.TRIBIA_PLAYWRIGHT_MODULE, 'Informe TRIBIA_PLAYWRIGHT_MODULE (pasta do módulo playwright).')
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.TRIBIA_PLAYWRIGHT_MODULE)
const raiz = resolve(import.meta.dirname, '../..')
const saida = join(raiz, 'backend/target/etapa4-e2e')
await mkdir(saida, { recursive: true })
const base = process.env.TRIBIA_E2E_BASE ?? 'http://127.0.0.1:15173'
const senhaAdmin = process.env.TRIBIA_E2E_SENHA
const senhaEmpresa = 'senha-apenas-e2e-2026'
const erros = []
const aprovados = []

const browser = await chromium.launch({
  executablePath: process.env.TRIBIA_CHROME_PATH ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true,
})
const admin = await browser.newContext({ baseURL: base, viewport: { width: 1440, height: 1000 } })
const page = await admin.newPage()
page.on('pageerror', (e) => erros.push(e.message))

async function passo(nome, fn) {
  const inicio = Date.now()
  try {
    await fn()
    aprovados.push(nome)
    console.log(`PASSOU (${Date.now() - inicio} ms): ${nome}`)
  } catch (e) {
    await page.screenshot({ path: join(saida, 'falha.png'), fullPage: true }).catch(() => {})
    console.error(`FALHOU: ${nome}: ${e.message}`)
    throw e
  }
}
async function login(p, email, senha) {
  await p.goto(`${base}/login`)
  await p.getByLabel('E-mail', { exact: true }).fill(email)
  await p.getByLabel('Senha', { exact: true }).fill(senha)
  await p.getByRole('button', { name: 'Entrar', exact: true }).click()
  await p.waitForURL(/\/dashboard/)
}
async function api(context, method, url, data) {
  if (method !== 'GET') await context.request.get('/api/auth/csrf')
  const cookie = (await context.cookies()).find((c) => c.name === 'XSRF-TOKEN')
  return context.request.fetch(url, {
    method, data, headers: cookie ? { 'X-XSRF-TOKEN': decodeURIComponent(cookie.value) } : {},
  })
}
async function json(r) { return r.json() }
const fmt = (v) => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }).replace(/\s+/g, ' ')
async function esperar(condicao, descricao, ms = 120000) {
  const limite = Date.now() + ms
  while (Date.now() < limite) {
    if (await condicao()) return
    await page.waitForTimeout(1000)
  }
  throw new Error(`Tempo esgotado: ${descricao}`)
}

try {
  await passo('login inválido é recusado e login ADMIN entra', async () => {
    await page.goto(`${base}/login`)
    await page.getByLabel('E-mail', { exact: true }).fill('admin@tribia.local')
    await page.getByLabel('Senha', { exact: true }).fill('incorreta-apenas-teste')
    await page.getByRole('button', { name: 'Entrar', exact: true }).click()
    await page.getByRole('alert').waitFor()
    assert.match(page.url(), /\/login/)
    await login(page, 'admin@tribia.local', senhaAdmin)
  })

  await passo('estado inicial da demonstração', async () => {
    const r = await api(admin, 'POST', '/api/demo/reiniciar')
    assert.equal(r.status(), 200)
    assert.equal((await json(r)).notasImportadas, 18)
    await page.goto(`${base}/dashboard`)
    for (const nome of ['Distribuidora Fictícia', 'Farma Fictícia', 'Casa Limpa Fictícia']) {
      await page.getByText(nome).first().waitFor()
    }
    assert.equal((await json(await api(admin, 'GET', '/api/clientes'))).length, 3)
  })

  await passo('início da empresa: comparativo hoje × 2027 vem do backend', async () => {
    await page.goto(`${base}/dashboard/empresas/1`)
    await page.getByText('Impacto da reforma: hoje × 2027').waitFor()
    const dash = await json(await api(admin, 'GET', '/api/clientes/1/dashboard'))
    // os valores chegam depois do título; espaços (inclusive o não separável do R$) normalizados dos dois lados
    const esperado = [fmt(dash.indicadores.liquidoHoje), fmt(dash.indicadores.liquido2027)]
    await esperar(async () => {
      const texto = (await page.locator('main').innerText()).replace(/\s+/g, ' ')
      return esperado.every((v) => texto.includes(v))
    }, `comparativo ${esperado.join(' / ')} na tela`, 15000)
    assert.equal(dash.indicadores.pendentesRevisao, 0)
    await page.screenshot({ path: join(saida, '1-inicio-empresa.png'), fullPage: true })
  })

  let notaNova
  await passo('upload de XML pela interface e processamento (classificar + calcular)', async () => {
    await page.goto(`${base}/dashboard/empresas/1/documentos`)
    await page.getByRole('button', { name: 'Enviar notas', exact: true }).click()
    await page.locator('input[type=file]').setInputFiles({
      name: '1-distribuidora_nf1004.xml', mimeType: 'application/xml',
      buffer: await readFile(join(raiz, 'backend/notas-demo-ao-vivo/1-distribuidora_nf1004.xml')),
    })
    await page.getByRole('button', { name: 'Enviar 1 arquivo', exact: true }).click()
    await page.getByRole('dialog').getByText('1 nota(s) importada(s)', { exact: true }).waitFor()
    const notas = await json(await api(admin, 'GET', '/api/clientes/1/notas'))
    notaNova = notas.find((n) => n.numero === 1004).id
    // a classificação roda em segundo plano depois do envio (com IA real leva ~12 s)
    await esperar(async () => {
      const d = await json(await api(admin, 'GET', `/api/notas/${notaNova}`))
      return d.itens.length === 8 && d.itens.every((i) => i.classificacao && i.calculo)
    }, 'nota 1004 classificada e calculada')
    await page.screenshot({ path: join(saida, '2-upload.png'), fullPage: true })
  })

  await passo('XML inválido e nota duplicada: erros claros na interface', async () => {
    await page.goto(`${base}/dashboard/empresas/1/documentos`)
    await page.getByRole('button', { name: 'Enviar notas', exact: true }).click()
    await page.locator('input[type=file]').setInputFiles([
      { name: 'invalido.xml', mimeType: 'application/xml', buffer: Buffer.from('<NFe>') },
      { name: 'duplicada.xml', mimeType: 'application/xml',
        buffer: await readFile(join(raiz, 'backend/notas-demo-ao-vivo/1-distribuidora_nf1004.xml')) },
    ])
    await page.getByRole('button', { name: 'Enviar 2 arquivos', exact: true }).click()
    await page.getByRole('dialog').getByText('2 arquivo(s) recusado(s)', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Fechar', exact: true }).last().click()
  })

  await passo('detalhe da nota mostra classificação e cálculo persistidos', async () => {
    await page.goto(`${base}/dashboard/empresas/1/documentos/${notaNova}`)
    await page.getByText('8 com classificação tributária').waitFor()
    await page.getByText('Classificação (reforma)').first().waitFor()
    const texto = await page.locator('main').innerText()
    assert.ok(/\b\d{6}\b/.test(texto), 'cClassTrib visível')
    await page.screenshot({ path: join(saida, '3-nota.png'), fullPage: true })
  })

  await passo('revisão: itens sugeridos pela IA aguardam aceite; aceitar tira da fila e recalcula', async () => {
    const antes = (await json(await api(admin, 'GET', '/api/clientes/1/revisao'))).total
    assert.ok(antes >= 1, 'há itens para revisar depois do upload')
    await page.goto(`${base}/dashboard/empresas/1/revisao`)
    await page.getByText(`${antes} item(ns) para revisar`).waitFor()
    await page.getByRole('button', { name: /Aceitar/ }).first().click()
    await page.getByText(/Classificação aceita/).first().waitFor()
    await esperar(async () => (await json(await api(admin, 'GET', '/api/clientes/1/revisao'))).total < antes,
      'fila de revisão diminuir', 15000)
    const dash = await json(await api(admin, 'GET', '/api/clientes/1/dashboard'))
    assert.equal(dash.indicadores.pendentesRevisao, (await json(await api(admin, 'GET', '/api/clientes/1/revisao'))).total)
    await page.screenshot({ path: join(saida, '4-revisao.png'), fullPage: true })
  })

  await passo('alertas fiscais carregam sobre os dados persistidos', async () => {
    await page.goto(`${base}/dashboard/empresas/1/alertas`)
    await page.getByRole('heading', { name: 'Alertas fiscais' }).waitFor()
    await page.getByText(/alerta\(s\)$/).first().waitFor()
    await page.getByText('Alíquota de referência 2027').waitFor()
    await page.screenshot({ path: join(saida, '5-alertas.png'), fullPage: true })
  })

  await passo('relatório da empresa e CSV (Excel pt-BR e padrão)', async () => {
    await page.goto(`${base}/dashboard/empresas/1/analises`)
    await page.getByText('Apuração de PIS/Cofins (regras atuais)').waitFor()
    const excel = await api(admin, 'GET', '/api/clientes/1/relatorio.csv')
    assert.equal(excel.status(), 200)
    const bytes = await excel.body()
    assert.deepEqual([...bytes.subarray(0, 3)], [0xef, 0xbb, 0xbf], 'BOM UTF-8')
    assert.ok(bytes.toString('utf8').includes(';'), 'separador ;')
    const padrao = await api(admin, 'GET', '/api/clientes/1/relatorio.csv?formato=PADRAO')
    assert.equal(padrao.status(), 200)
    assert.ok((await padrao.text()).split('\n')[0].includes(','), 'separador ,')
    await page.screenshot({ path: join(saida, '6-relatorio.png'), fullPage: true })
  })

  let contextoEmpresa
  await passo('perfil EMPRESA: só enxerga a própria empresa', async () => {
    const email = `e2e-empresa2-${Date.now()}@test.local`
    const r = await api(admin, 'POST', '/api/clientes/2/usuarios', { nome: 'E2E Farmácia', email, senha: senhaEmpresa })
    assert.equal(r.status(), 201)
    contextoEmpresa = await browser.newContext({ baseURL: base, viewport: { width: 1440, height: 1000 } })
    const pe = await contextoEmpresa.newPage()
    pe.on('pageerror', (e) => erros.push(e.message))
    await login(pe, email, senhaEmpresa)
    const lista = await json(await api(contextoEmpresa, 'GET', '/api/clientes'))
    assert.deepEqual(lista.map((c) => c.id), [2])
    for (const url of ['/api/clientes/1/dashboard', '/api/clientes/1/revisao', '/api/clientes/1/relatorio.csv',
      `/api/notas/${notaNova}`]) {
      const negada = await api(contextoEmpresa, 'GET', url)
      assert.ok([403, 404].includes(negada.status()), `${url} deveria ser negada, veio ${negada.status()}`)
    }
    const calcular = await api(contextoEmpresa, 'POST', `/api/notas/${notaNova}/classificar`)
    assert.ok([403, 404].includes(calcular.status()), 'classificar nota de outra empresa deve ser negado')
    // URL de outra empresa: a interface volta para a própria e não pede nada da empresa 1 ao backend
    const pedidas = []
    pe.on('request', (q) => { if (q.url().includes('/api/')) pedidas.push(new URL(q.url()).pathname) })
    await pe.goto(`${base}/dashboard/empresas/1`)
    await pe.waitForURL(/\/dashboard\/empresas\/2$/)
    await pe.getByText('Farma Fictícia').first().waitFor()
    await pe.waitForTimeout(1500)
    assert.deepEqual(pedidas.filter((u) => u.startsWith('/api/clientes/1')), [], 'nenhuma chamada à empresa 1')
    await pe.screenshot({ path: join(saida, '7-perfil-empresa.png'), fullPage: true })
  })

  await passo('logout invalida a sessão', async () => {
    await page.goto(`${base}/dashboard`)
    await page.getByRole('button', { name: /^Conta de/ }).click()
    await page.getByRole('menuitem', { name: 'Sair', exact: true }).click()
    await page.waitForURL(/\/login$/)
    assert.equal((await api(admin, 'GET', '/api/clientes')).status(), 401)
  })

  await contextoEmpresa?.close()
  assert.deepEqual(erros, [], 'Sem erros JavaScript inesperados')
  console.log(`E2E Etapa 4: ${aprovados.length} fluxos aprovados; erros JavaScript: ${erros.length}; capturas: ${saida}`)
} finally {
  await browser.close()
}

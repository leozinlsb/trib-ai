/**
 * E2E da tela de integrações (chaves da API pública): o administrador emite uma chave, ela aparece uma única vez,
 * funciona na API pública, é revogada pela tela e para de funcionar. Mesmo ambiente isolado do etapa4-e2e.mjs:
 *   TRIBIA_E2E_ISOLADO=1 TRIBIA_E2E_SENHA=... TRIBIA_PLAYWRIGHT_MODULE=... node scripts/integracoes-e2e.mjs
 * Não chama a IA (só GET /api/v1/uso com a chave).
 */
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'
import { join, resolve } from 'node:path'

assert.equal(process.env.TRIBIA_E2E_ISOLADO, '1', 'Confirme backend isolado; nunca apontar este teste a dados reais.')
assert.ok(process.env.TRIBIA_E2E_SENHA, 'Informe a senha do administrador em TRIBIA_E2E_SENHA.')
assert.ok(process.env.TRIBIA_PLAYWRIGHT_MODULE, 'Informe TRIBIA_PLAYWRIGHT_MODULE (pasta do módulo playwright).')
const { chromium } = createRequire(import.meta.url)(process.env.TRIBIA_PLAYWRIGHT_MODULE)
const saida = join(resolve(import.meta.dirname, '../..'), 'backend/target/integracoes-e2e')
await mkdir(saida, { recursive: true })
const base = process.env.TRIBIA_E2E_BASE ?? 'http://127.0.0.1:15173'
const erros = []

const browser = await chromium.launch({
  executablePath: process.env.TRIBIA_CHROME_PATH ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true,
})
const contexto = await browser.newContext({ baseURL: base, viewport: { width: 1440, height: 1000 } })
const page = await contexto.newPage()
page.on('pageerror', (e) => erros.push(e.message))

async function passo(nome, fn) {
  const inicio = Date.now()
  try {
    await fn()
    console.log(`PASSOU (${Date.now() - inicio} ms): ${nome}`)
  } catch (e) {
    await page.screenshot({ path: join(saida, 'falha.png'), fullPage: true }).catch(() => {})
    console.error(`FALHOU: ${nome}: ${e.message}`)
    throw e
  }
}
/** Chama a API pública como um integrador faria (sem cookie de sessão). */
async function usoComChave(chave) {
  const r = await fetch(`${base}/api/v1/uso`, { headers: { 'X-API-Key': chave } })
  return { status: r.status, corpo: await r.json().catch(() => null) }
}

let chave
try {
  await passo('login do administrador e seção de integrações nas configurações', async () => {
    await page.goto(`${base}/login`)
    await page.getByLabel('E-mail', { exact: true }).fill('admin@tribia.local')
    await page.getByLabel('Senha', { exact: true }).fill(process.env.TRIBIA_E2E_SENHA)
    await page.getByRole('button', { name: 'Entrar', exact: true }).click()
    await page.waitForURL(/\/dashboard/)
    await page.goto(`${base}/dashboard/configuracoes`)
    await page.getByRole('heading', { name: 'Integrações (API pública)' }).waitFor()
  })

  await passo('formulário recusa envio incompleto', async () => {
    await page.getByRole('button', { name: /Nova chave/ }).click()
    await page.getByRole('button', { name: /Emitir chave/ }).click()
    await page.getByText('Escolha a empresa.').waitFor()
    await page.getByText('Informe o nome do integrador.').waitFor()
  })

  await passo('emite a chave e ela aparece uma única vez, com confirmação para fechar', async () => {
    await page.locator('#nc-empresa').selectOption({ label: 'Distribuidora Fictícia' })
    await page.locator('#nc-nome').fill('ERP E2E')
    // tira uma permissão para conferir que a escolha chega ao servidor
    await page.getByRole('checkbox', { name: /Analisar NCM de mercadorias/ }).uncheck()
    await page.getByRole('button', { name: /Emitir chave/ }).click()
    await page.getByRole('heading', { name: 'Chave emitida' }).waitFor()
    chave = (await page.locator('#chave-emitida').innerText()).trim()
    assert.match(chave, /^tribia_[0-9a-f]{12}_[A-Za-z0-9_-]{40,}$/)
    assert.equal(await page.getByRole('button', { name: 'Concluir' }).isDisabled(), true, 'só fecha depois de confirmar')
    await page.screenshot({ path: join(saida, '1-chave-emitida.png') })
    await page.getByRole('checkbox', { name: /Já guardei a chave/ }).check()
    await page.getByRole('button', { name: 'Concluir' }).click()
    await page.getByRole('heading', { name: 'Chave emitida' }).waitFor({ state: 'detached' })
    const texto = await page.locator('main').innerText()
    assert.ok(!texto.includes(chave), 'depois de fechar, a chave completa não fica em lugar nenhum da tela')
    assert.ok(texto.includes(chave.slice(0, 19)), 'a lista mostra só o prefixo')
    await page.getByText('ERP E2E').first().waitFor()
  })

  await passo('a chave funciona na API pública, presa à empresa e com as permissões escolhidas', async () => {
    const r = await usoComChave(chave)
    assert.equal(r.status, 200)
    assert.equal(r.corpo.empresa.cnpj, '10433218000193')
    assert.equal(r.corpo.chave.escopos.includes('ANALISES_CRIAR'), false)
    assert.equal(r.corpo.limites.cotaDiariaItensIa, 500)
  })

  await passo('revoga pela tela e a chave para de funcionar na hora', async () => {
    await page.reload()
    const linha = page.locator('tr', { hasText: 'ERP E2E' })
    await linha.getByRole('button', { name: /Revogar/ }).click()
    await page.getByRole('button', { name: 'Revogar chave' }).click()
    await linha.getByText('Revogada').waitFor()
    await page.screenshot({ path: join(saida, '2-revogada.png'), fullPage: true })
    const r = await usoComChave(chave)
    assert.equal(r.status, 401)
    assert.equal(r.corpo.codigo, 'CHAVE_REVOGADA')
  })

  assert.deepEqual(erros, [], 'Sem erros JavaScript inesperados')
  console.log(`E2E integrações aprovado; capturas: ${saida}`)
} finally {
  await browser.close()
}

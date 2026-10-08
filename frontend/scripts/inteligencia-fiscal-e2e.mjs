/**
 * E2E da Inteligência Fiscal no navegador: nova análise (3 passos) → acompanhamento → resultado → histórico.
 * Usa a IA de verdade se o backend tiver GEMINI_API_KEY (1 chamada); sem chave, a análise termina em FALHA com
 * mensagem, o que também é verificado. Mesmo ambiente isolado do etapa4-e2e.mjs (ver o cabeçalho dele):
 *   TRIBIA_E2E_ISOLADO=1 TRIBIA_E2E_SENHA=... TRIBIA_PLAYWRIGHT_MODULE=... node scripts/inteligencia-fiscal-e2e.mjs
 */
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'
import { join, resolve } from 'node:path'

assert.equal(process.env.TRIBIA_E2E_ISOLADO, '1', 'Confirme backend isolado; nunca apontar este teste a dados reais.')
assert.ok(process.env.TRIBIA_E2E_SENHA, 'Informe a senha do administrador em TRIBIA_E2E_SENHA.')
assert.ok(process.env.TRIBIA_PLAYWRIGHT_MODULE, 'Informe TRIBIA_PLAYWRIGHT_MODULE (pasta do módulo playwright).')
const { chromium } = createRequire(import.meta.url)(process.env.TRIBIA_PLAYWRIGHT_MODULE)
const saida = join(resolve(import.meta.dirname, '../..'), 'backend/target/inteligencia-fiscal-e2e')
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

let analiseId
try {
  await passo('login e tela da Inteligência Fiscal no menu', async () => {
    await page.goto(`${base}/login`)
    await page.getByLabel('E-mail', { exact: true }).fill('admin@tribia.local')
    await page.getByLabel('Senha', { exact: true }).fill(process.env.TRIBIA_E2E_SENHA)
    await page.getByRole('button', { name: 'Entrar', exact: true }).click()
    await page.waitForURL(/\/dashboard/)
    await page.goto(`${base}/dashboard/empresas/1/inteligencia-fiscal`)
    await page.waitForTimeout(1500)
    const texto = await page.locator('main').innerText()
    assert.ok(!/ainda não disponível|indisponível/i.test(texto), 'a tela não pode mostrar "serviço indisponível"')
    await page.screenshot({ path: join(saida, '1-inicio.png'), fullPage: true })
  })

  await passo('nova análise em 3 passos com anexo de texto', async () => {
    await page.goto(`${base}/dashboard/empresas/1/inteligencia-fiscal/nova`)
    await page.getByLabel(/Nome da mercadoria/).fill('Sabonete de glicerina 90 g')
    await page.getByLabel(/Descrição detalhada/).fill(
      'Sabonete em barra de glicerina, para higiene pessoal (lavar mãos e corpo), embalado individualmente em caixa de papel.')
    await page.getByLabel(/Composição ou material/).fill('Glicerina vegetal, base de sabão de óleo de coco, essência.')
    await page.getByLabel(/NCM atual/).fill('34011190')
    await page.getByRole('button', { name: /Continuar/ }).click()
    await page.locator('input[type=file]').setInputFiles({
      name: 'ficha-tecnica.txt', mimeType: 'text/plain', buffer: Buffer.from('Peso líquido 90 g. pH 9 a 10. Uso tópico.'),
    })
    await page.getByRole('button', { name: /Continuar/ }).click()
    await page.getByRole('button', { name: /Iniciar análise fiscal/ }).click()
    await page.waitForURL(/\/inteligencia-fiscal\/\d+$/)
    analiseId = Number(page.url().split('/').at(-1))
  })

  await passo('acompanhamento até o fim do processamento', async () => {
    const fim = Date.now() + 120000
    let detalhe
    while (Date.now() < fim) {
      detalhe = await (await contexto.request.get(`/api/analises-fiscais/${analiseId}`)).json()
      if (!['AGUARDANDO', 'INTERPRETANDO', 'PESQUISANDO_NCM', 'AVALIANDO', 'VALIDANDO', 'GERANDO_RELATORIO'].includes(detalhe.status)) break
      await page.waitForTimeout(2000)
    }
    console.log(`        status final: ${detalhe.status}${detalhe.ncmSugerida ? ` | NCM sugerida ${detalhe.ncmSugerida}` : ''}${detalhe.mensagem ? ` | ${detalhe.mensagem}` : ''}`)
    if (detalhe.resultado) {
      // a tela atualiza sozinha (consulta a cada 4 s) e mostra o resultado
      await page.getByText('NCM sugerida').first().waitFor({ timeout: 15000 })
      assert.ok(detalhe.alternativas.length >= 1)
      assert.ok(detalhe.validacao.verificacoes.length >= 4)
    } else {
      assert.ok(detalhe.mensagem, 'sem resultado, a análise explica o motivo')
    }
    await page.screenshot({ path: join(saida, '2-resultado.png'), fullPage: true })
  })

  await passo('histórico e indicadores mostram a análise', async () => {
    const lista = await (await contexto.request.get('/api/clientes/1/analises-fiscais')).json()
    assert.ok(lista.itens.some((a) => a.id === analiseId))
    const ind = await (await contexto.request.get('/api/clientes/1/analises-fiscais/indicadores')).json()
    assert.ok(ind.total >= 1)
    await page.goto(`${base}/dashboard/empresas/1/inteligencia-fiscal`)
    await page.getByText('Sabonete de glicerina 90 g').first().waitFor()
    await page.screenshot({ path: join(saida, '3-historico.png'), fullPage: true })
  })

  assert.deepEqual(erros, [], 'Sem erros JavaScript inesperados')
  console.log(`E2E Inteligência Fiscal aprovado; capturas: ${saida}`)
} finally {
  await browser.close()
}

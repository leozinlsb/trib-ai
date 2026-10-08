// Testes do motor de alertas (src/lib/alertas.ts). Roda com o Node 22.6+, sem dependências: npm run test
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { gerarAlertas, fatorRegime, baseEstimada } from '../src/lib/alertas.ts'
import type { Classificacao, Item, NotaDetalhe, OpcaoClassificacao } from '../src/api/types.ts'

const EMPRESA = '10433218000193'
const FORNECEDOR = '51938267000165'

const integral: OpcaoClassificacao = {
  cClassTrib: '000001', cst: '000', nome: 'Tributação integral', regime: 'INTEGRAL', descricaoRegime: null,
  anexo: null, exigeNcmNaLista: false, sugeridaPeloNcm: false,
}
const reducao60 = (sugerida: boolean): OpcaoClassificacao => ({
  cClassTrib: '200035', cst: '200', nome: 'Higiene pessoal e limpeza', regime: 'REDUZIDA',
  descricaoRegime: 'Redução de 60%', anexo: 'VIII', exigeNcmNaLista: true, sugeridaPeloNcm: sugerida,
})
const insumoAgro: OpcaoClassificacao = {
  cClassTrib: '200038', cst: '200', nome: 'Insumos agropecuários', regime: 'REDUZIDA',
  descricaoRegime: 'Redução de 60%', anexo: 'IX', exigeNcmNaLista: true, sugeridaPeloNcm: true,
}

function classificacao(c: Partial<Classificacao> = {}): Classificacao {
  return {
    cst: '000', cClassTrib: '000001', nomeCClassTrib: null, regime: 'INTEGRAL', descricaoRegime: null,
    justificativa: null, confianca: 1, origem: 'XML', aceita: true, revisada: false, ...c,
  }
}

function item(id: number, codigoXml: string | null, c: Classificacao | null = classificacao()): Item {
  return {
    id, nItem: id, codigo: null, descricao: `PRODUTO ${id}`, ncm: '34011190', cfop: '5102', unidade: 'UN',
    quantidade: 1, valorTotal: 1000, valorUnitario: 1000, vIcms: 180, cstPisCofins: '01', vPis: 16.5,
    vCofins: 76, creditavel: true,
    ibsCbsDestacado: codigoXml ? ({ cst: '000', cClassTrib: codigoXml } as Item['ibsCbsDestacado']) : null,
    classificacao: c, calculo: null,
  }
}

function nota(id: number, itens: Item[], n: Partial<NotaDetalhe> = {}): NotaDetalhe {
  return {
    id, clienteId: 1, tipo: 'SAIDA', chave: String(id), numero: id, serie: 1, dataEmissao: '2026-08-01',
    competencia: '2026-08', operacao: 'VENDA', emitenteCnpj: EMPRESA, emitenteNome: 'EMPRESA', emitenteCrt: 3,
    destinatarioDocumento: null, destinatarioNome: null, contraparteCnpj: null, contraparteNome: 'CLIENTE',
    valorProdutos: 1000, valorTotal: 1000, itens, ...n,
  }
}

const opcoes = (lista: OpcaoClassificacao[]) => new Map([['34011190', lista]])

test('base de 2027 tira ICMS, PIS e Cofins do valor do item', () => {
  assert.equal(baseEstimada(item(1, null)), 1000 - 180 - 16.5 - 76)
})

test('fator do regime: integral 1, redução de 60% 0,4, alíquota zero 0, sem percentual desconhecido', () => {
  assert.equal(fatorRegime('INTEGRAL'), 1)
  assert.ok(Math.abs(fatorRegime('REDUZIDA', 'Redução de 60%')! - 0.4) < 1e-9)
  assert.equal(fatorRegime('ALIQUOTA_ZERO'), 0)
  assert.equal(fatorRegime('REDUZIDA', null), null)
  assert.equal(fatorRegime('OUTRO'), null)
})

test('venda com tributação integral e NCM na lista do benefício: oportunidade com o valor em jogo', () => {
  const r = gerarAlertas({
    cnpjEmpresa: EMPRESA, notas: [nota(1, [item(1, '000001')])], opcoesPorNcm: opcoes([reducao60(true), integral]),
  })
  const a = r.alertas.find((x) => x.tipo === 'BENEFICIO_NAO_APLICADO')
  assert.ok(a, 'alerta de benefício não aplicado')
  assert.equal(a.categoria, 'oportunidade')
  assert.equal(a.sugestao?.cClassTrib, '200035')
  // base 727,50 × alíquota padrão 9,53% × (1 − 0,4)
  assert.equal(a.impacto, Math.round(727.5 * 0.0953 * 0.6 * 100) / 100)
})

test('benefício aplicado a NCM fora da lista: risco na venda própria, sugestão integral', () => {
  const r = gerarAlertas({
    cnpjEmpresa: EMPRESA,
    notas: [nota(1, [item(1, '200035', classificacao({ cst: '200', cClassTrib: '200035', regime: 'REDUZIDA' }))])],
    opcoesPorNcm: opcoes([reducao60(false), integral]),
  })
  const a = r.alertas.find((x) => x.tipo === 'BENEFICIO_FORA_DA_LISTA')
  assert.ok(a)
  assert.equal(a.categoria, 'risco')
  assert.equal(a.sugestao?.cClassTrib, '000001')
})

test('código que depende do adquirente nunca vira sugestão de benefício', () => {
  const r = gerarAlertas({
    cnpjEmpresa: EMPRESA, notas: [nota(1, [item(1, '000001')])], opcoesPorNcm: opcoes([insumoAgro, integral]),
  })
  assert.equal(r.alertas.filter((x) => x.tipo === 'BENEFICIO_NAO_APLICADO').length, 0)
})

test('par CST/cClassTrib fora da tabela: alerta de código inválido, sem valor estimado', () => {
  const r = gerarAlertas({
    cnpjEmpresa: EMPRESA,
    notas: [nota(1, [item(1, '999999', classificacao({ origem: 'IA', aceita: false, confianca: 0.8 }))])],
    opcoesPorNcm: opcoes([integral]),
  })
  const a = r.alertas.find((x) => x.tipo === 'CODIGO_INEXISTENTE')
  assert.ok(a)
  assert.equal(a.impacto, null)
})

test('compras de fornecedor do Simples são agregadas por fornecedor', () => {
  const compra = (id: number) => nota(id, [item(id, null)], {
    tipo: 'ENTRADA', operacao: 'COMPRA', emitenteCnpj: FORNECEDOR, emitenteNome: 'FORNECEDOR SN', emitenteCrt: 1,
  })
  const r = gerarAlertas({ cnpjEmpresa: EMPRESA, notas: [compra(1), compra(2)], opcoesPorNcm: new Map() })
  const simples = r.alertas.filter((x) => x.tipo === 'FORNECEDOR_SIMPLES')
  assert.equal(simples.length, 1)
  assert.equal(simples[0]!.quantidade, 2)
})

test('itens sem classificação, não aceitos ou de baixa confiança contam como pendentes; revisados não', () => {
  const r = gerarAlertas({
    cnpjEmpresa: EMPRESA,
    notas: [nota(1, [
      item(1, null, null),
      item(2, null, classificacao({ origem: 'IA', aceita: false })),
      item(3, null, classificacao({ origem: 'CACHE', confianca: 0.65 })),
      item(4, null, classificacao({ origem: 'IA', aceita: false, revisada: true })),
      item(5, null, classificacao()),
    ])],
    opcoesPorNcm: new Map(),
  })
  assert.equal(r.alertas.find((x) => x.tipo === 'REVISAO_PENDENTE')?.quantidade, 3)
})

test('R1: benefício não aplicado numa compra é efeito no preço e não soma em oportunidades', () => {
  const compra = nota(1, [item(1, '000001')], {
    tipo: 'ENTRADA', operacao: 'COMPRA', emitenteCnpj: FORNECEDOR, emitenteNome: 'FORNECEDOR', emitenteCrt: 3,
  })
  const r = gerarAlertas({ cnpjEmpresa: EMPRESA, notas: [compra], opcoesPorNcm: opcoes([reducao60(true), integral]) })
  const a = r.alertas.find((x) => x.tipo === 'BENEFICIO_NAO_APLICADO')
  assert.ok(a)
  assert.equal(a.categoria, 'conformidade')
  assert.equal(a.impacto, null)
  assert.equal(a.efeitoNoPreco, Math.round(727.5 * 0.0953 * 0.6 * 100) / 100)
  assert.equal(r.resumo.oportunidade, 0)
})

test('R1: divergência confirmada numa compra também não soma em oportunidades', () => {
  const compra = nota(1, [item(1, '000001', classificacao({ cst: '200', cClassTrib: '200035', regime: 'REDUZIDA', descricaoRegime: 'Redução de 60%', revisada: true }))], {
    tipo: 'ENTRADA', operacao: 'COMPRA', emitenteCnpj: FORNECEDOR, emitenteNome: 'FORNECEDOR', emitenteCrt: 3,
  })
  const r = gerarAlertas({ cnpjEmpresa: EMPRESA, notas: [compra], opcoesPorNcm: opcoes([reducao60(true), integral]) })
  const a = r.alertas.find((x) => x.tipo === 'DIVERGENCIA_CONFIRMADA')
  assert.ok(a)
  assert.equal(a.impacto, null)
  assert.ok((a.efeitoNoPreco ?? 0) > 0)
  assert.equal(r.resumo.oportunidade, 0)
})

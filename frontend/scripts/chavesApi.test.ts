// Testes da lógica da tela de integrações (src/lib/chavesApi.ts). Roda com o Node 22.6+: npm test
import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  ESCOPOS, FORM_CHAVE_VAZIO, TODOS_OS_ESCOPOS, corpoNovaChave, exemploDeUso, resumoEscopos, validarFormChave,
  type FormChave,
} from '../src/lib/chavesApi.ts'

const valido = (extra: Partial<FormChave> = {}): FormChave => ({
  ...FORM_CHAVE_VAZIO, escopos: [...TODOS_OS_ESCOPOS], clienteId: '1', nomeIntegrador: 'ERP da Distribuidora', ...extra,
})

test('os seis escopos do backend estão na tela, e todos vêm marcados por padrão', () => {
  assert.deepEqual([...TODOS_OS_ESCOPOS].sort(),
    ['ANALISES_CRIAR', 'ANALISES_LER', 'CALCULAR', 'CLASSIFICAR', 'NOTAS_ENVIAR', 'NOTAS_LER'])
  assert.deepEqual(FORM_CHAVE_VAZIO.escopos, TODOS_OS_ESCOPOS)
  // os que gastam a cota de IA ficam sinalizados
  assert.deepEqual(ESCOPOS.filter((e) => e.usaIa).map((e) => e.id).sort(), ['ANALISES_CRIAR', 'CLASSIFICAR', 'NOTAS_ENVIAR'])
})

test('formulário válido não tem erros; empresa, nome e ao menos uma permissão são obrigatórios', () => {
  assert.deepEqual(validarFormChave(valido()), {})
  const e = validarFormChave(valido({ clienteId: '', nomeIntegrador: '   ', escopos: [] }))
  assert.ok(e.clienteId && e.nomeIntegrador && e.escopos)
  assert.ok(validarFormChave(valido({ nomeIntegrador: 'x'.repeat(101) })).nomeIntegrador)
})

test('limites opcionais: vazio usa o padrão; fora da faixa ou não inteiro é erro', () => {
  assert.deepEqual(validarFormChave(valido({ cotaDiariaItensIa: '', validadeDias: '' })), {})
  assert.deepEqual(validarFormChave(valido({ cotaDiariaItensIa: '500', validadeDias: '30' })), {})
  assert.ok(validarFormChave(valido({ cotaDiariaItensIa: '0' })).cotaDiariaItensIa)
  assert.ok(validarFormChave(valido({ maxAnalisesSimultaneas: '101' })).maxAnalisesSimultaneas)
  assert.ok(validarFormChave(valido({ requisicoesPorMinuto: '1.5' })).requisicoesPorMinuto)
  assert.ok(validarFormChave(valido({ validadeDias: 'abc' })).validadeDias)
})

test('corpo da emissão: números convertidos, vazios omitidos (o servidor aplica os padrões)', () => {
  const c = corpoNovaChave(valido({ nomeIntegrador: '  ERP  ', cotaDiariaItensIa: '800', escopos: ['NOTAS_LER'] }))
  assert.equal(c.clienteId, 1)
  assert.equal(c.nomeIntegrador, 'ERP')
  assert.equal(c.cotaDiariaItensIa, 800)
  assert.equal(c.validadeDias, undefined)
  assert.deepEqual(c.escopos, ['NOTAS_LER'])
  assert.equal(JSON.stringify(c).includes('validadeDias'), false, 'vazio não vai no JSON')
})

test('resumo das permissões e exemplo de uso sem chave embutida', () => {
  assert.equal(resumoEscopos(TODOS_OS_ESCOPOS), 'Todas as permissões')
  assert.equal(resumoEscopos(['CALCULAR']), 'Simular o cálculo de 2027')
  const ex = exemploDeUso('https://trib-ai.onrender.com/')
  assert.ok(ex.includes('https://trib-ai.onrender.com/api/v1/uso'))
  assert.ok(ex.includes('$TRIBIA_API_KEY'))
  assert.ok(!/tribia_[0-9a-f]{12}_/.test(ex), 'o exemplo nunca traz uma chave real')
})

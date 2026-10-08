import { useState, type FormEvent } from 'react'
import { LoaderCircle } from 'lucide-react'
import { ApiError } from '../../api/client'
import { atualizarCliente, criarCliente } from '../../api/tribia'
import type { Cliente, ClienteForm } from '../../api/types'
import { cnpjValido, mascararCnpj } from '../../lib/cnpj'
import { Aviso } from '../ui'

const VAZIO: ClienteForm = {
  razaoSocial: '', nomeFantasia: '', cnpj: '', regime: '', setor: '', uf: '', municipio: '',
  codigoMunicipio: '', email: '', telefone: '', responsavel: '', observacoes: '',
}

function doCliente(c: Cliente): ClienteForm {
  return {
    razaoSocial: c.razaoSocial, nomeFantasia: c.nomeFantasia ?? '', cnpj: mascararCnpj(c.cnpj), regime: c.regime,
    setor: c.setor ?? '', uf: c.uf ?? '', municipio: c.municipio ?? '', codigoMunicipio: c.codigoMunicipio ?? '',
    email: c.email ?? '', telefone: c.telefone ?? '', responsavel: c.responsavel ?? '', observacoes: c.observacoes ?? '',
  }
}

function validar(f: ClienteForm) {
  const e: Partial<Record<keyof ClienteForm, string>> = {}
  if (!f.razaoSocial.trim()) e.razaoSocial = 'Informe a razão social.'
  if (!f.cnpj.trim()) e.cnpj = 'Informe o CNPJ.'
  else if (!cnpjValido(f.cnpj)) e.cnpj = 'CNPJ inválido. Confira os 14 dígitos.'
  if (!f.regime) e.regime = 'Informe o regime de apuração.'
  if (f.uf && !/^[A-Za-z]{2}$/.test(f.uf)) e.uf = 'A UF deve ter 2 letras.'
  if (f.codigoMunicipio && !/^\d{7}$/.test(f.codigoMunicipio)) e.codigoMunicipio = 'O código IBGE tem 7 dígitos.'
  if (f.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email)) e.email = 'E-mail inválido.'
  return e
}

/**
 * Cadastro/edição de empresa. Obrigatórios: razão social, CNPJ e regime (usados pelas regras do sistema).
 * O CNPJ fica bloqueado quando a empresa já tem notas: é por ele que as notas são vinculadas.
 */
export function EmpresaForm({ empresa, cnpjBloqueado, onSalvo, onCancelar, idForm = 'form-empresa' }: {
  empresa?: Cliente
  cnpjBloqueado?: boolean
  onSalvo: (c: Cliente) => void
  onCancelar?: () => void
  idForm?: string
}) {
  const [form, setForm] = useState<ClienteForm>(empresa ? doCliente(empresa) : VAZIO)
  const [tocados, setTocados] = useState<Set<keyof ClienteForm>>(new Set())
  const [servidor, setServidor] = useState<Partial<Record<string, string>>>({})
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  // erros do servidor que ainda valem + validação local (que tem prioridade)
  const erros: Partial<Record<string, string>> = {
    ...Object.fromEntries(Object.entries(servidor).filter(([, v]) => v)),
    ...validar(form),
  }
  const mostra = (k: keyof ClienteForm) => (tocados.has(k) ? erros[k] : undefined)
  const set = (k: keyof ClienteForm, v: string) => {
    setForm((f) => ({ ...f, [k]: v }))
    setServidor((s) => ({ ...s, [k]: undefined }))
  }
  const toca = (k: keyof ClienteForm) => setTocados((t) => new Set(t).add(k))

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    setTocados(new Set(Object.keys(form) as (keyof ClienteForm)[]))
    if (Object.keys(validar(form)).length) return
    setSalvando(true)
    setErro(null)
    try {
      const salvo = empresa ? await atualizarCliente(empresa.id, form) : await criarCliente(form)
      onSalvo(salvo)
    } catch (err) {
      if (err instanceof ApiError && err.problem?.campos) setServidor(err.problem.campos)
      setErro(err instanceof Error ? err.message : 'Não foi possível salvar.')
    } finally {
      setSalvando(false)
    }
  }

  const campo = (k: keyof ClienteForm, rotulo: string, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <div className="field">
      <label className="field__label" htmlFor={`${idForm}-${k}`}>{rotulo}</label>
      <input
        id={`${idForm}-${k}`}
        className={`input${mostra(k) ? ' input--erro' : ''}`}
        value={form[k]}
        onChange={(e) => set(k, e.target.value)}
        onBlur={() => toca(k)}
        aria-invalid={!!mostra(k)}
        disabled={salvando}
        {...props}
      />
      {mostra(k) && <span className="field__erro">{mostra(k)}</span>}
    </div>
  )

  return (
    <form id={idForm} onSubmit={enviar} noValidate className="form-empresa">
      {erro && <Aviso tipo="error">{erro}</Aviso>}

      <fieldset>
        <legend>Identificação</legend>
        <div className="form-grid">
          <div className="span-2">{campo('razaoSocial', 'Razão social *', { autoFocus: !empresa, maxLength: 255 })}</div>
          {campo('nomeFantasia', 'Nome fantasia', { maxLength: 255 })}
          <div className="field">
            <label className="field__label" htmlFor={`${idForm}-cnpj`}>CNPJ *</label>
            <input
              id={`${idForm}-cnpj`}
              className={`input num${mostra('cnpj') ? ' input--erro' : ''}`}
              value={form.cnpj}
              inputMode="numeric"
              placeholder="00.000.000/0000-00"
              onChange={(e) => set('cnpj', mascararCnpj(e.target.value))}
              onBlur={() => toca('cnpj')}
              disabled={salvando || cnpjBloqueado}
              aria-invalid={!!mostra('cnpj')}
            />
            {mostra('cnpj') ? (
              <span className="field__erro">{mostra('cnpj')}</span>
            ) : cnpjBloqueado ? (
              <span className="cell-sub">Não pode ser alterado: a empresa já tem notas importadas.</span>
            ) : (
              <span className="cell-sub">As notas são vinculadas à empresa por este CNPJ.</span>
            )}
          </div>
          <div className="field">
            <label className="field__label" htmlFor={`${idForm}-regime`}>Regime de apuração *</label>
            <select
              id={`${idForm}-regime`}
              className={`select${mostra('regime') ? ' input--erro' : ''}`}
              value={form.regime}
              onChange={(e) => set('regime', e.target.value)}
              onBlur={() => toca('regime')}
              disabled={salvando}
            >
              <option value="">Selecione</option>
              <option value="LUCRO_REAL">Lucro Real</option>
              <option value="LUCRO_PRESUMIDO">Lucro Presumido</option>
            </select>
            {mostra('regime') && <span className="field__erro">{mostra('regime')}</span>}
          </div>
          {campo('setor', 'Setor', { maxLength: 255, placeholder: 'Ex.: Varejo de alimentos' })}
        </div>
      </fieldset>

      <fieldset>
        <legend>Localização</legend>
        <div className="form-grid form-grid--3">
          {campo('municipio', 'Município', { maxLength: 255 })}
          {campo('uf', 'UF', { maxLength: 2, style: { textTransform: 'uppercase' } })}
          {campo('codigoMunicipio', 'Código IBGE', { maxLength: 7, inputMode: 'numeric' })}
        </div>
      </fieldset>

      <fieldset>
        <legend>Contato</legend>
        <div className="form-grid">
          {campo('responsavel', 'Responsável', { maxLength: 255 })}
          {campo('email', 'E-mail', { type: 'email', maxLength: 255 })}
          {campo('telefone', 'Telefone', { type: 'tel', maxLength: 30 })}
        </div>
        <div className="field" style={{ marginTop: 14 }}>
          <label className="field__label" htmlFor={`${idForm}-observacoes`}>Observações</label>
          <textarea
            id={`${idForm}-observacoes`}
            className="input textarea"
            rows={3}
            maxLength={2000}
            value={form.observacoes}
            onChange={(e) => set('observacoes', e.target.value)}
            disabled={salvando}
          />
        </div>
      </fieldset>

      <div className="form-acoes">
        {onCancelar && (
          <button type="button" className="btn btn--secondary" onClick={onCancelar} disabled={salvando}>
            Cancelar
          </button>
        )}
        <button type="submit" className="btn btn--primary" disabled={salvando}>
          {salvando && <LoaderCircle size={16} className="spin" />}
          {empresa ? 'Salvar alterações' : 'Cadastrar empresa'}
        </button>
      </div>
    </form>
  )
}

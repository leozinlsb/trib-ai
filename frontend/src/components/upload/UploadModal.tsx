import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { CircleCheck, CircleX, FileCode2, LoaderCircle, Trash2, UploadCloud } from 'lucide-react'
import { Aviso, Modal } from '../ui'
import { useAtividades, useDados, usePreferencias, type Atividade } from '../../state/contexts'
import { fmtBytes, fmtCnpj, nomeCliente, REGIME_LABEL, TIPO_LABEL } from '../../lib/format'

const MAX_ARQUIVO = 10 * 1024 * 1024 // spring.servlet.multipart.max-file-size
const MAX_TOTAL = 50 * 1024 * 1024 // spring.servlet.multipart.max-request-size

/** Upload real: POST /api/clientes/{id}/notas (multipart "arquivos"). */
export function UploadModal({ clienteInicial, travado, onFechar }: {
  clienteInicial?: number
  travado?: boolean
  onFechar: () => void
}) {
  const { clientes: todos } = useDados()
  // só empresas ativas recebem notas
  const clientes = todos.filter((c) => c.ativo)
  const { enviar } = useAtividades()
  const { prefs } = usePreferencias()
  const padrao = [clienteInicial, prefs.clientePadraoId].find((id) => id != null && clientes.some((c) => c.id === id))
  const [clienteId, setClienteId] = useState<number | ''>(padrao ?? clientes[0]?.id ?? '')
  const [arquivos, setArquivos] = useState<File[]>([])
  const [sobre, setSobre] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<Atividade | null>(null)
  const input = useRef<HTMLInputElement>(null)

  const cliente = clientes.find((c) => c.id === clienteId)
  const total = arquivos.reduce((s, f) => s + f.size, 0)
  const grandes = arquivos.filter((f) => f.size > MAX_ARQUIVO)
  const naoXml = arquivos.filter((f) => !f.name.toLowerCase().endsWith('.xml'))
  const bloqueado = grandes.length > 0 || total > MAX_TOTAL

  const adicionar = (lista: FileList | null) => {
    if (!lista) return
    // copia já: o FileList do input é esvaziado quando o valor do input é limpo
    const novos = [...lista]
    setResultado(null)
    setArquivos((atual) => {
      const nomes = new Set(atual.map((f) => f.name + f.size))
      return [...atual, ...novos.filter((f) => !nomes.has(f.name + f.size))]
    })
  }

  const submeter = async () => {
    if (!clienteId || arquivos.length === 0) return
    setEnviando(true)
    const r = await enviar(clienteId, arquivos)
    setEnviando(false)
    setResultado(r.atividade)
    if (r.ok) setArquivos([])
  }

  return (
    <Modal
      titulo="Enviar notas fiscais"
      sub="Envie os XMLs das NF-e (modelo 55). O TribIA identifica se cada nota é uma compra ou uma venda da empresa."
      onFechar={onFechar}
      travado={enviando}
      rodape={
        <>
          <button className="btn btn--secondary" onClick={onFechar} disabled={enviando}>
            {resultado ? 'Fechar' : 'Cancelar'}
          </button>
          <button
            className="btn btn--primary"
            onClick={submeter}
            disabled={enviando || !clienteId || arquivos.length === 0 || bloqueado}
          >
            {enviando ? <LoaderCircle size={17} className="spin" /> : <UploadCloud size={17} />}
            {enviando ? 'Processando...' : `Enviar ${arquivos.length || ''} arquivo${arquivos.length === 1 ? '' : 's'}`}
          </button>
        </>
      }
    >
      <div className="field">
        <label className="field__label" htmlFor="up-cliente">Empresa</label>
        {travado && cliente ? (
          <div className="input" id="up-cliente" style={{ display: 'flex', alignItems: 'center', background: 'var(--surface-2)' }}>
            {nomeCliente(cliente)} — {fmtCnpj(cliente.cnpj)}
          </div>
        ) : (
          <select
            id="up-cliente"
            className="select"
            value={clienteId}
            onChange={(e) => setClienteId(Number(e.target.value))}
            disabled={enviando}
          >
            {clientes.length === 0 && <option value="">Nenhuma empresa ativa</option>}
            {clientes.map((c) => (
              <option key={c.id} value={c.id}>
                {nomeCliente(c)} — {fmtCnpj(c.cnpj)}
              </option>
            ))}
          </select>
        )}
        {cliente && (
          <span className="cell-sub">
            {cliente.razaoSocial} · {REGIME_LABEL[cliente.regime]} · {cliente.municipio}/{cliente.uf}
          </span>
        )}
      </div>

      <div
        className={`dropzone${sobre ? ' is-over' : ''}`}
        role="button"
        tabIndex={0}
        onClick={() => input.current?.click()}
        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && input.current?.click()}
        onDragOver={(e) => {
          e.preventDefault()
          setSobre(true)
        }}
        onDragLeave={() => setSobre(false)}
        onDrop={(e) => {
          e.preventDefault()
          setSobre(false)
          adicionar(e.dataTransfer.files)
        }}
        aria-label="Selecionar arquivos XML"
      >
        <UploadCloud size={28} color="var(--green-600)" />
        <div style={{ marginTop: 8 }}>
          <strong>Arraste os XMLs aqui</strong> ou clique para selecionar
        </div>
        <div style={{ fontSize: 12.5, marginTop: 4 }}>Até 10 MB por arquivo e 50 MB por envio</div>
        <input
          ref={input}
          type="file"
          accept=".xml,text/xml,application/xml"
          multiple
          hidden
          onChange={(e) => {
            adicionar(e.target.files)
            e.target.value = ''
          }}
        />
      </div>

      {arquivos.length > 0 && (
        <ul className="file-list" aria-label="Arquivos selecionados">
          {arquivos.map((f) => (
            <li key={f.name + f.size}>
              <FileCode2 size={17} color="var(--navy-700)" />
              <span className="grow" title={f.name}>{f.name}</span>
              <span className="cell-sub" style={{ color: f.size > MAX_ARQUIVO ? 'var(--danger)' : undefined }}>
                {fmtBytes(f.size)}
              </span>
              <button
                className="icon-btn"
                style={{ width: 28, height: 28 }}
                onClick={() => setArquivos((a) => a.filter((x) => x !== f))}
                disabled={enviando}
                aria-label={`Remover ${f.name}`}
              >
                <Trash2 size={15} />
              </button>
            </li>
          ))}
        </ul>
      )}

      {grandes.length > 0 && (
        <Aviso tipo="error">Há arquivo(s) acima de 10 MB, o limite permitido. Remova-os para enviar.</Aviso>
      )}
      {total > MAX_TOTAL && <Aviso tipo="error">O envio passa de 50 MB, o limite permitido. Envie os arquivos em partes.</Aviso>}
      {naoXml.length > 0 && !bloqueado && (
        <Aviso tipo="warn">
          {naoXml.length} arquivo(s) sem extensão .xml. Se não forem XMLs de NF-e, serão recusados.
        </Aviso>
      )}

      {resultado && <ResultadoUpload r={resultado} onFechar={onFechar} />}
    </Modal>
  )
}

function ResultadoUpload({ r, onFechar }: { r: Atividade; onFechar: () => void }) {
  return (
    <div className="stack" style={{ gap: 10 }} aria-live="polite">
      {r.importadas.length > 0 && (
        <div className="notice" style={{ borderColor: '#bfe5d2', background: '#f1faf5' }}>
          <CircleCheck size={17} color="var(--green-600)" />
          <div>
            <strong>{r.importadas.length} nota(s) importada(s)</strong>
            <ul style={{ margin: '6px 0 0', paddingLeft: 18 }}>
              {r.importadas.map((n) => (
                <li key={n.id}>
                  <Link to={`/dashboard/documentos/${n.id}`} onClick={onFechar}>
                    NF-e {n.numero ?? 's/n'}
                  </Link>{' '}
                  · {TIPO_LABEL[n.tipo]}
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
      {r.rejeitadas.length > 0 && (
        <div className="notice notice--error">
          <CircleX size={17} />
          <div>
            <strong>{r.rejeitadas.length} arquivo(s) recusado(s)</strong>
            <ul style={{ margin: '6px 0 0', paddingLeft: 18 }}>
              {r.rejeitadas.map((x, i) => (
                <li key={i}>
                  <span className="mono">{x.arquivo}</span> — {x.motivo}{' '}
                  <span className="muted">({x.status === 409 ? 'já importada' : 'recusada'})</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
      {r.erro && <Aviso tipo="error">{r.erro}</Aviso>}
    </div>
  )
}

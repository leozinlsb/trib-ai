import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react'
import { CircleAlert, CircleCheck, Info, X } from 'lucide-react'
import { ToastContext, type Toast } from './contexts'

const DURACAO = 6000

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const seq = useRef(0)

  const fechar = useCallback((id: number) => setToasts((ts) => ts.filter((t) => t.id !== id)), [])

  const mostrar = useCallback(
    (t: Omit<Toast, 'id'>) => {
      const id = ++seq.current
      setToasts((ts) => [...ts.slice(-3), { ...t, id }])
      window.setTimeout(() => fechar(id), DURACAO)
    },
    [fechar],
  )

  const valor = useMemo(() => ({ mostrar }), [mostrar])

  return (
    <ToastContext value={valor}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast ${t.tipo === 'erro' ? 'toast--error' : t.tipo === 'info' ? 'toast--info' : ''}`}>
            {t.tipo === 'sucesso' && <CircleCheck size={18} color="var(--green-600)" />}
            {t.tipo === 'erro' && <CircleAlert size={18} color="var(--danger)" />}
            {t.tipo === 'info' && <Info size={18} color="var(--navy-700)" />}
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="toast__title">{t.titulo}</div>
              {t.texto && <div style={{ color: 'var(--text-3)', marginTop: 2 }}>{t.texto}</div>}
            </div>
            <button className="icon-btn" style={{ width: 24, height: 24 }} onClick={() => fechar(t.id)} aria-label="Fechar aviso">
              <X size={14} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext>
  )
}

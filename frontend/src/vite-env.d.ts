/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string
  /** "true" mostra a Inteligência Fiscal no menu (ainda sem backend) */
  readonly VITE_INTELIGENCIA_FISCAL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

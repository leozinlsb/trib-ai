/**
 * Logotipo TribIA (arte oficial em frontend/images/logo.png; variantes com fundo transparente em public/brand/).
 * fundo="escuro" (sidebar/painel azul): versão branca. fundo="claro" (landing, fundo branco): versão azul-marinho.
 * `tamanho` é a altura em pixels.
 */
export function Logo({ tamanho = 40, fundo = 'escuro' }: { tamanho?: number; fundo?: 'escuro' | 'claro' }) {
  return (
    <img
      src={fundo === 'escuro' ? '/brand/logo-escuro.png' : '/brand/logo-claro.png'}
      alt="TribIA"
      height={Math.round(tamanho * 1.3)}
      style={{ display: 'block', height: tamanho * 1.3, width: 'auto' }}
      draggable={false}
    />
  )
}

package br.com.tribia.security;

import br.com.tribia.model.Papel;

import java.util.function.Supplier;

/**
 * Executa um trecho do motor da plataforma (importar nota, classificar, calcular, painel) em nome de UMA empresa, a
 * pedido da API pública. Dentro do bloco, o {@link AcessoService} enxerga um usuário de empresa (papel EMPRESA) preso
 * a essa empresa: as mesmas regras que protegem o site valem para a integração (outra empresa = 404).
 *
 * Fora do bloco nada muda: o principal da API pública ({@code IntegradorAutenticado}) continua recusado pelo
 * AcessoService. O escopo é por thread e sempre removido no fim (inclusive em erro), então não vaza para outra
 * requisição nem para outra tarefa da mesma thread do pool. A empresa vem sempre da chave de API conferida, nunca do
 * corpo da requisição.
 *
 * Não é bean e não abre transação: quem chama decide as transações (o AcessoService é readOnly e não pode envolver
 * a importação de uma nota).
 */
public final class EscopoIntegracao {

    private static final ThreadLocal<UsuarioLogado> ATUAL = new ThreadLocal<>();

    private EscopoIntegracao() {
    }

    /** Usuário técnico da integração: sem id (não existe na tabela de usuários), papel EMPRESA, só esta empresa. */
    static UsuarioLogado usuario(Long clienteId) {
        return new UsuarioLogado(null, "Integração (API pública)", "integracao@api.tribia", null, Papel.EMPRESA, clienteId);
    }

    public static <T> T executar(Long clienteId, Supplier<T> acao) {
        if (clienteId == null) {
            throw new IllegalArgumentException("Escopo de integração exige a empresa da chave");
        }
        UsuarioLogado anterior = ATUAL.get();
        ATUAL.set(usuario(clienteId));
        try {
            return acao.get();
        } finally {
            if (anterior == null) {
                ATUAL.remove();
            } else {
                ATUAL.set(anterior);
            }
        }
    }

    public static void executar(Long clienteId, Runnable acao) {
        executar(clienteId, () -> {
            acao.run();
            return null;
        });
    }

    /** Usuário de integração ativo nesta thread; null fora de um bloco {@link #executar}. */
    static UsuarioLogado ativo() {
        return ATUAL.get();
    }
}

package br.com.tribia.service.fiscal;

import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Pontuacao;

import java.util.List;
import java.util.Map;

/**
 * Ponto de encaixe da JEV AI: dá uma pontuação de compatibilidade entre a mercadoria e cada NCM candidata.
 *
 * Para plugar a JEV, basta criar um bean (@Component) que implemente esta interface: ele substitui o
 * {@link JevIndisponivel}, que é usado só enquanto não houver outro. Regras do contrato com o front:
 * <ul>
 *   <li>A pontuação mede compatibilidade, não é probabilidade de acerto; diga isso em {@code significado}.</li>
 *   <li>Informe a escala (ex.: "0 a 1"); o front mostra valor + escala e nunca converte em porcentagem.</li>
 *   <li>Rode no servidor: chaves e chamadas da JEV nunca vão para o navegador.</li>
 *   <li>Falha ou indisponibilidade: lance {@link JevIndisponivelException}; a análise segue sem pontuação.</li>
 * </ul>
 */
public interface AvaliadorJev {

    /** false enquanto a JEV não estiver integrada (a análise registra a limitação). */
    boolean disponivel();

    /** true só no modo de desenvolvimento ({@link JevSimulado}): a análise avisa que as pontuações são fictícias. */
    default boolean simulado() {
        return false;
    }

    /**
     * @param mercadoria  dados informados pela pessoa e características interpretadas
     * @param candidatas  NCMs candidatas (8 dígitos) com a descrição usada na análise
     * @return pontuação por NCM; NCM ausente no mapa fica sem pontuação
     */
    Map<String, Pontuacao> avaliar(MercadoriaParaJev mercadoria, List<Candidata> candidatas);

    record MercadoriaParaJev(String nome, String descricao, String composicao, String finalidade,
                             String caracteristicasInformadas, List<String> caracteristicasInterpretadas) {
    }

    record Candidata(String ncm, String descricao) {
    }

    class JevIndisponivelException extends RuntimeException {
        public JevIndisponivelException(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }
}

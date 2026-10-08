package br.com.tribia.service.fiscal;

import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Pontuacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modo de desenvolvimento (tribia.jev.modo=SIMULADO): pontuações FICTÍCIAS, determinísticas, para exercitar as telas
 * sem chamar a JEV. Nunca é integração real: o significado de cada pontuação diz que é simulação, a análise registra a
 * limitação e o profile prod recusa subir com este modo.
 */
public class JevSimulado implements AvaliadorJev {

    static final String SIGNIFICADO = "SIMULAÇÃO de desenvolvimento: valor fictício, não é avaliação da JEV AI "
            + "e não tem significado fiscal.";

    @Override
    public boolean disponivel() {
        return true;
    }

    @Override
    public boolean simulado() {
        return true;
    }

    @Override
    public Map<String, Pontuacao> avaliar(MercadoriaParaJev mercadoria, List<Candidata> candidatas) {
        Map<String, Pontuacao> m = new LinkedHashMap<>();
        for (int i = 0; i < candidatas.size(); i++) {
            // decrescente pela ordem das candidatas: só para a tela ter valores distintos
            BigDecimal v = BigDecimal.valueOf(Math.max(0.05, 0.80 - 0.15 * i)).setScale(2, RoundingMode.HALF_EVEN);
            m.put(candidatas.get(i).ncm(), new Pontuacao(v, JevHttp.ESCALA, SIGNIFICADO));
        }
        return m;
    }
}

package br.com.tribia.apipublica;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites padrão da API pública v1 (cada chave pode ter os seus; nulos usam estes).
 *
 * @param requisicoesPorMinuto          requisições por chave por minuto (todas as rotas /api/v1)
 * @param cotaDiariaAnalises            análises novas por chave por dia (fuso de Brasília); repetição idempotente não conta
 * @param maxAnalisesSimultaneas        análises (e envios de nota) da chave ainda em processamento ao mesmo tempo
 * @param cotaDiariaItensIa             itens de NF-e enviados à IA de classificação por chave por dia (Brasília);
 *                                      itens resolvidos pelo XML ou pelo cache não contam
 * @param falhasAutenticacaoPorMinuto   chaves ausentes/inválidas aceitas por endereço IP por minuto antes de 429
 * @param validadeMaximaDias            limite de validade que o administrador pode dar a uma chave (0 = sem limite)
 */
@ConfigurationProperties(prefix = "tribia.api-publica")
public record ApiPublicaProperties(Integer requisicoesPorMinuto, Integer cotaDiariaAnalises,
                                   Integer maxAnalisesSimultaneas, Integer falhasAutenticacaoPorMinuto,
                                   Integer validadeMaximaDias, Integer cotaDiariaItensIa) {

    public ApiPublicaProperties {
        requisicoesPorMinuto = positivo(requisicoesPorMinuto, 60);
        cotaDiariaAnalises = positivo(cotaDiariaAnalises, 100);
        maxAnalisesSimultaneas = positivo(maxAnalisesSimultaneas, 5);
        falhasAutenticacaoPorMinuto = positivo(falhasAutenticacaoPorMinuto, 20);
        cotaDiariaItensIa = positivo(cotaDiariaItensIa, 500);
        validadeMaximaDias = validadeMaximaDias == null || validadeMaximaDias < 0 ? 365 : validadeMaximaDias;
    }

    private static Integer positivo(Integer valor, int padrao) {
        return valor == null || valor < 1 ? padrao : valor;
    }
}

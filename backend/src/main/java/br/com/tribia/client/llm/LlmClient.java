package br.com.tribia.client.llm;

import java.util.Map;

/** Gera uma resposta em JSON a partir de instruções e de um pedido. */
public interface LlmClient {

    /**
     * @param instrucoes instruções fixas (papel, regras, opções válidas)
     * @param pedido     o que classificar agora
     * @param esquema    esquema JSON da resposta (subconjunto OpenAPI, tipos em maiúsculas: ARRAY, OBJECT, STRING...)
     * @return o texto JSON devolvido pelo modelo
     * @throws LlmException se não estiver configurado, estiver fora do ar ou devolver algo inutilizável
     */
    String gerarJson(String instrucoes, String pedido, Map<String, Object> esquema);
}

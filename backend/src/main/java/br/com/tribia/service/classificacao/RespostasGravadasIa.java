package br.com.tribia.service.classificacao;

import br.com.tribia.util.ChaveClassificacao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plano B da apresentação (profile demo): respostas que o Gemini REAL deu para os produtos das notas do upload ao
 * vivo, gravadas antes em demo/respostas-ia.json (GerarRespostasIaDemoTest). Só são usadas quando a IA falha
 * (sem internet, sem cota) e com tribia.demo.respostas-ia=true; a resposta da API avisa quando isso acontece.
 */
@Component
public class RespostasGravadasIa {

    private static final Logger log = LoggerFactory.getLogger(RespostasGravadasIa.class);
    static final String ARQUIVO = "demo/respostas-ia.json";

    public record Resposta(String ncm, String descricao, String cst, String cClassTrib, String justificativa,
                           BigDecimal confianca) {
    }

    private final boolean habilitadas;
    private final Map<String, Resposta> porProduto;

    public RespostasGravadasIa(@Value("${tribia.demo.respostas-ia:false}") boolean habilitadas, ObjectMapper json) {
        this.habilitadas = habilitadas;
        this.porProduto = habilitadas ? carregar(json) : Map.of();
    }

    public boolean habilitadas() {
        return habilitadas && !porProduto.isEmpty();
    }

    public Optional<Resposta> buscar(String ncm, String descricao) {
        return Optional.ofNullable(porProduto.get(ChaveClassificacao.de(ncm, descricao)));
    }

    public int quantidade() {
        return porProduto.size();
    }

    private static Map<String, Resposta> carregar(ObjectMapper json) {
        ClassPathResource r = new ClassPathResource(ARQUIVO);
        if (!r.exists()) {
            log.warn("Demo: {} não encontrado; sem respostas gravadas da IA", ARQUIVO);
            return Map.of();
        }
        try (InputStream in = r.getInputStream()) {
            Map<String, Resposta> m = new LinkedHashMap<>();
            for (Resposta resp : List.of(json.readValue(in, Resposta[].class))) {
                m.put(ChaveClassificacao.de(resp.ncm(), resp.descricao()), resp);
            }
            log.info("Demo: {} respostas gravadas da IA disponíveis como plano B", m.size());
            return m;
        } catch (IOException e) {
            log.warn("Demo: {} ilegível ({}); sem respostas gravadas da IA", ARQUIVO, e.getMessage());
            return Map.of();
        }
    }
}

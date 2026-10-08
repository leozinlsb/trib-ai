package br.com.tribia.service.classificacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.tribia.security.AcessoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Carrega seed/classificacoes.json no catálogo público curado: as notas de demonstração são classificadas
 * na inicialização sem chamar a IA. Gerado pelo GerarArquivosSeedTest a partir das regras oficiais por NCM.
 */
@Component
public class ClassificacoesSeedLoader {

    private static final Logger log = LoggerFactory.getLogger(ClassificacoesSeedLoader.class);
    static final String ARQUIVO = "seed/classificacoes.json";

    public record Entrada(String ncm, String descricao, String cst, String cClassTrib, String justificativa,
                          BigDecimal confianca) {
    }

    private final ClassificacaoService classificacaoService;
    private final ObjectMapper json;
    private final AcessoService acesso;

    public ClassificacoesSeedLoader(ClassificacaoService classificacaoService, ObjectMapper json, AcessoService acesso) {
        this.classificacaoService = classificacaoService;
        this.json = json;
        this.acesso = acesso;
    }

    /** @return quantas entradas foram para o cache */
    public int carregar() {
        acesso.exigirAdmin();
        ClassPathResource r = new ClassPathResource(ARQUIVO);
        if (!r.exists()) {
            log.warn("Seed: {} não encontrado; o cache de classificação começa vazio", ARQUIVO);
            return 0;
        }
        List<Entrada> entradas;
        try (InputStream in = r.getInputStream()) {
            entradas = List.of(json.readValue(in, Entrada[].class));
        } catch (IOException e) {
            throw new UncheckedIOException("Seed: " + ARQUIVO + " ilegível", e);
        }
        int ok = 0;
        for (Entrada e : entradas) {
            try {
                classificacaoService.gravarNoCache(e.ncm(), e.descricao(), e.cst(), e.cClassTrib(), e.justificativa(),
                        e.confianca(), "SEED", true);
                ok++;
            } catch (IllegalArgumentException ex) {
                log.warn("Seed: classificação de '{}' ignorada: {}", e.descricao(), ex.getMessage());
            }
        }
        log.info("Seed: {} classificações carregadas no cache", ok);
        return ok;
    }
}

package br.com.tribia.service.demo;

import br.com.tribia.exception.ApiException;
import br.com.tribia.model.Cliente;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.security.AcessoService;
import br.com.tribia.service.NotaService;
import br.com.tribia.service.calculo.CalculoService;
import br.com.tribia.service.classificacao.ClassificacaoService;
import br.com.tribia.service.classificacao.ClassificacoesSeedLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Dados de demonstração (adendo, seção 7), para o painel não abrir vazio:
 * 1. carrega seed/classificacoes.json no cache global (sem chamar a IA);
 * 2. importa classpath:seed/{cnpjCliente}/*.xml pelo mesmo fluxo do upload ({@link NotaService#importar});
 * 3. classifica (só XML e cache) e calcula cada nota, se tribia.seed.calcular=true.
 * Os arquivos são gerados pelo GerarArquivosSeedTest. Usado na inicialização e no reinício da demo.
 */
@Service
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    private static final String PADRAO = "classpath*:seed/*/*.xml";

    /** @param origemCalculo CALCULADORA ou SIMPLIFICADA (da última nota calculada); null se não calculou */
    public record Resultado(int classificacoesNoCache, int notasImportadas, int notasComFalha, int itensSemClassificacao,
                            String origemCalculo) {
    }

    private final NotaService notaService;
    private final ClienteRepository clienteRepository;
    private final ClassificacoesSeedLoader classificacoesSeed;
    private final ClassificacaoService classificacaoService;
    private final CalculoService calculoService;
    private final boolean calcular;
    private final AcessoService acesso;

    public SeedService(NotaService notaService, ClienteRepository clienteRepository,
                       ClassificacoesSeedLoader classificacoesSeed, ClassificacaoService classificacaoService,
                       CalculoService calculoService, @Value("${tribia.seed.calcular:true}") boolean calcular,
                       AcessoService acesso) {
        this.notaService = notaService;
        this.clienteRepository = clienteRepository;
        this.classificacoesSeed = classificacoesSeed;
        this.classificacaoService = classificacaoService;
        this.calculoService = calculoService;
        this.calcular = calcular;
        this.acesso = acesso;
    }

    public Resultado carregar() {
        acesso.exigirAdmin();
        int noCache = classificacoesSeed.carregar();

        Resource[] arquivos;
        try {
            arquivos = new PathMatchingResourcePatternResolver().getResources(PADRAO);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // pasta do cliente + nome do arquivo (que começa pela data): ordem cronológica e ids estáveis
        Arrays.sort(arquivos, Comparator.comparing(SeedService::caminho));

        int falhas = 0;
        List<Long> notas = new ArrayList<>();
        for (Resource arquivo : arquivos) {
            String cnpj = pastaDoCliente(arquivo);
            Optional<Cliente> cliente = clienteRepository.findByCnpj(cnpj);
            if (cliente.isEmpty()) {
                log.warn("Seed: nenhum cliente com CNPJ {} para {}", cnpj, arquivo.getFilename());
                falhas++;
                continue;
            }
            try {
                notas.add(notaService.importar(cliente.get().getId(), arquivo.getContentAsByteArray()).getId());
            } catch (ApiException e) {
                log.warn("Seed: {} rejeitado: {}", arquivo.getFilename(), e.getMessage());
                falhas++;
            } catch (IOException e) {
                log.warn("Seed: {} ilegível: {}", arquivo.getFilename(), e.getMessage());
                falhas++;
            }
        }
        log.info("Seed: {} notas importadas, {} com falha", notas.size(), falhas);

        int pendentes = 0;
        String origem = null;
        if (calcular) {
            for (Long id : notas) {
                pendentes += classificacaoService.classificar(id, false).pendentes().size();
                try {
                    var r = calculoService.calcular(id, null);
                    origem = r.origem() == null ? origem : r.origem().name();
                } catch (ApiException e) {
                    log.warn("Seed: nota {} não calculada: {}", id, e.getMessage());
                }
            }
            log.info("Seed: {} notas classificadas e calculadas (origem: {}); {} itens sem classificação",
                    notas.size(), origem, pendentes);
        }
        return new Resultado(noCache, notas.size(), falhas, pendentes, origem);
    }

    private static String caminho(Resource r) {
        try {
            return r.getURL().toString();
        } catch (IOException e) {
            return String.valueOf(r.getFilename());
        }
    }

    /** .../seed/{cnpj}/arquivo.xml → {cnpj} */
    private static String pastaDoCliente(Resource r) {
        String[] partes = caminho(r).split("/");
        return partes[partes.length - 2];
    }
}

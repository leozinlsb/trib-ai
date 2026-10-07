package br.com.tribia.config;

import br.com.tribia.exception.ApiException;
import br.com.tribia.model.Cliente;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.service.NotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/**
 * Importa as notas de demonstração na inicialização, para o painel não abrir vazio (adendo, seção 7).
 * Lê classpath:seed/{cnpjCliente}/*.xml e usa o mesmo fluxo do upload ({@link NotaService#importar}).
 * Os XMLs são gerados pelo GerarArquivosSeedTest. Desligue com tribia.seed.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "tribia.seed.enabled", havingValue = "true", matchIfMissing = true)
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
    private static final String PADRAO = "classpath*:seed/*/*.xml";

    private final NotaService notaService;
    private final ClienteRepository clienteRepository;

    public SeedRunner(NotaService notaService, ClienteRepository clienteRepository) {
        this.notaService = notaService;
        this.clienteRepository = clienteRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource[] arquivos = new PathMatchingResourcePatternResolver().getResources(PADRAO);
        // pasta do cliente + nome do arquivo (que começa pela data): ordem cronológica e ids estáveis
        Arrays.sort(arquivos, Comparator.comparing(SeedRunner::caminho));

        int importadas = 0;
        int falhas = 0;
        for (Resource arquivo : arquivos) {
            String cnpj = pastaDoCliente(arquivo);
            Optional<Cliente> cliente = clienteRepository.findByCnpj(cnpj);
            if (cliente.isEmpty()) {
                log.warn("Seed: nenhum cliente com CNPJ {} para {}", cnpj, arquivo.getFilename());
                falhas++;
                continue;
            }
            try {
                notaService.importar(cliente.get().getId(), arquivo.getContentAsByteArray());
                importadas++;
            } catch (ApiException e) {
                log.warn("Seed: {} rejeitado: {}", arquivo.getFilename(), e.getMessage());
                falhas++;
            }
        }
        log.info("Seed: {} notas importadas, {} com falha", importadas, falhas);
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

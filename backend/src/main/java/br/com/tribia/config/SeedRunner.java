package br.com.tribia.config;

import br.com.tribia.service.demo.SeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Carrega os dados de demonstração ({@link SeedService}) na inicialização.
 *
 * Roda como SmartInitializingSingleton, e não como ApplicationRunner (o que o adendo sugeria): assim o seed termina
 * ANTES de o servidor web começar a aceitar requisições, e o front nunca vê o painel pela metade. Se o seed falhar,
 * a API sobe assim mesmo (vazia), com o erro no log. Desligue com tribia.seed.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "tribia.seed.enabled", havingValue = "true", matchIfMissing = true)
public class SeedRunner implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final SeedService seedService;

    public SeedRunner(SeedService seedService) {
        this.seedService = seedService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            seedService.carregar();
        } catch (RuntimeException e) {
            log.error("Seed: falhou; a API sobe sem os dados de demonstração", e);
        }
    }
}

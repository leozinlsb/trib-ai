package br.com.tribia.config;

import br.com.tribia.service.demo.SeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
    private final AdminSeeder adminSeeder;

    public SeedRunner(SeedService seedService, AdminSeeder adminSeeder) {
        this.seedService = seedService;
        this.adminSeeder = adminSeeder;
    }

    @Override
    public void afterSingletonsInstantiated() {
        var anterior = SecurityContextHolder.getContext();
        try {
            var principal = adminSeeder.principalInicializacao();
            var contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, principal.getAuthorities()));
            SecurityContextHolder.setContext(contexto);
            seedService.carregar();
        } catch (RuntimeException e) {
            log.error("Seed: falhou; a API sobe sem os dados de demonstração", e);
        } finally {
            SecurityContextHolder.setContext(anterior);
        }
    }
}

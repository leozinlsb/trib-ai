package br.com.tribia.config;

import br.com.tribia.model.Papel;
import br.com.tribia.security.UsuarioLogado;
import br.com.tribia.service.demo.SeedService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeedRunnerAutorizacaoTest {
    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void inicializaComAdminERestauraContextoMesmoQuandoFalha(boolean falhar) {
        var anterior = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(anterior);
        var admin = new UsuarioLogado(1L, "Admin", "admin@test.local", null, Papel.ADMIN, null);
        var seeder = mock(AdminSeeder.class);
        var seed = mock(SeedService.class);
        when(seeder.principalInicializacao()).thenReturn(admin);
        when(seed.carregar()).thenAnswer(i -> {
            assertSame(admin, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
            if (falhar) throw new IllegalStateException("Falha simulada do seed");
            return null;
        });
        new SeedRunner(seed, seeder).afterSingletonsInstantiated();
        verify(seed).carregar();
        assertSame(anterior, SecurityContextHolder.getContext());
        assertNull(anterior.getAuthentication(), "Inicialização não deve deixar ADMIN na thread nem criar sessão");
    }
}

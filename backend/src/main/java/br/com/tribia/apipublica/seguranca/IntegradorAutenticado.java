package br.com.tribia.apipublica.seguranca;

import br.com.tribia.apipublica.model.EscopoApi;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Quem chamou a API pública: a chave de API conferida e a empresa à qual ela está presa. Vive só na requisição
 * (nada de sessão). Não é um {@code UsuarioLogado}: as rotas internas, que dependem do AcessoService, recusam este
 * principal.
 *
 * @param clienteId empresa da chave; a única que a requisição enxerga
 */
public record IntegradorAutenticado(Long chaveId, String prefixo, String nomeIntegrador, Long clienteId,
                                    Set<EscopoApi> escopos, int requisicoesPorMinuto, int cotaDiariaAnalises,
                                    int maxAnalisesSimultaneas) {

    public boolean pode(EscopoApi escopo) {
        return escopos.contains(escopo);
    }

    /** O integrador da requisição atual (o filtro da chave já garantiu que existe). */
    public static IntegradorAutenticado atual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a instanceof Autenticacao t) {
            return t.getPrincipal();
        }
        throw new IllegalStateException("Requisição da API pública sem chave autenticada");
    }

    /** Token do Spring Security para o integrador; autoridades: ROLE_INTEGRADOR e SCOPE_&lt;escopo&gt;. */
    public static final class Autenticacao extends AbstractAuthenticationToken {
        private final IntegradorAutenticado integrador;

        public Autenticacao(IntegradorAutenticado integrador) {
            super(Stream.concat(Stream.of("ROLE_INTEGRADOR"), integrador.escopos().stream().map(e -> "SCOPE_" + e.name()))
                    .map(SimpleGrantedAuthority::new).toList());
            this.integrador = integrador;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null; // a chave nunca fica guardada no contexto
        }

        @Override
        public IntegradorAutenticado getPrincipal() {
            return integrador;
        }

        @Override
        public String getName() {
            return integrador.prefixo();
        }
    }
}

package br.com.tribia.apipublica.seguranca;

import br.com.tribia.apipublica.ApiPublicaProperties;
import br.com.tribia.apipublica.repository.ChaveApiRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Map;

/**
 * Cadeia de segurança só da API pública (/api/v1/**), avaliada antes da cadeia da plataforma (SecurityConfig).
 * Sem sessão e sem cookie: um usuário logado no navegador NÃO acessa a API pública com o cookie de sessão, e por isso
 * não há CSRF aqui (o ataque de CSRF depende de credencial enviada automaticamente pelo navegador; a chave vai num
 * cabeçalho que só o integrador põe). A chave de API, por sua vez, não vale nas rotas internas: elas ficam na outra
 * cadeia e dependem de UsuarioLogado.
 */
@Configuration
public class ApiPublicaSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain apiPublicaFilterChain(HttpSecurity http, ChaveApiRepository chaves,
                                              PlatformTransactionManager transacoes, ObjectMapper json,
                                              ApiPublicaProperties props) throws Exception {
        Clock relogio = Clock.systemUTC();
        FiltroRequestId requestId = new FiltroRequestId();
        FiltroChaveApi chave = new FiltroChaveApi(chaves, new TransactionTemplate(transacoes), json, props,
                new LimitadorRequisicoes(relogio), new LimitadorRequisicoes(relogio), relogio);
        http
                .securityMatcher("/api/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(s -> s.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(requestId, AnonymousAuthenticationFilter.class)
                .addFilterAfter(chave, FiltroRequestId.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/analises").hasAuthority("SCOPE_ANALISES_CRIAR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/notas").hasAuthority("SCOPE_NOTAS_ENVIAR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/classificacoes").hasAuthority("SCOPE_CLASSIFICAR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/classificacoes/**").hasAuthority("SCOPE_CLASSIFICAR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/calculos/simular").hasAuthority("SCOPE_CALCULAR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/notas", "/api/v1/notas/**", "/api/v1/comparativo")
                        .hasAuthority("SCOPE_NOTAS_LER")
                        // consumo da própria chave: qualquer escopo de leitura
                        .requestMatchers(HttpMethod.GET, "/api/v1/uso")
                        .hasAnyAuthority("SCOPE_ANALISES_LER", "SCOPE_NOTAS_LER", "SCOPE_CLASSIFICAR", "SCOPE_CALCULAR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAuthority("SCOPE_ANALISES_LER")
                        .anyRequest().hasRole("INTEGRADOR"))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> RespostaErroPublica.escrever(req, res, json,
                                HttpStatus.UNAUTHORIZED, "CHAVE_AUSENTE", "Chave de API ausente",
                                "Envie a chave de API no cabeçalho " + FiltroChaveApi.CABECALHO + ".", Map.of()))
                        .accessDeniedHandler((req, res, ex) -> RespostaErroPublica.escrever(req, res, json,
                                HttpStatus.FORBIDDEN, "ESCOPO_INSUFICIENTE", "Operação não permitida",
                                "Esta chave de API não tem permissão para esta operação.", Map.of())));
        return http.build();
    }
}

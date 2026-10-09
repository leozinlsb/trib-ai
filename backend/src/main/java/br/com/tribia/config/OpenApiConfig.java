package br.com.tribia.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dois documentos no Swagger (/swagger-ui.html, seletor no topo): "interna" (a plataforma, sessão + CSRF) e
 * "publica-v1" (integradores, chave no cabeçalho X-API-Key). O profile prod desliga os dois.
 */
@Configuration
public class OpenApiConfig {

    static final String ESQUEMA_CHAVE = "chaveApi";

    @Bean
    public OpenAPI tribiaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("TribIA API")
                .version("0.1.0")
                .description("Classificação de itens de NF-e (CST + cClassTrib) com IA e comparativo "
                        + "PIS/Cofins hoje x CBS/IBS/IS 2027, por cliente."));
    }

    @Bean
    public GroupedOpenApi apiInterna() {
        return GroupedOpenApi.builder()
                .group("interna")
                .displayName("Plataforma (interna)")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/v1/**")
                .build();
    }

    @Bean
    public GroupedOpenApi apiPublicaV1() {
        return GroupedOpenApi.builder()
                .group("publica-v1")
                .displayName("API pública v1 (integradores)")
                .pathsToMatch("/api/v1/**")
                .addOpenApiCustomizer(api -> {
                    api.info(new Info()
                            .title("TribIA — API pública")
                            .version("v1")
                            .description("""
                                    Inteligência Fiscal do TribIA para ERPs e sistemas contábeis: envie uma mercadoria e \
                                    receba a sugestão de NCM com fundamentação, alternativas, verificação na NCM vigente e \
                                    situação de revisão humana.

                                    **Autenticação:** cabeçalho `X-API-Key` com a chave emitida pelo administrador do \
                                    TribIA. A chave é presa a uma empresa: o integrador nunca escolhe a empresa.

                                    **Fluxo:** `POST /api/v1/analises` (202 + id) → `GET /api/v1/analises/{id}` até \
                                    `finalizada=true`.

                                    **Idempotência:** envie `Idempotency-Key`; repetir com o mesmo corpo devolve a mesma \
                                    análise sem nova chamada à IA.

                                    **Limites:** por minuto (cabeçalhos `X-RateLimit-*`), cota diária e análises \
                                    simultâneas por chave; excesso = 429 com `Retry-After`.

                                    **Erros:** `application/problem+json` (RFC 9457) com `codigo` estável e `requestId`.

                                    **Importante:** o resultado é sugestão de IA conferida contra a NCM vigente, não \
                                    classificação fiscal definitiva nem decisão da Receita Federal; a pontuação da JEV AI \
                                    não é probabilidade de acerto."""));
                    api.components(new Components().addSecuritySchemes(ESQUEMA_CHAVE, new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.HEADER)
                            .name("X-API-Key")
                            .description("Chave tribia_<prefixo>_<segredo> emitida em /api/admin/chaves-api")));
                    api.addSecurityItem(new SecurityRequirement().addList(ESQUEMA_CHAVE));
                })
                .build();
    }
}

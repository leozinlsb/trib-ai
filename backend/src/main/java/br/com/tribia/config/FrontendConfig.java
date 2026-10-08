package br.com.tribia.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.List;

/**
 * Serve o front (React) pelo próprio Spring quando ele está empacotado em classpath:/static (a imagem Docker faz
 * isso). Assim a API e as telas ficam no mesmo endereço: sem proxy, sem CORS e com o cookie de sessão na mesma origem.
 *
 * Rotas de tela do React (ex.: /dashboard/empresas/1/documentos) não são arquivos: caem no index.html. Caminhos da
 * API, do console H2 e da documentação nunca caem nele, para continuarem respondendo 404 quando não existirem.
 * Em desenvolvimento não há /static e nada muda (o front roda no Vite).
 */
@Configuration
public class FrontendConfig implements WebMvcConfigurer {

    private static final List<String> RESERVADOS = List.of("api/", "h2-console", "swagger-ui", "v3/api-docs");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String caminho, Resource local) throws IOException {
                        Resource pedido = local.createRelative(caminho);
                        if (pedido.exists() && pedido.isReadable()) {
                            return pedido;
                        }
                        if (reservado(caminho) || temExtensao(caminho)) {
                            return null;
                        }
                        Resource indice = new ClassPathResource("static/index.html");
                        return indice.exists() ? indice : null;
                    }
                });
    }

    static boolean reservado(String caminho) {
        String c = caminho.startsWith("/") ? caminho.substring(1) : caminho;
        return RESERVADOS.stream().anyMatch(c::startsWith);
    }

    /** Arquivo ausente (ex.: /assets/velho.js) deve dar 404, não uma página HTML no lugar. */
    static boolean temExtensao(String caminho) {
        String ultimo = caminho.substring(caminho.lastIndexOf('/') + 1);
        return ultimo.contains(".");
    }
}

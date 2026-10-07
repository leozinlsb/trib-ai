package br.com.tribia.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tribiaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("TribIA API")
                .version("0.1.0")
                .description("Classificação de itens de NF-e (CST + cClassTrib) com IA e comparativo "
                        + "PIS/Cofins hoje x CBS/IBS/IS 2027, por cliente."));
    }
}

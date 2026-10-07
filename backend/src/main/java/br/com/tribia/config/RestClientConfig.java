package br.com.tribia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class RestClientConfig {

    /** RestClient da calculadora oficial, com URL base e timeouts de application.properties. */
    @Bean
    public RestClient calculadoraRestClient(RestClient.Builder builder, CalculadoraProperties props) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(props.timeoutConexao()).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(props.timeoutResposta());
        return builder.baseUrl(props.url()).requestFactory(fabrica).build();
    }
}

package br.com.tribia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TribiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TribiaApplication.class, args);
    }
}

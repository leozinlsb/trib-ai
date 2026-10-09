package br.com.tribia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Execução das análises fiscais em segundo plano: poucas threads (cada análise chama a IA) e fila limitada; com a
 * fila cheia, o envio responde 503. tribia.fiscal.sincrono=true processa na própria requisição (usado nos testes).
 * O que estava em andamento quando o servidor parou é retomado na inicialização (RetomadaAnalisesFiscais).
 */
@Configuration
public class AnalisesFiscaisConfig {

    @Bean(name = "analisesFiscaisExecutor")
    TaskExecutor analisesFiscaisExecutor(@Value("${tribia.fiscal.sincrono:false}") boolean sincrono,
                                         @Value("${tribia.fiscal.threads:2}") int threads,
                                         @Value("${tribia.fiscal.fila:20}") int fila) {
        if (sincrono) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setThreadNamePrefix("analise-fiscal-");
        e.setCorePoolSize(threads);
        e.setMaxPoolSize(threads);
        e.setQueueCapacity(fila);
        e.setWaitForTasksToCompleteOnShutdown(false);
        return e; // o Spring inicializa o pool (afterPropertiesSet)
    }
}

package br.com.tribia.config;

import br.com.tribia.model.StatusAnalise;
import br.com.tribia.repository.AnaliseFiscalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * Execução das análises fiscais em segundo plano: poucas threads (cada análise chama a IA) e fila limitada; com a
 * fila cheia, o envio responde 503. tribia.fiscal.sincrono=true processa na própria requisição (usado nos testes).
 */
@Configuration
public class AnalisesFiscaisConfig {

    private static final Logger log = LoggerFactory.getLogger(AnalisesFiscaisConfig.class);

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

    /** O processamento é em memória: o que estava em andamento quando o servidor parou não volta sozinho. */
    @Bean
    ApplicationRunner interromperAnalisesPendentes(AnaliseFiscalRepository repository,
                                                   PlatformTransactionManager transacoes) {
        return args -> {
            Integer n = new TransactionTemplate(transacoes).execute(s -> repository.interromperEmAndamento(
                    StatusAnalise.EM_ANDAMENTO,
                    "A análise foi interrompida porque o servidor reiniciou. Inicie uma nova análise.", Instant.now()));
            if (n != null && n > 0) {
                log.warn("Análises fiscais: {} análise(s) em andamento marcada(s) como falha após reinício", n);
            }
        };
    }
}

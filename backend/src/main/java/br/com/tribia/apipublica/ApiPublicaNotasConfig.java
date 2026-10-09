package br.com.tribia.apipublica;

import br.com.tribia.apipublica.repository.ClassificacaoAvulsaApiRepository;
import br.com.tribia.apipublica.repository.EnvioNotaApiRepository;
import br.com.tribia.apipublica.servico.ProcessadorClassificacaoAvulsa;
import br.com.tribia.apipublica.servico.ProcessadorEnvioNota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

/**
 * Fila própria do trabalho em segundo plano da API pública: envios de NF-e e classificações avulsas (separada da
 * fila das análises de NCM, para uma não travar a outra). tribia.api-publica.sincrono=true processa na própria
 * requisição (usado nos testes).
 */
@Configuration
public class ApiPublicaNotasConfig {

    private static final Logger log = LoggerFactory.getLogger(ApiPublicaNotasConfig.class);

    @Bean(name = "notasApiExecutor")
    TaskExecutor notasApiExecutor(@Value("${tribia.api-publica.sincrono:false}") boolean sincrono,
                                  @Value("${tribia.api-publica.threads-notas:2}") int threads,
                                  @Value("${tribia.api-publica.fila-notas:20}") int fila) {
        if (sincrono) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setThreadNamePrefix("api-nota-");
        e.setCorePoolSize(threads);
        e.setMaxPoolSize(threads);
        e.setQueueCapacity(fila);
        e.setWaitForTasksToCompleteOnShutdown(false);
        return e; // o Spring inicializa o pool
    }

    /** Classificações avulsas em processamento quando o servidor parou voltam para a fila (cota já reservada). */
    @Bean
    ApplicationRunner retomarClassificacoesAvulsas(ClassificacaoAvulsaApiRepository pedidos,
                                                   ProcessadorClassificacaoAvulsa processador,
                                                   @Qualifier("notasApiExecutor") TaskExecutor executor) {
        return args -> {
            List<Long> pendentes = pedidos.idsEmProcessamento();
            for (Long id : pendentes) {
                try {
                    executor.execute(() -> processador.processar(id));
                } catch (TaskRejectedException e) {
                    log.warn("API pública: fila cheia ao retomar a classificação {}; fica para a próxima inicialização", id);
                }
            }
            if (!pendentes.isEmpty()) {
                log.info("API pública: {} classificação(ões) avulsa(s) retomada(s) após reinício", pendentes.size());
            }
        };
    }

    /** Envios que estavam em processamento quando o servidor parou voltam para a fila (o processamento é idempotente). */
    @Bean
    ApplicationRunner retomarEnviosDeNotas(EnvioNotaApiRepository envios, ProcessadorEnvioNota processador,
                                           @Qualifier("notasApiExecutor") TaskExecutor executor) {
        return args -> {
            List<Long> pendentes = envios.idsEmProcessamento();
            for (Long id : pendentes) {
                try {
                    executor.execute(() -> processador.processar(id));
                } catch (TaskRejectedException e) {
                    log.warn("API pública: fila cheia ao retomar o envio {}; fica para a próxima inicialização", id);
                }
            }
            if (!pendentes.isEmpty()) {
                log.info("API pública: {} envio(s) de nota retomado(s) após reinício", pendentes.size());
            }
        };
    }
}

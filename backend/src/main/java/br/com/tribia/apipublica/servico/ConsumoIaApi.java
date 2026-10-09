package br.com.tribia.apipublica.servico;

import br.com.tribia.apipublica.repository.ClassificacaoAvulsaApiRepository;
import br.com.tribia.apipublica.repository.EnvioNotaApiRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cota diária de itens enviados à IA por chave, somando os envios de NF-e e as classificações avulsas (contada no
 * banco: sobrevive a reinício). Também dá a trava por chave compartilhada pelos dois caminhos, para que um envio de
 * nota e uma classificação simultâneos da mesma chave não passem juntos da cota.
 */
@Component
public class ConsumoIaApi {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private final EnvioNotaApiRepository envios;
    private final ClassificacaoAvulsaApiRepository avulsas;
    private final TransactionTemplate tx;
    private final Map<Long, Object> travas = new ConcurrentHashMap<>();

    public ConsumoIaApi(EnvioNotaApiRepository envios, ClassificacaoAvulsaApiRepository avulsas,
                        PlatformTransactionManager transacoes) {
        this.envios = envios;
        this.avulsas = avulsas;
        this.tx = new TransactionTemplate(transacoes);
    }

    /** Itens que a chave já enviou à IA hoje (dia de Brasília). */
    public long itensHoje(Long chaveId) {
        Instant desde = inicioDoDia(hoje());
        Long total = tx.execute(s -> envios.somarItensIaDesde(chaveId, desde) + avulsas.somarItensIaDesde(chaveId, desde));
        return total == null ? 0 : total;
    }

    /** Trava da chave: segure-a entre conferir a cota e gravar o consumo. */
    public Object trava(Long chaveId) {
        return travas.computeIfAbsent(chaveId, k -> new Object());
    }

    public static LocalDate hoje() {
        return LocalDate.now(BRASILIA);
    }

    public static Instant inicioDoDia(LocalDate d) {
        return d.atStartOfDay(BRASILIA).toInstant();
    }

    /** Segundos até a cota renovar (meia-noite de Brasília), para o Retry-After. */
    public static long segundosAteRenovar() {
        return Math.max(1, java.time.Duration.between(Instant.now(), inicioDoDia(hoje().plusDays(1))).toSeconds());
    }
}

package br.com.tribia.service.fiscal;

import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.Etapa;
import br.com.tribia.model.AnaliseFiscal;
import br.com.tribia.model.StatusAnalise;
import br.com.tribia.repository.AnaliseFiscalRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Na inicialização, retoma as análises fiscais que estavam em andamento quando o servidor parou: volta para
 * AGUARDANDO (registrado no histórico) e entra na fila de novo. Tudo o que o processamento precisa está gravado
 * (dados e texto dos anexos), e a reserva atômica do processador impede execução duplicada.
 *
 * Limites: depois de tribia.fiscal.max-tentativas inícios, a análise vira FALHA em vez de repetir para sempre (ex.: uma
 * análise que derruba o servidor). Análises criadas antes de o texto dos anexos ser gravado, que tinham anexos, também
 * viram FALHA: retomá-las sem os anexos mudaria o resultado sem avisar. tribia.fiscal.retomar-apos-reinicio=false
 * desliga a retomada (todas as interrompidas viram FALHA).
 */
@Component
public class RetomadaAnalisesFiscais {

    private static final Logger log = LoggerFactory.getLogger(RetomadaAnalisesFiscais.class);

    private final AnaliseFiscalRepository repository;
    private final ProcessadorAnaliseFiscal processador;
    private final TaskExecutor executor;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final boolean retomar;
    private final int maxTentativas;

    public RetomadaAnalisesFiscais(AnaliseFiscalRepository repository, ProcessadorAnaliseFiscal processador,
                                   @Qualifier("analisesFiscaisExecutor") TaskExecutor executor, ObjectMapper json,
                                   PlatformTransactionManager transacoes,
                                   @Value("${tribia.fiscal.retomar-apos-reinicio:true}") boolean retomar,
                                   @Value("${tribia.fiscal.max-tentativas:2}") int maxTentativas) {
        this.repository = repository;
        this.processador = processador;
        this.executor = executor;
        this.json = json;
        this.tx = new TransactionTemplate(transacoes);
        this.retomar = retomar;
        this.maxTentativas = maxTentativas;
    }

    public record Retomada(List<Long> retomadas, List<Long> encerradas) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        Retomada r = retomarInterrompidas();
        if (!r.retomadas().isEmpty() || !r.encerradas().isEmpty()) {
            log.warn("Análises fiscais interrompidas pelo reinício: {} retomada(s) {}, {} encerrada(s) como falha {}",
                    r.retomadas().size(), r.retomadas(), r.encerradas().size(), r.encerradas());
        }
    }

    /** Separado do evento para poder ser testado sem reiniciar o contexto. */
    public Retomada retomarInterrompidas() {
        List<Long> ids = tx.execute(s -> repository.idsEmAndamento(StatusAnalise.EM_ANDAMENTO));
        List<Long> retomadas = new ArrayList<>();
        List<Long> encerradas = new ArrayList<>();
        for (Long id : ids == null ? List.<Long>of() : ids) {
            Boolean voltou = tx.execute(s -> preparar(id));
            if (Boolean.TRUE.equals(voltou)) {
                try {
                    executor.execute(() -> processador.processar(id));
                    retomadas.add(id);
                } catch (TaskRejectedException e) {
                    tx.executeWithoutResult(s -> encerrar(id,
                            "A análise foi interrompida porque o servidor reiniciou e a fila estava cheia. Inicie uma nova análise."));
                    encerradas.add(id);
                }
            } else {
                encerradas.add(id);
            }
        }
        return new Retomada(retomadas, encerradas);
    }

    /** true = voltou para AGUARDANDO; false = encerrada como FALHA. */
    private boolean preparar(Long id) {
        AnaliseFiscal a = repository.findById(id).orElse(null);
        if (a == null || !a.getStatus().emAndamento()) {
            return false;
        }
        boolean semTextoDosAnexos = a.getAnexosLidosJson() == null && a.getAnexosJson() != null
                && !a.getAnexosJson().isBlank() && !"[]".equals(a.getAnexosJson().trim());
        String motivo = !retomar ? "A análise foi interrompida porque o servidor reiniciou. Inicie uma nova análise."
                : a.getTentativas() >= maxTentativas
                ? "A análise foi interrompida " + a.getTentativas() + " vez(es) pelo reinício do servidor e não será "
                + "repetida automaticamente. Inicie uma nova análise."
                : semTextoDosAnexos
                ? "A análise foi interrompida pelo reinício do servidor e os anexos não ficaram gravados. Inicie uma nova análise."
                : null;
        if (motivo != null) {
            encerrar(a, motivo);
            return false;
        }
        Instant agora = Instant.now();
        a.mudarStatus(StatusAnalise.AGUARDANDO, historicoCom(a, StatusAnalise.AGUARDANDO, agora), agora);
        return true;
    }

    private void encerrar(Long id, String motivo) {
        repository.findById(id).ifPresent(a -> encerrar(a, motivo));
    }

    private void encerrar(AnaliseFiscal a, String motivo) {
        Instant agora = Instant.now();
        a.mudarStatus(StatusAnalise.FALHA, historicoCom(a, StatusAnalise.FALHA, agora), agora);
        a.concluir(null, null, motivo);
    }

    private String historicoCom(AnaliseFiscal a, StatusAnalise status, Instant agora) {
        List<Etapa> h = new ArrayList<>(processador.ler(a.getHistoricoJson()));
        h.add(new Etapa(status, agora));
        try {
            return json.writeValueAsString(h);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

package br.com.tribia.apipublica.servico;

import br.com.tribia.apipublica.model.EnvioNotaApi;
import br.com.tribia.apipublica.repository.EnvioNotaApiRepository;
import br.com.tribia.dto.CalculoNotaDto;
import br.com.tribia.dto.ClassificacaoNotaDto;
import br.com.tribia.security.EscopoIntegracao;
import br.com.tribia.service.calculo.CalculoService;
import br.com.tribia.service.classificacao.ClassificacaoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Segunda metade de um envio de NF-e pela API pública, em segundo plano: classifica os itens que o XML e o cache não
 * resolveram (IA, se a cota permitiu) e calcula 2027, pelos mesmos serviços do site. Roda num
 * {@link EscopoIntegracao} preso à empresa do envio, então o AcessoService confere a nota como faria para um usuário
 * daquela empresa.
 *
 * Idempotente: classificar pula itens já classificados e calcular substitui os cálculos da nota. Por isso um envio
 * interrompido por reinício é simplesmente processado de novo (ver ApiPublicaNotasConfig).
 */
@Component
public class ProcessadorEnvioNota {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorEnvioNota.class);

    private final EnvioNotaApiRepository envios;
    private final ClassificacaoService classificacao;
    private final CalculoService calculo;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public ProcessadorEnvioNota(EnvioNotaApiRepository envios, ClassificacaoService classificacao,
                                CalculoService calculo, ObjectMapper json, PlatformTransactionManager transacoes) {
        this.envios = envios;
        this.classificacao = classificacao;
        this.calculo = calculo;
        this.json = json;
        this.tx = new TransactionTemplate(transacoes);
    }

    private record Alvo(Long clienteId, Long notaId, boolean usarIa, String chave) {
    }

    public void processar(Long envioId) {
        Alvo alvo = tx.execute(s -> envios.findById(envioId)
                .filter(e -> e.getStatus() == EnvioNotaApi.Status.EM_PROCESSAMENTO)
                .map(e -> new Alvo(e.getCliente().getId(), e.getNota().getId(), e.isUsarIa(), e.getChave().getPrefixo()))
                .orElse(null));
        if (alvo == null) {
            return; // já terminou (ex.: retomada em paralelo) ou não existe
        }
        try {
            List<String> avisos = EscopoIntegracao.executar(alvo.clienteId(), () -> {
                ClassificacaoNotaDto c = classificacao.classificar(alvo.notaId(), alvo.usarIa());
                CalculoNotaDto calc = calculo.calcular(alvo.notaId(), null);
                Set<String> todos = new LinkedHashSet<>(c.avisos());
                if (!alvo.usarIa() && !c.pendentes().isEmpty()) {
                    todos.add("Cota diária de itens para a IA desta chave atingida: " + c.pendentes().size()
                            + " item(ns) ficaram sem classificação automática e fora do cálculo. Classifique-os na "
                            + "plataforma (Revisão).");
                }
                todos.addAll(calc.avisos());
                return new ArrayList<>(todos);
            });
            tx.executeWithoutResult(s -> envios.findById(envioId).ifPresent(e -> e.concluir(escrever(avisos), Instant.now())));
            log.info("API pública: nota do envio {} processada (chave {})", envioId, alvo.chave());
        } catch (RuntimeException e) {
            log.error("API pública: falha ao processar o envio {} (chave {})", envioId, alvo.chave(), e);
            tx.executeWithoutResult(s -> envios.findById(envioId).ifPresent(x -> x.falhar(
                    "Não foi possível classificar ou calcular a nota. Ela continua importada na plataforma, onde pode "
                            + "ser processada de novo.", Instant.now())));
        }
    }

    private String escrever(List<String> avisos) {
        try {
            return json.writeValueAsString(avisos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

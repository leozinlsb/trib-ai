package br.com.tribia.apipublica.servico;

import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.ProdutoClassificado;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.ProdutoClassificar;
import br.com.tribia.apipublica.model.ClassificacaoAvulsaApi;
import br.com.tribia.apipublica.repository.ClassificacaoAvulsaApiRepository;
import br.com.tribia.security.EscopoIntegracao;
import br.com.tribia.service.classificacao.ClassificacaoService;
import br.com.tribia.service.classificacao.ClassificacaoService.ProdutoAvulso;
import br.com.tribia.service.classificacao.ClassificacaoService.ResultadoAvulso;
import br.com.tribia.service.classificacao.ClassificacaoService.SugestaoAvulsa;
import br.com.tribia.service.classificacao.CriterioRevisao;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaCClassTrib.CClassTrib;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Segunda metade de um pedido de classificação avulsa, em segundo plano: manda à IA os produtos que o cache não
 * resolveu (até o que foi reservado da cota) e grava o resultado público. Roda num {@link EscopoIntegracao} preso à
 * empresa do pedido. Também monta o resultado quando tudo vem do cache (sem fila).
 */
@Component
public class ProcessadorClassificacaoAvulsa {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorClassificacaoAvulsa.class);
    static final TypeReference<List<ProdutoClassificar>> LISTA_PRODUTOS = new TypeReference<>() {
    };

    private final ClassificacaoAvulsaApiRepository pedidos;
    private final ClassificacaoService classificacao;
    private final TabelaCClassTrib tabela;
    private final CriterioRevisao criterio;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public ProcessadorClassificacaoAvulsa(ClassificacaoAvulsaApiRepository pedidos, ClassificacaoService classificacao,
                                          TabelaCClassTrib tabela, CriterioRevisao criterio, ObjectMapper json,
                                          PlatformTransactionManager transacoes) {
        this.pedidos = pedidos;
        this.classificacao = classificacao;
        this.tabela = tabela;
        this.criterio = criterio;
        this.json = json;
        this.tx = new TransactionTemplate(transacoes);
    }

    private record Alvo(Long clienteId, int reservados, List<ProdutoClassificar> produtos, String chave) {
    }

    public void processar(Long pedidoId) {
        Alvo alvo = tx.execute(s -> pedidos.findById(pedidoId)
                .filter(p -> p.getStatus() == ClassificacaoAvulsaApi.Status.EM_PROCESSAMENTO)
                .map(p -> new Alvo(p.getCliente().getId(), p.getItensIa(), ler(p.getProdutosJson(), LISTA_PRODUTOS),
                        p.getChave().getPrefixo()))
                .orElse(null));
        if (alvo == null) {
            return; // já terminou (ex.: retomada em paralelo) ou não existe
        }
        try {
            ResultadoAvulso r = EscopoIntegracao.executar(alvo.clienteId(), () -> classificacao.classificarAvulsos(
                    alvo.clienteId(), paraMotor(alvo.produtos()), alvo.reservados()));
            String resultado = escrever(publico(alvo.produtos(), r));
            String avisos = escrever(r.avisos());
            tx.executeWithoutResult(s -> pedidos.findById(pedidoId).ifPresent(p -> p.concluir(resultado, avisos, Instant.now())));
            log.info("API pública: classificação avulsa {} concluída (chave {}, IA: {})", pedidoId, alvo.chave(), r.itensIa());
        } catch (RuntimeException e) {
            log.error("API pública: falha na classificação avulsa {} (chave {})", pedidoId, alvo.chave(), e);
            tx.executeWithoutResult(s -> pedidos.findById(pedidoId).ifPresent(p -> p.falhar(
                    "Não foi possível classificar os produtos. Envie de novo com outra Idempotency-Key.", Instant.now())));
        }
    }

    static List<ProdutoAvulso> paraMotor(List<ProdutoClassificar> produtos) {
        return produtos.stream()
                .map(p -> new ProdutoAvulso(p.ncm().replaceAll("\\D", ""), p.descricao().trim(),
                        p.unidade() == null || p.unidade().isBlank() ? null : p.unidade().trim(), p.valorUnitario()))
                .toList();
    }

    /** Resultado público, na ordem dos produtos enviados. Mesma regra de situação das notas. */
    List<ProdutoClassificado> publico(List<ProdutoClassificar> produtos, ResultadoAvulso r) {
        List<ProdutoClassificado> saida = new ArrayList<>();
        for (int i = 0; i < produtos.size(); i++) {
            ProdutoClassificar p = produtos.get(i);
            String ncm = p.ncm().replaceAll("\\D", "");
            SugestaoAvulsa s = r.sugestoes().get(i);
            if (s == null) {
                saida.add(new ProdutoClassificado(p.referencia(), ncm, p.descricao(), "SEM_CLASSIFICACAO", null, null,
                        null, null, null, null, null, null));
                continue;
            }
            Optional<CClassTrib> oficial = tabela.buscar(s.cClassTrib());
            boolean confiancaBaixa = s.confianca() == null || s.confianca().compareTo(criterio.confiancaMinima()) < 0;
            String situacao = !s.aceita() || confiancaBaixa ? "PENDENTE_REVISAO" : "CONFIRMADA";
            saida.add(new ProdutoClassificado(p.referencia(), ncm, p.descricao(), situacao, s.cst(), s.cClassTrib(),
                    oficial.map(CClassTrib::nome).orElse(null), oficial.map(c -> c.regime().name()).orElse(null),
                    oficial.map(CClassTrib::descricaoRegime).orElse(null), s.origem().name(), s.confianca(),
                    s.justificativa()));
        }
        return saida;
    }

    String escrever(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    <T> T ler(String texto, TypeReference<T> tipo) {
        try {
            return json.readValue(texto, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Pedido de classificação ilegível", e);
        }
    }
}

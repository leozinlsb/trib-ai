package br.com.tribia.service.fiscal;

import br.com.tribia.client.llm.LlmException;
import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.Etapa;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Alternativa;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Fonte;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Fundamentacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Pontuacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Resultado;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.SituacaoValidacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Validacao;
import br.com.tribia.model.AnaliseFiscal;
import br.com.tribia.model.StatusAnalise;
import br.com.tribia.repository.AnaliseFiscalRepository;
import br.com.tribia.service.fiscal.AvaliadorJev.Candidata;
import br.com.tribia.service.fiscal.AvaliadorJev.MercadoriaParaJev;
import br.com.tribia.service.fiscal.PesquisaNcmIa.Resposta;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executa uma análise fiscal em segundo plano, etapa por etapa, gravando cada mudança de status (o front acompanha
 * consultando o detalhe). A chamada à IA e à JEV acontece fora de transação.
 *
 * Não confere acesso: quem chama ({@link AnaliseFiscalService}) já autorizou a empresa antes de criar a análise, e
 * esta classe só recebe o id dela.
 */
@Component
public class ProcessadorAnaliseFiscal {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorAnaliseFiscal.class);
    private static final TypeReference<List<Etapa>> LISTA_ETAPAS = new TypeReference<>() {
    };

    private final AnaliseFiscalRepository repository;
    private final PesquisaNcmIa pesquisa;
    private final ValidadorNcm validador;
    private final ObjectProvider<AvaliadorJev> jev;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Clock relogio;

    public ProcessadorAnaliseFiscal(AnaliseFiscalRepository repository, PesquisaNcmIa pesquisa, ValidadorNcm validador,
                                    ObjectProvider<AvaliadorJev> jev, ObjectMapper json,
                                    PlatformTransactionManager transacoes) {
        this.repository = repository;
        this.pesquisa = pesquisa;
        this.validador = validador;
        this.jev = jev;
        this.json = json;
        this.tx = new TransactionTemplate(transacoes);
        this.relogio = Clock.systemUTC();
    }

    /**
     * @param textoDosAnexos  conteúdo dos anexos de texto (nome → texto), enviado à IA
     * @param anexosNaoLidos  anexos que a IA não lê nesta versão (PDF, imagens, Office)
     */
    public void processar(Long analiseId, Map<String, String> textoDosAnexos, List<String> anexosNaoLidos) {
        try {
            executar(analiseId, textoDosAnexos, anexosNaoLidos);
        } catch (RuntimeException e) {
            log.error("Análise fiscal {}: falha inesperada", analiseId, e);
            encerrar(analiseId, StatusAnalise.FALHA, null, null,
                    "Erro inesperado durante a análise. Tente novamente; se persistir, avise o suporte.");
        }
    }

    private void executar(Long analiseId, Map<String, String> textoDosAnexos, List<String> anexosNaoLidos) {
        AnaliseFiscal a = mudar(analiseId, StatusAnalise.INTERPRETANDO);
        PesquisaNcmIa.Entrada entrada = new PesquisaNcmIa.Entrada(a.getMercadoria(), a.getDescricao(), a.getComposicao(),
                a.getFinalidade(), a.getCaracteristicas(), a.getNcmAtual(), textoDosAnexos);

        mudar(analiseId, StatusAnalise.PESQUISANDO_NCM);
        Resposta r;
        try {
            r = pesquisa.pesquisar(entrada);
        } catch (LlmException e) {
            log.warn("Análise fiscal {}: IA indisponível ({}: {})", analiseId, e.getTipo(), e.getMessage());
            encerrar(analiseId, StatusAnalise.FALHA, null, null, switch (e.getTipo()) {
                case NAO_CONFIGURADO -> "A IA não está configurada neste ambiente (GEMINI_API_KEY): a análise não pôde ser feita.";
                case INDISPONIVEL -> "A IA está indisponível no momento (sobrecarga ou limite de uso). Tente novamente em alguns minutos.";
                case RESPOSTA_INVALIDA -> "A IA devolveu uma resposta inutilizável. Tente novamente; se persistir, detalhe mais a mercadoria.";
            });
            return;
        }

        if (r.candidatas().isEmpty() || !r.suficiente()) {
            encerrar(analiseId, r.candidatas().isEmpty() && r.suficiente() ? StatusAnalise.FALHA
                    : StatusAnalise.INFORMACOES_INSUFICIENTES, null, null, mensagemInsuficiente(r));
            return;
        }

        mudar(analiseId, StatusAnalise.AVALIANDO);
        List<String> limitacoes = limitacoes(anexosNaoLidos);
        Map<String, Pontuacao> pontuacoes = avaliarComJev(entrada, r, limitacoes);

        mudar(analiseId, StatusAnalise.VALIDANDO);
        ValidadorNcm.Resultado v = validador.validar(r.candidatas(), a.getNcmAtual());

        mudar(analiseId, StatusAnalise.GERANDO_RELATORIO);
        PesquisaNcmIa.Candidata escolhida = r.candidatas().get(0);
        SituacaoValidacao situacao = v.validacao().situacao();
        List<String> observacoes = new ArrayList<>(r.observacoes());
        if (!r.descartadas().isEmpty()) {
            observacoes.add("Códigos propostos pela IA e descartados por formato inválido: " + String.join("; ", r.descartadas()) + ".");
        }
        ResultadoAnaliseFiscal resultado = new ResultadoAnaliseFiscal(
                new Resultado(escolhida.ncm(), escolhida.descricao(), situacao, Instant.now(relogio).toString()),
                new Fundamentacao(r.caracteristicas(), escolhida.motivos(), r.regrasConsideradas(), observacoes, limitacoes),
                r.candidatas().stream()
                        .map(c -> new Alternativa(c.ncm(), c.descricao(),
                                c.avaliacao() + " (confiança da análise: " + c.confianca() + ")", pontuacoes.get(c.ncm())))
                        .toList(),
                v.validacao(),
                fontes(v.usouRegrasDaReforma()));

        boolean validado = situacao == SituacaoValidacao.VALIDADO_VERIFICACOES;
        encerrar(analiseId, validado ? StatusAnalise.CONCLUIDA : StatusAnalise.AGUARDANDO_REVISAO, escolhida.ncm(),
                resultado, validado ? null : mensagemRevisao(v.validacao()));
    }

    private Map<String, Pontuacao> avaliarComJev(PesquisaNcmIa.Entrada e, Resposta r, List<String> limitacoes) {
        AvaliadorJev avaliador = jev.getIfAvailable(JevIndisponivel::new);
        if (!avaliador.disponivel()) {
            limitacoes.add("Pontuação de compatibilidade (JEV AI) ainda não disponível: as alternativas aparecem sem ela.");
            return Map.of();
        }
        try {
            return avaliador.avaliar(
                    new MercadoriaParaJev(e.nome(), e.descricao(), e.composicao(), e.finalidade(), e.caracteristicas(),
                            r.caracteristicas()),
                    r.candidatas().stream().map(c -> new Candidata(c.ncm(), c.descricao())).toList());
        } catch (RuntimeException ex) {
            log.warn("JEV AI indisponível: {}", ex.getMessage());
            limitacoes.add("A JEV AI não respondeu nesta análise: as alternativas aparecem sem pontuação.");
            return Map.of();
        }
    }

    private static List<String> limitacoes(List<String> anexosNaoLidos) {
        List<String> l = new ArrayList<>();
        l.add("Sugestão gerada por IA a partir das informações enviadas: não é classificação fiscal definitiva e "
                + "deve ser conferida por profissional habilitado.");
        l.add("A descrição do código vem da análise da IA; confira o texto oficial na TIPI.");
        l.add(ValidadorNcm.VIGENCIA_NAO_VERIFICADA);
        if (!anexosNaoLidos.isEmpty()) {
            l.add("Anexos não lidos pela análise nesta versão (só arquivos .txt são lidos): "
                    + String.join(", ", anexosNaoLidos) + ".");
        }
        return l;
    }

    private static List<Fonte> fontes(boolean usouRegrasDaReforma) {
        if (!usouRegrasDaReforma) {
            return List.of();
        }
        return List.of(
                new Fonte("Tabela de cClassTrib e NCMs aplicáveis", "Calculadora RTC oficial (Receita Federal)",
                        "distribuição offline usada pelo TribIA", null, null),
                new Fonte("Lei Complementar nº 214/2025", "Anexos de redução e alíquota zero", null, null,
                        "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp214.htm"));
    }

    private static String mensagemInsuficiente(Resposta r) {
        StringBuilder sb = new StringBuilder();
        if (r.candidatas().isEmpty() && r.suficiente()) {
            return "A IA não propôs nenhuma NCM válida para esta mercadoria. Detalhe melhor a descrição e tente de novo.";
        }
        sb.append("As informações não bastam para indicar a NCM com segurança.");
        if (!r.faltando().isEmpty()) {
            sb.append(" Informe também: ").append(String.join("; ", r.faltando())).append('.');
        }
        if (!r.candidatas().isEmpty()) {
            sb.append(" Códigos possíveis até aqui: ")
                    .append(r.candidatas().stream().map(c -> ValidadorNcm.formatar(c.ncm())).collect(Collectors.joining(", ")))
                    .append('.');
        }
        return sb.toString();
    }

    private static String mensagemRevisao(Validacao v) {
        return v.pendencias().isEmpty() ? null : "Pontos a conferir: " + String.join(" ", v.pendencias());
    }

    // ---------------- gravação ----------------

    private AnaliseFiscal mudar(Long id, StatusAnalise status) {
        return tx.execute(s -> {
            AnaliseFiscal a = repository.findById(id).orElseThrow();
            a.mudarStatus(status, historicoCom(a, status), Instant.now(relogio));
            return a;
        });
    }

    private void encerrar(Long id, StatusAnalise status, String ncm, ResultadoAnaliseFiscal resultado, String mensagem) {
        tx.executeWithoutResult(s -> repository.findById(id).ifPresent(a -> {
            a.mudarStatus(status, historicoCom(a, status), Instant.now(relogio));
            a.concluir(ncm, resultado == null ? null : escrever(resultado), mensagem);
        }));
    }

    private String historicoCom(AnaliseFiscal a, StatusAnalise status) {
        List<Etapa> historico = new ArrayList<>(ler(a.getHistoricoJson()));
        historico.add(new Etapa(status, Instant.now(relogio)));
        return escrever(historico);
    }

    List<Etapa> ler(String historicoJson) {
        if (historicoJson == null || historicoJson.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(historicoJson, LISTA_ETAPAS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Histórico da análise ilegível", e);
        }
    }

    private String escrever(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

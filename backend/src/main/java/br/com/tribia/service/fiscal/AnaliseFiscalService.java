package br.com.tribia.service.fiscal;

import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.Anexo;
import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.AnaliseDetalhe;
import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.AnaliseResumo;
import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.Etapa;
import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.Indicadores;
import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.Pagina;
import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.Relatorio;
import br.com.tribia.dto.fiscal.MercadoriaEntradaDto;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal;
import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.AnaliseFiscal;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.StatusAnalise;
import br.com.tribia.repository.AnaliseFiscalRepository;
import br.com.tribia.security.AcessoService;
import br.com.tribia.security.UsuarioLogado;
import br.com.tribia.service.tabelas.TabelaNcmVigente;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Inteligência Fiscal: cria, lista e detalha análises de NCM de mercadorias. Toda operação passa pelo
 * {@link AcessoService}: usuário de empresa só vê as análises da própria empresa (as outras dão 404).
 * O processamento roda em segundo plano ({@link ProcessadorAnaliseFiscal}); o front acompanha pelo detalhe.
 */
@Service
public class AnaliseFiscalService {

    static final int MAX_ARQUIVOS = 10;
    static final long MAX_BYTES_ARQUIVO = 10L * 1024 * 1024;
    static final Set<String> FORMATOS = Set.of("pdf", "png", "jpg", "jpeg", "webp", "doc", "docx", "xls", "xlsx", "txt");
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final TypeReference<List<Anexo>> LISTA_ANEXOS = new TypeReference<>() {
    };

    private final AnaliseFiscalRepository repository;
    private final AcessoService acesso;
    private final ProcessadorAnaliseFiscal processador;
    private final TaskExecutor executor;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final RelatorioAnalisePdf pdf;
    private final LeitorAnexos leitor;
    private final TabelaNcmVigente ncmVigente;

    public AnaliseFiscalService(AnaliseFiscalRepository repository, AcessoService acesso,
                                ProcessadorAnaliseFiscal processador,
                                @Qualifier("analisesFiscaisExecutor") TaskExecutor executor, ObjectMapper json,
                                PlatformTransactionManager transacoes, RelatorioAnalisePdf pdf,
                                LeitorAnexos leitor, TabelaNcmVigente ncmVigente) {
        this.ncmVigente = ncmVigente;
        this.pdf = pdf;
        this.leitor = leitor;
        this.repository = repository;
        this.acesso = acesso;
        this.processador = processador;
        this.executor = executor;
        this.json = json;
        this.tx = new TransactionTemplate(transacoes);
    }

    public Pagina<AnaliseResumo> listar(Long clienteId, String q, String status, String de, String ate,
                                       Integer pagina, Integer tamanho) {
        acesso.clienteAcessivel(clienteId);
        int p = pagina == null ? 0 : pagina;
        int t = tamanho == null ? 10 : tamanho;
        if (p < 0 || t < 1 || t > 50) {
            throw ApiException.requisicaoInvalida("pagina deve ser >= 0 e tamanho entre 1 e 50.");
        }
        StatusAnalise filtroStatus = status == null || status.isBlank() ? null : status(status);
        String termo = null;
        if (q != null && !q.isBlank()) {
            String busca = q.trim().toLowerCase(Locale.ROOT);
            // NCM digitada com pontos ("3401.11.90") é procurada só pelos dígitos, como fica gravada
            termo = "%" + (busca.matches("[\\d.]+") ? busca.replace(".", "") : busca) + "%";
        }
        String termoFinal = termo;
        Instant inicio = de == null || de.isBlank() ? null : inicioDoDia(data(de, "de"));
        Instant fim = ate == null || ate.isBlank() ? null : inicioDoDia(data(ate, "ate").plusDays(1));
        Page<AnaliseFiscal> r = tx.execute(s -> repository.buscar(clienteId, filtroStatus, termoFinal, inicio, fim,
                PageRequest.of(p, t, Sort.by(Sort.Direction.DESC, "criadaEm").and(Sort.by(Sort.Direction.DESC, "id")))));
        return new Pagina<>(r.getContent().stream().map(AnaliseFiscalService::resumo).toList(), r.getTotalElements(), p, t);
    }

    public Indicadores indicadores(Long clienteId) {
        acesso.clienteAcessivel(clienteId);
        Map<StatusAnalise, Long> porStatus = new LinkedHashMap<>();
        for (Object[] linha : repository.contarPorStatus(clienteId)) {
            porStatus.put((StatusAnalise) linha[0], (Long) linha[1]);
        }
        long total = porStatus.values().stream().mapToLong(Long::longValue).sum();
        long emProcessamento = porStatus.entrySet().stream().filter(e -> e.getKey().emAndamento())
                .mapToLong(Map.Entry::getValue).sum();
        return new Indicadores(total, porStatus.getOrDefault(StatusAnalise.CONCLUIDA, 0L), emProcessamento,
                porStatus.getOrDefault(StatusAnalise.AGUARDANDO_REVISAO, 0L),
                porStatus.getOrDefault(StatusAnalise.FALHA, 0L),
                porStatus.getOrDefault(StatusAnalise.INFORMACOES_INSUFICIENTES, 0L));
    }

    /** Cria a análise (status AGUARDANDO) e dispara o processamento em segundo plano. */
    public AnaliseResumo iniciar(Long clienteId, MercadoriaEntradaDto dados, List<MultipartFile> arquivos) {
        Cliente cliente = acesso.clienteAcessivel(clienteId);
        if (!cliente.isAtivo()) {
            throw new ApiException(HttpStatus.CONFLICT, "Empresa desativada",
                    "Esta empresa está desativada. Reative-a para fazer análises fiscais.");
        }
        List<MultipartFile> lista = arquivos == null ? List.of() : arquivos.stream().filter(f -> !f.isEmpty()).toList();
        validarArquivos(lista);

        List<Anexo> anexos = new ArrayList<>();
        Map<String, String> textos = new LinkedHashMap<>();
        List<String> naoLidos = new ArrayList<>();
        for (MultipartFile f : lista) {
            String nome = nome(f);
            anexos.add(new Anexo(nome, f.getSize(), f.getContentType() == null ? "application/octet-stream" : f.getContentType()));
            LeitorAnexos.Leitura leitura;
            try {
                leitura = leitor.ler(nome, extensao(nome), bytes(f));
            } catch (LeitorAnexos.AnexoRecusadoException e) {
                throw ApiException.requisicaoInvalida(e.getMessage());
            }
            if (leitura.lido()) {
                textos.put(nome, leitura.texto());
            }
            if (leitura.observacao() != null) {
                naoLidos.add(nome + " (" + leitura.observacao() + ")");
            }
        }
        String anexosLidos = escrever(new ProcessadorAnaliseFiscal.AnexosLidos(textos, naoLidos));

        String ncmAtual = dados.ncmAtual() == null ? null : dados.ncmAtual().replaceAll("\\D", "");
        AnaliseFiscal salva = tx.execute(s -> {
            Instant agora = Instant.now();
            AnaliseFiscal a = new AnaliseFiscal(cliente, dados.nome().trim(), dados.descricao().trim(),
                    vazioComoNulo(dados.composicao()), vazioComoNulo(dados.finalidade()),
                    vazioComoNulo(dados.caracteristicas()), vazioComoNulo(ncmAtual), escrever(anexos), agora);
            a.mudarStatus(StatusAnalise.AGUARDANDO, escrever(List.of(new Etapa(StatusAnalise.AGUARDANDO, agora))), agora);
            a.guardarAnexosLidos(anexosLidos);
            return repository.save(a);
        });

        // o 202 descreve a análise como foi criada (AGUARDANDO), antes de o processamento começar
        AnaliseResumo criada = resumo(salva);
        // depois do commit: o processamento lê a análise em outra transação
        try {
            executor.execute(() -> processador.processar(salva.getId()));
        } catch (TaskRejectedException e) {
            tx.executeWithoutResult(s -> repository.findById(salva.getId()).ifPresent(a -> {
                a.mudarStatus(StatusAnalise.FALHA, a.getHistoricoJson(), Instant.now());
                a.concluir(null, null, "O servidor está ocupado com outras análises. Tente novamente em instantes.");
            }));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço ocupado",
                    "Muitas análises em andamento. Tente novamente em instantes.");
        }
        return criada;
    }

    /** Pedido de revisão humana: a NCM decidida (a sugerida, uma alternativa ou outra da NCM vigente) e o porquê. */
    public record RevisaoForm(String ncm, String observacao) {
    }

    /**
     * Registra a decisão de uma pessoa sobre a sugestão. Só em análises com resultado (concluída ou aguardando
     * revisão). A NCM decidida precisa constar da NCM vigente; trocar a sugestão exige justificativa. O resultado
     * automático é preservado como evidência e a revisão entra no histórico; a análise passa a CONCLUIDA.
     */
    public AnaliseDetalhe revisar(Long analiseId, RevisaoForm form) {
        UsuarioLogado quem = acesso.atual();
        AnaliseFiscal lida = acessivel(analiseId);
        if (lida.getResultadoJson() == null
                || (lida.getStatus() != StatusAnalise.CONCLUIDA && lida.getStatus() != StatusAnalise.AGUARDANDO_REVISAO)) {
            throw new ApiException(HttpStatus.CONFLICT, "Revisão indisponível",
                    "Só é possível revisar uma análise concluída ou aguardando revisão.");
        }
        String ncm = form == null || form.ncm() == null ? "" : form.ncm().replaceAll("\\D", "");
        String observacao = form == null || form.observacao() == null ? "" : form.observacao().trim();
        if (ncm.length() != 8) {
            throw ApiException.requisicaoInvalida("Informe a NCM decidida com 8 dígitos.");
        }
        if (observacao.length() > 1000) {
            throw ApiException.requisicaoInvalida("A observação pode ter até 1000 caracteres.");
        }
        if (ncmVigente.consultar(ncm, LocalDate.now(BRASILIA)).situacao() != TabelaNcmVigente.Situacao.VIGENTE) {
            throw ApiException.requisicaoInvalida("A NCM " + ValidadorNcm.formatar(ncm)
                    + " não consta da NCM vigente: confira o código.");
        }
        ResultadoAnaliseFiscal atual = ler(lida.getResultadoJson(), ResultadoAnaliseFiscal.class);
        String sugerida = atual.resultado() == null ? null : atual.resultado().ncm();
        boolean aceita = ncm.equals(sugerida);
        if (!aceita && observacao.isBlank()) {
            throw ApiException.requisicaoInvalida("Explique por que a NCM decidida é diferente da sugerida.");
        }
        Instant agora = Instant.now();
        ResultadoAnaliseFiscal.RevisaoHumana r = new ResultadoAnaliseFiscal.RevisaoHumana(aceita ? "ACEITA" : "ALTERADA",
                ncm, sugerida, observacao.isBlank() ? null : observacao, quem.nome() + " (" + quem.email() + ")",
                agora.toString());
        tx.executeWithoutResult(s -> repository.findById(analiseId).ifPresent(a -> {
            List<Etapa> historico = new ArrayList<>(processador.ler(a.getHistoricoJson()));
            historico.add(new Etapa(StatusAnalise.CONCLUIDA, agora));
            a.mudarStatus(StatusAnalise.CONCLUIDA, escrever(historico), agora);
            a.concluir(a.getNcmSugerida(), escrever(atual.comRevisao(r)), a.getMensagem());
        }));
        return detalhar(analiseId);
    }

    public AnaliseDetalhe detalhar(Long analiseId) {
        return detalhe(acessivel(analiseId));
    }

    /** PDF do relatório: só para análises com resultado; outra empresa = 404, como no detalhe. */
    public RelatorioPdf relatorio(Long analiseId) {
        AnaliseFiscal a = acessivel(analiseId);
        if (a.getResultadoJson() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Relatório indisponível",
                    a.getStatus().emAndamento()
                            ? "A análise ainda está em processamento: o relatório fica disponível quando ela terminar."
                            : "Esta análise terminou sem resultado ("
                            + RelatorioAnalisePdf.nomeStatus(a.getStatus()).toLowerCase(Locale.ROOT)
                            + "): não há relatório para gerar.");
        }
        byte[] conteudo = pdf.gerar(detalhe(a), a.getCliente(), Instant.now());
        return new RelatorioPdf(conteudo, "tribia-analise-fiscal-" + a.getId() + ".pdf");
    }

    public record RelatorioPdf(byte[] conteudo, String nomeArquivo) {
    }

    /** Lê a análise e confere a empresa; empresa alheia vira "análise não encontrada" (404). */
    private AnaliseFiscal acessivel(Long analiseId) {
        return tx.execute(s -> {
            AnaliseFiscal encontrada = repository.findById(analiseId).orElseThrow(() -> naoEncontrada(analiseId));
            try {
                acesso.clienteAcessivel(encontrada.getCliente().getId());
            } catch (RecursoNaoEncontradoException e) {
                throw naoEncontrada(analiseId);
            }
            encontrada.getCliente().getRazaoSocial(); // carrega a empresa (usada no relatório, fora da transação)
            return encontrada;
        });
    }

    private AnaliseDetalhe detalhe(AnaliseFiscal a) {
        MercadoriaEntradaDto entrada = new MercadoriaEntradaDto(a.getMercadoria(), a.getDescricao(), a.getComposicao(),
                a.getFinalidade(), a.getCaracteristicas(), a.getNcmAtual());
        ResultadoAnaliseFiscal resultado = a.getResultadoJson() == null ? null : ler(a.getResultadoJson(), ResultadoAnaliseFiscal.class);
        List<Etapa> historico = processador.ler(a.getHistoricoJson());
        List<Anexo> anexos = a.getAnexosJson() == null ? List.of() : ler(a.getAnexosJson(), LISTA_ANEXOS);
        return new AnaliseDetalhe(resumo(a), entrada, anexos, historico, resultado, a.getMensagem(),
                a.getResultadoJson() == null ? new Relatorio(false, null)
                        : new Relatorio(true, "/api/analises-fiscais/" + a.getId() + "/relatorio"));
    }

    // ---------------- apoio ----------------

    private static AnaliseResumo resumo(AnaliseFiscal a) {
        return new AnaliseResumo(a.getId(), a.getCliente().getId(), a.getMercadoria(), a.getNcmSugerida(), a.getStatus(),
                a.getCriadaEm(), a.getAtualizadaEm(), a.getResultadoJson() != null);
    }

    private static void validarArquivos(List<MultipartFile> arquivos) {
        if (arquivos.size() > MAX_ARQUIVOS) {
            throw ApiException.requisicaoInvalida("Envie no máximo " + MAX_ARQUIVOS + " arquivos.");
        }
        for (MultipartFile f : arquivos) {
            if (f.getSize() > MAX_BYTES_ARQUIVO) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo muito grande",
                        "\"" + nome(f) + "\" passa de 10 MB.");
            }
            if (!FORMATOS.contains(extensao(nome(f)))) {
                throw ApiException.requisicaoInvalida("Formato não aceito: \"" + nome(f)
                        + "\". Use pdf, png, jpg, jpeg, webp, doc, docx, xls, xlsx ou txt.");
            }
        }
    }

    private static String nome(MultipartFile f) {
        String n = f.getOriginalFilename() == null ? "arquivo" : f.getOriginalFilename();
        // só o nome, sem caminho (alguns navegadores mandam o caminho completo)
        n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1);
        return n.length() > 200 ? n.substring(0, 200) : n;
    }

    private static String extensao(String nome) {
        int i = nome.lastIndexOf('.');
        return i < 0 ? "" : nome.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] bytes(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw ApiException.requisicaoInvalida("Não foi possível ler o arquivo \"" + nome(f) + "\".");
        }
    }

    private static StatusAnalise status(String s) {
        try {
            return StatusAnalise.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.requisicaoInvalida("status inválido: " + s);
        }
    }

    private static LocalDate data(String s, String campo) {
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.requisicaoInvalida(campo + " deve estar no formato AAAA-MM-DD.");
        }
    }

    private static Instant inicioDoDia(LocalDate d) {
        return d.atStartOfDay(BRASILIA).toInstant();
    }

    private static String vazioComoNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static RecursoNaoEncontradoException naoEncontrada(Long id) {
        return new RecursoNaoEncontradoException("Análise " + id + " não encontrada");
    }

    private String escrever(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T ler(String texto, Class<T> tipo) {
        try {
            return json.readValue(texto, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Resultado da análise ilegível", e);
        }
    }

    private <T> T ler(String texto, TypeReference<T> tipo) {
        try {
            return json.readValue(texto, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Anexos da análise ilegíveis", e);
        }
    }
}

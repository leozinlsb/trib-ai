package br.com.tribia.apipublica.servico;

import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.AliquotasNominais;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.ItemSimulado;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.ItemSimular;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.ProdutoClassificado;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.ProdutoClassificar;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.RespostaClassificacao;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.RespostaSimulacao;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.SolicitacaoClassificacao;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.SolicitacaoSimulacao;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.Totais;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos;
import br.com.tribia.apipublica.model.ClassificacaoAvulsaApi;
import br.com.tribia.apipublica.repository.ChaveApiRepository;
import br.com.tribia.apipublica.repository.ClassificacaoAvulsaApiRepository;
import br.com.tribia.apipublica.seguranca.IntegradorAutenticado;
import br.com.tribia.apipublica.web.ApiPublicaException;
import br.com.tribia.client.calculadora.ResultadoCalculo.AliquotasAplicadas;
import br.com.tribia.exception.ApiException;
import br.com.tribia.model.Operacao;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.security.EscopoIntegracao;
import br.com.tribia.service.calculo.CalculoService;
import br.com.tribia.service.calculo.CalculoService.ItemSimuladoResultado;
import br.com.tribia.service.calculo.CalculoService.ResultadoSimulacao;
import br.com.tribia.service.classificacao.ClassificacaoService;
import br.com.tribia.service.classificacao.ClassificacaoService.ProdutoAvulso;
import br.com.tribia.service.classificacao.ClassificacaoService.ResultadoAvulso;
import br.com.tribia.service.classificacao.ClassificacaoService.SugestaoAvulsa;
import br.com.tribia.service.classificacao.CriterioRevisao;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaCClassTrib.CClassTrib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * API pública, fase 2: classificação de produtos avulsos e simulação do cálculo de 2027, pelos mesmos serviços da
 * plataforma ({@link ClassificacaoService#classificarAvulsos}, {@link CalculoService#simular}) dentro de um
 * {@link EscopoIntegracao} preso à empresa da chave. As regras ainda aguardam validação profissional (S5, R2,
 * V1–V6): as respostas saem como sugestão (classificação) ou projeção (cálculo), com avisos.
 */
@Service
public class ApiPublicaFase2Service {

    private static final Logger log = LoggerFactory.getLogger(ApiPublicaFase2Service.class);

    static final List<String> AVISOS_CLASSIFICACAO = List.of(
            "Sugestões automáticas (IA, cache, regra): não são classificação fiscal definitiva. Confirme com um "
                    + "profissional antes de usar em documento fiscal.",
            "Regras de enquadramento da reforma ainda pendentes de validação profissional (ex.: medicamentos, itens "
                    + "de higiene e limpeza, insumos agropecuários).");

    static final List<String> AVISOS_SIMULACAO = List.of(
            "Simulação de 2027: projeção pendente de validação fiscal. Alíquota da CBS estimada e base sem ICMS, PIS e "
                    + "Cofins informados (hipótese a confirmar com especialista). Não é apuração definitiva.",
            "O crédito de COMPRA supõe fornecedor do regime regular, pagamento confirmado e enquadramento igual ao "
                    + "destacado na nota; na apuração real ele pode ser menor.");

    private final ClassificacaoService classificacao;
    private final CalculoService calculo;
    private final TabelaCClassTrib tabela;
    private final CriterioRevisao criterio;
    private final ConsumoIaApi consumo;
    private final ClassificacaoAvulsaApiRepository avulsas;
    private final ChaveApiRepository chaves;
    private final ClienteRepository clientes;
    private final TransactionTemplate tx;

    public ApiPublicaFase2Service(ClassificacaoService classificacao, CalculoService calculo, TabelaCClassTrib tabela,
                                  CriterioRevisao criterio, ConsumoIaApi consumo,
                                  ClassificacaoAvulsaApiRepository avulsas, ChaveApiRepository chaves,
                                  ClienteRepository clientes, PlatformTransactionManager transacoes) {
        this.classificacao = classificacao;
        this.calculo = calculo;
        this.tabela = tabela;
        this.criterio = criterio;
        this.consumo = consumo;
        this.avulsas = avulsas;
        this.chaves = chaves;
        this.clientes = clientes;
        this.tx = new TransactionTemplate(transacoes);
    }

    // ---------------- classificação ----------------

    public RespostaClassificacao classificar(IntegradorAutenticado quem, SolicitacaoClassificacao pedido) {
        List<ProdutoAvulso> produtos = pedido.produtos().stream()
                .map(p -> new ProdutoAvulso(p.ncm().replaceAll("\\D", ""), p.descricao().trim(), vazio(p.unidade()),
                        p.valorUnitario()))
                .toList();
        ResultadoAvulso r;
        synchronized (consumo.trava(quem.chaveId())) {
            long usados = consumo.itensHoje(quem.chaveId());
            if (usados >= quem.cotaDiariaItensIa()) {
                throw new ApiPublicaException(HttpStatus.TOO_MANY_REQUESTS, "COTA_DIARIA_ITENS_IA_EXCEDIDA",
                        "Cota diária de itens para a IA excedida",
                        "Esta chave já enviou " + usados + " itens para a IA hoje (cota: " + quem.cotaDiariaItensIa()
                                + "). A cota renova à meia-noite (horário de Brasília).",
                        Map.of(), Map.of("Retry-After", String.valueOf(ConsumoIaApi.segundosAteRenovar())));
            }
            int restante = (int) Math.min(Integer.MAX_VALUE, quem.cotaDiariaItensIa() - usados);
            r = EscopoIntegracao.executar(quem.clienteId(),
                    () -> classificacao.classificarAvulsos(quem.clienteId(), produtos, restante));
            int itensIa = r.itensIa();
            tx.executeWithoutResult(s -> avulsas.save(new ClassificacaoAvulsaApi(
                    chaves.getReferenceById(quem.chaveId()), clientes.getReferenceById(quem.clienteId()),
                    produtos.size(), itensIa, Instant.now())));
        }
        log.info("API pública: {} produto(s) classificado(s) avulsos (chave {}, itens para a IA: {})", produtos.size(),
                quem.prefixo(), r.itensIa());

        List<ProdutoClassificado> saida = new ArrayList<>();
        for (int i = 0; i < pedido.produtos().size(); i++) {
            ProdutoClassificar p = pedido.produtos().get(i);
            saida.add(produto(p, produtos.get(i).ncm(), r.sugestoes().get(i)));
        }
        List<String> avisos = new ArrayList<>(AVISOS_CLASSIFICACAO);
        r.avisos().stream().filter(a -> !avisos.contains(a)).forEach(avisos::add);
        return new RespostaClassificacao(ApiPublicaFase2Dtos.NATUREZA_SUGESTAO, saida, r.itensIa(), avisos);
    }

    private ProdutoClassificado produto(ProdutoClassificar p, String ncm, SugestaoAvulsa s) {
        if (s == null) {
            return new ProdutoClassificado(p.referencia(), ncm, p.descricao(), "SEM_CLASSIFICACAO", null, null, null,
                    null, null, null, null, null);
        }
        Optional<CClassTrib> oficial = tabela.buscar(s.cClassTrib());
        boolean confiancaBaixa = s.confianca() == null || s.confianca().compareTo(criterio.confiancaMinima()) < 0;
        String situacao = !s.aceita() || confiancaBaixa ? "PENDENTE_REVISAO" : "CONFIRMADA";
        return new ProdutoClassificado(p.referencia(), ncm, p.descricao(), situacao, s.cst(), s.cClassTrib(),
                oficial.map(CClassTrib::nome).orElse(null), oficial.map(c -> c.regime().name()).orElse(null),
                oficial.map(CClassTrib::descricaoRegime).orElse(null), s.origem().name(), s.confianca(),
                s.justificativa());
    }

    // ---------------- simulação ----------------

    public RespostaSimulacao simular(IntegradorAutenticado quem, SolicitacaoSimulacao pedido) {
        Map<String, String> erros = new LinkedHashMap<>();
        List<CalculoService.ItemSimulado> itens = new ArrayList<>();
        List<CClassTrib> codigos = new ArrayList<>();
        for (int i = 0; i < pedido.itens().size(); i++) {
            ItemSimular it = pedido.itens().get(i);
            Optional<CClassTrib> oficial = tabela.buscar(it.cClassTrib()).filter(c -> tabela.opcaoNfe(c.codigo()));
            if (oficial.isEmpty()) {
                erros.put("itens[" + i + "].cClassTrib", "cClassTrib " + it.cClassTrib()
                        + " não é uma opção válida para NF-e na tabela oficial.");
                continue;
            }
            String cst = vazio(it.cst()) == null ? oficial.get().cst() : it.cst();
            if (!cst.equals(oficial.get().cst())) {
                erros.put("itens[" + i + "].cst", "O cClassTrib " + it.cClassTrib() + " pertence ao CST "
                        + oficial.get().cst() + ", não ao " + cst + ".");
                continue;
            }
            String ncm = vazio(it.ncm()) == null ? null : it.ncm().replaceAll("\\D", "");
            itens.add(new CalculoService.ItemSimulado(ncm, it.quantidade() == null ? BigDecimal.ONE : it.quantidade(),
                    vazio(it.unidade()) == null ? "UN" : it.unidade(), it.valor(), zero(it.icms()), zero(it.pis()),
                    zero(it.cofins()), cst, oficial.get().codigo()));
            codigos.add(oficial.get());
        }
        if (!erros.isEmpty()) {
            throw new ApiPublicaException(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", "Dados inválidos",
                    erros.values().iterator().next(), Map.of("campos", erros), Map.of());
        }

        Operacao operacao = Operacao.valueOf(pedido.operacao());
        ResultadoSimulacao r;
        try {
            r = EscopoIntegracao.executar(quem.clienteId(), () -> calculo.simular(quem.clienteId(), operacao,
                    vazio(pedido.municipioDestino()), vazio(pedido.ufDestino()), itens, pedido.aliquotaCbs()));
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.SERVICE_UNAVAILABLE) {
                throw new ApiPublicaException(HttpStatus.SERVICE_UNAVAILABLE, "CALCULADORA_INDISPONIVEL",
                        "Calculadora indisponível", e.getMessage(), Map.of(), Map.of("Retry-After", "30"));
            }
            if (e.getStatus().is4xxClientError()) {
                throw new ApiPublicaException(HttpStatus.UNPROCESSABLE_ENTITY, "CALCULO_RECUSADO", "Cálculo recusado",
                        e.getMessage());
            }
            throw e;
        }

        boolean venda = operacao == Operacao.VENDA;
        List<ItemSimulado> saida = new ArrayList<>();
        BigDecimal base = BigDecimal.ZERO, cbs = BigDecimal.ZERO, ibs = BigDecimal.ZERO, is = BigDecimal.ZERO,
                debito = BigDecimal.ZERO, credito = BigDecimal.ZERO;
        for (int i = 0; i < itens.size(); i++) {
            ItemSimuladoResultado x = r.itens().get(i);
            ItemSimular entrada = pedido.itens().get(i);
            CClassTrib c = codigos.get(i);
            if (x == null) {
                saida.add(new ItemSimulado(entrada.referencia(), itens.get(i).ncm(), itens.get(i).cst(), c.codigo(),
                        c.regime().name(), c.descricaoRegime(), null, null, null, null, null, false, null, null, null,
                        null, null, null, null));
                continue;
            }
            var t = x.tributos();
            AliquotasAplicadas a = x.aliquotas();
            BigDecimal deb = venda ? t.totalDebito() : null;
            BigDecimal cred = venda ? null : t.totalCredito();
            saida.add(new ItemSimulado(entrada.referencia(), itens.get(i).ncm(), itens.get(i).cst(), c.codigo(),
                    c.regime().name(), c.descricaoRegime(), x.base(), t.vCbs(), t.vIbsUf(), t.vIbsMun(), t.vIs(),
                    x.sujeitoIs(), a.pCbs(), a.pIbsUf(), a.pIbsMun(), a.reducaoCbs(), a.reducaoIbs(), deb, cred));
            base = base.add(x.base());
            cbs = cbs.add(t.vCbs());
            ibs = ibs.add(t.vIbs());
            is = is.add(t.vIs());
            if (venda) {
                debito = debito.add(t.totalDebito());
            } else {
                credito = credito.add(t.totalCredito());
            }
        }
        List<String> avisos = new ArrayList<>(AVISOS_SIMULACAO);
        r.avisos().stream().filter(a -> !avisos.contains(a)).forEach(avisos::add);
        var n = r.nominais();
        return new RespostaSimulacao(ApiPublicaNotasDtos.NATUREZA_PROJECAO, operacao.name(), r.origem().name(),
                new AliquotasNominais(n.cbs(), n.ibsUf(), n.ibsMun()), saida,
                new Totais(base, cbs, ibs, is, venda ? debito : null, venda ? null : credito), avisos);
    }

    private static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

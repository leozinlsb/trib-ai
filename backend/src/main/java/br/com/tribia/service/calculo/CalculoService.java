package br.com.tribia.service.calculo;

import br.com.tribia.client.calculadora.CalculadoraException;
import br.com.tribia.client.calculadora.CalculadoraOficialClient;
import br.com.tribia.client.calculadora.CalculadoraSimplificadaClient;
import br.com.tribia.client.calculadora.OperacaoCalculo;
import br.com.tribia.client.calculadora.OperacaoCalculo.AliquotasNominais;
import br.com.tribia.client.calculadora.OperacaoCalculo.ImpostoSeletivo;
import br.com.tribia.client.calculadora.OperacaoCalculo.ItemCalculo;
import br.com.tribia.client.calculadora.ResultadoCalculo;
import br.com.tribia.client.calculadora.ResultadoCalculo.ItemCalculado;
import br.com.tribia.config.AliquotasProperties;
import br.com.tribia.config.CalculoProperties;
import br.com.tribia.dto.CalculoClienteDto;
import br.com.tribia.dto.CalculoNotaDto;
import br.com.tribia.dto.ComparativoDto;
import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.Calculo;
import br.com.tribia.model.Classificacao;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.Item;
import br.com.tribia.model.Natureza;
import br.com.tribia.model.Nota;
import br.com.tribia.model.Operacao;
import br.com.tribia.model.Regime;
import br.com.tribia.repository.CalculoRepository;
import br.com.tribia.repository.ClassificacaoRepository;
import br.com.tribia.repository.ItemRepository;
import br.com.tribia.repository.NotaRepository;
import br.com.tribia.security.AcessoService;
import br.com.tribia.service.ClienteService;
import br.com.tribia.service.apuracao.Apuracao;
import br.com.tribia.service.apuracao.Comparativo;
import br.com.tribia.service.apuracao.ItemTributavel;
import br.com.tribia.service.apuracao.RegrasApuracao;
import br.com.tribia.service.apuracao.Tributos2027;
import br.com.tribia.service.tabelas.TabelaImpostoSeletivo;
import br.com.tribia.util.CnpjUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calcula 2027 para os itens classificados de uma nota e grava um {@link Calculo} por item, com o imposto de hoje e
 * o de 2027 já como débito ou crédito (e com sinal negativo nas devoluções, que estornam).
 *
 * Três fases, para a chamada à calculadora oficial não segurar uma transação aberta: (1) lê a nota e monta a
 * operação; (2) calcula, fora de transação; (3) grava. Modo AUTO: se a oficial falhar, usa a simplificada e avisa.
 * Recalcular é idempotente: os cálculos anteriores da nota são substituídos.
 */
@Service
public class CalculoService {

    private static final Logger log = LoggerFactory.getLogger(CalculoService.class);
    private static final ZoneOffset BRASILIA = ZoneOffset.ofHours(-3);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final NotaRepository notaRepository;
    private final ItemRepository itemRepository;
    private final ClassificacaoRepository classificacaoRepository;
    private final CalculoRepository calculoRepository;
    private final ClienteService clienteService;
    private final CalculadoraOficialClient oficial;
    private final CalculadoraSimplificadaClient simplificada;
    private final RegrasApuracao regras;
    private final TabelaImpostoSeletivo tabelaIs;
    private final AliquotasProperties aliquotas;
    private final CalculoProperties props;
    private final TransactionTemplate tx;
    private final AcessoService acesso;

    public CalculoService(NotaRepository notaRepository, ItemRepository itemRepository,
                          ClassificacaoRepository classificacaoRepository, CalculoRepository calculoRepository,
                          ClienteService clienteService, CalculadoraOficialClient oficial,
                          CalculadoraSimplificadaClient simplificada, RegrasApuracao regras,
                          TabelaImpostoSeletivo tabelaIs, AliquotasProperties aliquotas, CalculoProperties props,
                           PlatformTransactionManager transacoes, AcessoService acesso) {
        this.notaRepository = notaRepository;
        this.itemRepository = itemRepository;
        this.classificacaoRepository = classificacaoRepository;
        this.calculoRepository = calculoRepository;
        this.clienteService = clienteService;
        this.oficial = oficial;
        this.simplificada = simplificada;
        this.regras = regras;
        this.tabelaIs = tabelaIs;
        this.aliquotas = aliquotas;
        this.props = props;
        this.tx = new TransactionTemplate(transacoes);
        this.acesso = acesso;
    }

    /** Tudo o que a fase 3 precisa da nota, lido na fase 1 (sem depender de entidades carregadas). */
    private record Preparo(Long notaId, OperacaoCalculo operacaoCalculo, List<Long> idsItens, List<Integer> pendentes,
                           Map<Integer, ItemPreparado> porNumero, Operacao operacao, Regime regime,
                           boolean emitidaPeloCliente, boolean fornecedorSimples, boolean pagamentoConfirmado) {
    }

    private record ItemPreparado(Long itemId, ItemTributavel tributavel, boolean sujeitoIs) {
    }

    /**
     * @param cbsCenario alíquota da CBS (%) para simular um cenário; null usa a configurada
     */
    public CalculoNotaDto calcular(Long notaId, BigDecimal cbsCenario) {
        acesso.notaAcessivel(notaId);
        AliquotasNominais nominais = nominais(cbsCenario);
        Preparo p = tx.execute(s -> preparar(notaId, nominais));

        Set<String> avisos = new LinkedHashSet<>(avisosDeAliquota(nominais, cbsCenario));
        if (!p.pendentes().isEmpty()) {
            avisos.add(p.pendentes().size() + " item(ns) sem classificação ficaram fora do cálculo: classifique a nota antes.");
        }
        acesso.notaAcessivel(notaId);
        ResultadoCalculo r = p.operacaoCalculo().itens().isEmpty() ? null : executar(p.operacaoCalculo(), avisos);

        Comparativo total = tx.execute(s -> gravar(p, r, avisos));
        return new CalculoNotaDto(notaId, r == null ? null : r.origem(), r != null && r.simulado(), nominais.cbs(),
                r == null ? 0 : r.itens().size(), p.pendentes(), List.copyOf(avisos), ComparativoDto.de(total));
    }

    /**
     * Recalcula a nota mantendo o cenário de CBS com que ela foi calculada da última vez (se havia um).
     * Usado depois de classificar ou revisar, para o painel refletir a mudança sem perder o cenário escolhido.
     */
    public CalculoNotaDto recalcular(Long notaId) {
        acesso.notaAcessivel(notaId);
        BigDecimal configurada = aliquotas.ano2027().cbsEfetiva();
        BigDecimal cenario = calculoRepository.findByNota(notaId).stream()
                .map(Calculo::getPCbs)
                .filter(p -> p != null && p.compareTo(configurada) != 0)
                .findFirst().orElse(null);
        return calcular(notaId, cenario);
    }

    /** Recalcula todas as notas do cliente (ex.: para trocar o cenário da CBS). Uma transação por nota. */
    public CalculoClienteDto calcularCliente(Long clienteId, BigDecimal cbsCenario) {
        clienteService.buscar(clienteId);
        List<Long> notas = notaRepository.buscar(clienteId, null, null).stream().map(Nota::getId).toList();
        Set<String> avisos = new LinkedHashSet<>();
        Comparativo total = Comparativo.ZERO;
        int calculados = 0;
        int pendentes = 0;
        for (Long id : notas) {
            CalculoNotaDto r = calcular(id, cbsCenario);
            avisos.addAll(r.avisos());
            calculados += r.itensCalculados();
            pendentes += r.itensPendentes().size();
            total = total.somar(paraComparativo(r.comparativo()));
        }
        return new CalculoClienteDto(clienteId, nominais(cbsCenario).cbs(), notas.size(), calculados, pendentes,
                List.copyOf(avisos), ComparativoDto.de(total));
    }

    /**
     * Marca se a compra foi paga ao fornecedor. Sem pagamento confirmado, a compra não gera crédito de 2027
     * (LC 214, art. 47: o crédito depende da extinção do débito do fornecedor). Recalcula a nota.
     */
    public CalculoNotaDto definirPagamento(Long notaId, boolean confirmado) {
        tx.executeWithoutResult(s -> {
            acesso.notaAcessivel(notaId);
            Nota n = notaRepository.findById(notaId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Nota " + notaId + " não encontrada"));
            if (n.getOperacao().natureza() != Natureza.CREDITO) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Operação inválida",
                        "Só compras (e devoluções de compra) geram crédito: o pagamento não se aplica a esta nota.");
            }
            n.setPagamentoConfirmado(confirmado);
        });
        return recalcular(notaId);
    }

    // ---------------- fase 1: lê e monta a operação ----------------

    private Preparo preparar(Long notaId, AliquotasNominais nominais) {
        acesso.notaAcessivel(notaId);
        Nota nota = notaRepository.buscarComItens(notaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nota " + notaId + " não encontrada"));
        Cliente cliente = nota.getCliente();
        List<Long> ids = nota.getItens().stream().map(Item::getId).toList();
        Map<Long, Classificacao> classificacoes = classificacaoRepository.findByItemIdIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getItem().getId(), Function.identity()));

        Operacao operacao = nota.getOperacao();
        // IS monofásico: só o fabricante paga, e só quando vende
        boolean cobraIs = cliente.isFabricante() && operacao == Operacao.VENDA;
        List<ItemCalculo> itensCalculo = new ArrayList<>();
        Map<Integer, ItemPreparado> porNumero = new LinkedHashMap<>();
        List<Integer> pendentes = new ArrayList<>();
        for (Item i : nota.getItens()) {
            Classificacao c = classificacoes.get(i.getId());
            if (c == null) {
                pendentes.add(i.getNItem());
                continue;
            }
            boolean sujeitoIs = tabelaIs.aliquota(i.getNcm(), props.dataFatoGerador()).isPresent();
            ImpostoSeletivo is = !sujeitoIs ? null : cobraIs ? new ImpostoSeletivo("000", "000001") : ImpostoSeletivo.REVENDA;
            BigDecimal base = RegrasApuracao.base2027(i.valorDaOperacao(), i.getVIcms(), i.getVPis(), i.getVCofins(),
                    props.excluirTributosDaBase());
            itensCalculo.add(new ItemCalculo(i.getNItem(), i.getNcm(), i.getQuantidade(), i.getUnidade(), base,
                    c.getCst(), c.getCClassTrib(), is));
            porNumero.put(i.getNItem(), new ItemPreparado(i.getId(), ItemTributavel.de(i), sujeitoIs));
        }
        // IBS é devido no destino: município de quem recebe a mercadoria (ver Nota.municipioDestino)
        String municipio = nota.getMunicipioDestino() != null ? nota.getMunicipioDestino() : cliente.getCodigoMunicipio();
        String uf = nota.getUfDestino() != null ? nota.getUfDestino() : cliente.getUf();
        OperacaoCalculo op = new OperacaoCalculo("nota-" + notaId, dataFatoGerador(), municipio, uf, itensCalculo, nominais);
        boolean emitidaPeloCliente = CnpjUtil.somenteDigitos(cliente.getCnpj()).equals(nota.getEmitenteCnpj());
        return new Preparo(notaId, op, ids, pendentes, porNumero, operacao, cliente.getRegime(), emitidaPeloCliente,
                !emitidaPeloCliente && nota.emitenteDoSimples(), nota.isPagamentoConfirmado());
    }

    // ---------------- fase 2: calcula (fora de transação) ----------------

    private ResultadoCalculo executar(OperacaoCalculo op, Set<String> avisos) {
        ResultadoCalculo r = switch (props.modo()) {
            case SIMPLIFICADA -> simplificada.calcular(op);
            case OFICIAL -> {
                try {
                    yield oficial.calcular(op);
                } catch (CalculadoraException e) {
                    throw e.getTipo() == CalculadoraException.Tipo.INDISPONIVEL
                            ? new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Calculadora indisponível", e.getMessage())
                            : new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Cálculo recusado", e.getMessage());
                }
            }
            case AUTO -> {
                try {
                    yield oficial.calcular(op);
                } catch (CalculadoraException e) {
                    log.warn("{}: usando o cálculo simplificado ({})", op.id(), e.getMessage());
                    avisos.add((e.getTipo() == CalculadoraException.Tipo.INDISPONIVEL
                            ? "Calculadora oficial fora do ar"
                            : e.getMessage()) + ": valores estimados pelo método simplificado; exigem revisão fiscal.");
                    yield simplificada.calcular(op);
                }
            }
        };
        avisos.addAll(r.avisos());
        return r;
    }

    // ---------------- fase 3: grava ----------------

    private Comparativo gravar(Preparo p, ResultadoCalculo r, Set<String> avisos) {
        acesso.notaAcessivel(p.notaId());
        calculoRepository.apagarDosItens(p.idsItens());
        if (r == null) {
            return Comparativo.ZERO;
        }
        boolean credito = p.operacao().natureza() == Natureza.CREDITO;
        boolean semCreditoPorSimples = credito && p.fornecedorSimples()
                && props.creditoFornecedorSimples() == CalculoProperties.CreditoSimples.SEM_CREDITO;
        boolean semCreditoPorPagamento = credito && !p.pagamentoConfirmado();
        if (semCreditoPorSimples) {
            avisos.add("Fornecedor do Simples Nacional: o crédito de 2027 não foi considerado, porque é limitado ao que "
                    + "ele recolheu no Simples (LC 214, art. 47) e esse valor não vem na nota.");
        }
        if (semCreditoPorPagamento) {
            avisos.add("Pagamento ao fornecedor não confirmado: sem crédito de 2027 (LC 214, art. 47).");
        }
        BigDecimal sinal = BigDecimal.valueOf(p.operacao().sinal());

        Comparativo total = Comparativo.ZERO;
        for (ItemCalculado ic : r.itens()) {
            ItemPreparado ip = p.porNumero().get(ic.numero());
            ItemTributavel it = ip.tributavel();
            Tributos2027 t = ic.tributos();
            BigDecimal hoje = regras.pisCofinsHoje(p.regime(), p.operacao(), it, p.emitidaPeloCliente());
            BigDecimal ano2027 = semCreditoPorSimples || semCreditoPorPagamento ? ZERO
                    : (p.operacao().natureza() == Natureza.DEBITO ? t.totalDebito()
                    : (it.creditavel() ? t.totalCredito() : ZERO));
            var a = ic.aliquotas();
            Calculo c = calculoRepository.save(new Calculo(itemRepository.getReferenceById(ip.itemId()),
                    p.operacao().natureza(), r.origem(), t.vCbs(), t.vIbsUf(), t.vIbsMun(), t.vIs(), a.pCbs(), a.pIbsUf(),
                    a.pIbsMun(), a.reducaoCbs(), a.reducaoIbs(), a.pIs(), ip.sujeitoIs(),
                    hoje.multiply(sinal), ano2027.multiply(sinal), r.simulado()));
            total = total.somar(comparativo(c));
        }
        return total;
    }

    // ---------------- apoio ----------------

    /**
     * Contribuição de um item para o comparativo: hoje e 2027 na coluna de débito (venda e devolução de venda) ou de
     * crédito (compra e devolução de compra). Nas devoluções o valor gravado já é negativo (estorno).
     */
    public static Comparativo comparativo(Calculo c) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return c.getNatureza() == Natureza.DEBITO
                ? new Comparativo(new Apuracao(c.getImpostoHoje(), zero), new Apuracao(c.getImposto2027(), zero))
                : new Comparativo(new Apuracao(zero, c.getImpostoHoje()), new Apuracao(zero, c.getImposto2027()));
    }

    private static Comparativo paraComparativo(ComparativoDto d) {
        return new Comparativo(new Apuracao(d.hoje().debito(), d.hoje().credito()),
                new Apuracao(d.ano2027().debito(), d.ano2027().credito()));
    }

    private AliquotasNominais nominais(BigDecimal cbsCenario) {
        AliquotasProperties.Ano2027 a = aliquotas.ano2027();
        if (cbsCenario != null && (cbsCenario.signum() <= 0 || cbsCenario.compareTo(BigDecimal.valueOf(30)) > 0)) {
            throw ApiException.requisicaoInvalida("Cenário de CBS deve estar entre 0 e 30 (%): " + cbsCenario);
        }
        return new AliquotasNominais(cbsCenario != null ? cbsCenario : a.cbsEfetiva(), a.ibsUf(), a.ibsMun());
    }

    private List<String> avisosDeAliquota(AliquotasNominais n, BigDecimal cbsCenario) {
        List<String> avisos = new ArrayList<>();
        avisos.add("Comparativo tributário é estimativa e exige revisão profissional; não é apuração fiscal definitiva.");
        AliquotasProperties.Ano2027 a = aliquotas.ano2027();
        if (cbsCenario != null) {
            avisos.add("Cenário simulado: CBS de " + cbsCenario.stripTrailingZeros().toPlainString() + "% em 2027.");
        } else if (a.cbsEstimativa()) {
            avisos.add("Alíquota da CBS 2027 (" + n.cbs().stripTrailingZeros().toPlainString()
                    + "%) é estimativa: o valor oficial ainda não foi fixado.");
        }
        if (cbsCenario == null && a.reducaoCbsTransicao() != null && a.reducaoCbsTransicao().signum() > 0) {
            avisos.add("CBS reduzida em " + a.reducaoCbsTransicao().stripTrailingZeros().toPlainString()
                    + " p.p. pela regra de transição de 2027-2028.");
        }
        return avisos;
    }

    private OffsetDateTime dataFatoGerador() {
        return OffsetDateTime.of(props.dataFatoGerador(), LocalTime.of(10, 0), BRASILIA);
    }
}

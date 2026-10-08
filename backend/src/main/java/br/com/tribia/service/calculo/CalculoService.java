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
import br.com.tribia.repository.CalculoRepository;
import br.com.tribia.repository.ClassificacaoRepository;
import br.com.tribia.repository.NotaRepository;
import br.com.tribia.service.ClienteService;
import br.com.tribia.service.apuracao.Apuracao;
import br.com.tribia.service.apuracao.Comparativo;
import br.com.tribia.service.apuracao.ItemTributavel;
import br.com.tribia.service.apuracao.RegrasApuracao;
import br.com.tribia.service.tabelas.TabelaImpostoSeletivo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calcula 2027 para os itens classificados de uma nota e grava um {@link Calculo} por item, com o imposto
 * de hoje e o de 2027 já na coluna certa (débito na saída, crédito na entrada).
 *
 * Modo AUTO: tenta a calculadora oficial; se ela estiver fora do ar ou recusar a operação, usa a simplificada
 * e registra o motivo nos avisos. Recalcular é idempotente: os cálculos anteriores da nota são substituídos.
 */
@Service
public class CalculoService {

    private static final Logger log = LoggerFactory.getLogger(CalculoService.class);
    private static final ZoneOffset BRASILIA = ZoneOffset.ofHours(-3);

    private final NotaRepository notaRepository;
    private final ClassificacaoRepository classificacaoRepository;
    private final CalculoRepository calculoRepository;
    private final ClienteService clienteService;
    private final CalculadoraOficialClient oficial;
    private final CalculadoraSimplificadaClient simplificada;
    private final RegrasApuracao regras;
    private final TabelaImpostoSeletivo tabelaIs;
    private final AliquotasProperties aliquotas;
    private final CalculoProperties props;

    public CalculoService(NotaRepository notaRepository, ClassificacaoRepository classificacaoRepository,
                          CalculoRepository calculoRepository, ClienteService clienteService,
                          CalculadoraOficialClient oficial, CalculadoraSimplificadaClient simplificada,
                          RegrasApuracao regras, TabelaImpostoSeletivo tabelaIs, AliquotasProperties aliquotas,
                          CalculoProperties props) {
        this.notaRepository = notaRepository;
        this.classificacaoRepository = classificacaoRepository;
        this.calculoRepository = calculoRepository;
        this.clienteService = clienteService;
        this.oficial = oficial;
        this.simplificada = simplificada;
        this.regras = regras;
        this.tabelaIs = tabelaIs;
        this.aliquotas = aliquotas;
        this.props = props;
    }

    /**
     * @param cbsCenario alíquota da CBS (%) para simular um cenário; null usa tribia.aliquotas.ano2027.cbs
     */
    @Transactional
    public CalculoNotaDto calcular(Long notaId, BigDecimal cbsCenario) {
        Nota nota = notaRepository.buscarComItens(notaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nota " + notaId + " não encontrada"));
        AliquotasNominais nominais = nominais(cbsCenario);

        List<Long> idsItens = nota.getItens().stream().map(Item::getId).toList();
        Map<Long, Classificacao> classificacoes = classificacaoRepository.findByItemIdIn(idsItens).stream()
                .collect(Collectors.toMap(c -> c.getItem().getId(), Function.identity()));
        List<Item> calculaveis = nota.getItens().stream().filter(i -> classificacoes.containsKey(i.getId())).toList();
        List<Integer> pendentes = nota.getItens().stream().filter(i -> !classificacoes.containsKey(i.getId()))
                .map(Item::getNItem).toList();

        Set<String> avisos = new LinkedHashSet<>(avisosDeAliquota(nominais, cbsCenario));
        if (!pendentes.isEmpty()) {
            avisos.add(pendentes.size() + " item(ns) sem classificação ficaram fora do cálculo: classifique a nota antes.");
        }
        calculoRepository.apagarDosItens(idsItens);
        if (calculaveis.isEmpty()) {
            return new CalculoNotaDto(notaId, null, false, nominais.cbs(), 0, pendentes, List.copyOf(avisos),
                    ComparativoDto.de(Comparativo.ZERO));
        }

        Cliente cliente = nota.getCliente();
        OperacaoCalculo op = new OperacaoCalculo("nota-" + notaId, dataFatoGerador(), cliente.getCodigoMunicipio(),
                cliente.getUf(), calculaveis.stream().map(i -> itemCalculo(i, classificacoes.get(i.getId()))).toList(),
                nominais);
        ResultadoCalculo r = executar(op, avisos);

        Map<Integer, Item> porNumero = calculaveis.stream().collect(Collectors.toMap(Item::getNItem, Function.identity()));
        Comparativo total = Comparativo.ZERO;
        for (ItemCalculado ic : r.itens()) {
            Item item = porNumero.get(ic.numero());
            Calculo c = calculoRepository.save(novoCalculo(nota, item, ic, r));
            total = total.somar(comparativo(c));
        }
        return new CalculoNotaDto(notaId, r.origem(), r.simulado(), nominais.cbs(), r.itens().size(), pendentes,
                List.copyOf(avisos), ComparativoDto.de(total));
    }

    /** Recalcula todas as notas do cliente (ex.: para trocar o cenário da CBS). */
    @Transactional
    public CalculoClienteDto calcularCliente(Long clienteId, BigDecimal cbsCenario) {
        clienteService.buscar(clienteId);
        List<Nota> notas = notaRepository.buscar(clienteId, null, null);
        Set<String> avisos = new LinkedHashSet<>();
        Comparativo total = Comparativo.ZERO;
        int calculados = 0;
        int pendentes = 0;
        for (Nota n : notas) {
            CalculoNotaDto r = calcular(n.getId(), cbsCenario);
            avisos.addAll(r.avisos());
            calculados += r.itensCalculados();
            pendentes += r.itensPendentes().size();
            total = total.somar(paraComparativo(r.comparativo()));
        }
        return new CalculoClienteDto(clienteId, nominais(cbsCenario).cbs(), notas.size(), calculados, pendentes,
                List.copyOf(avisos), ComparativoDto.de(total));
    }

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
                            : e.getMessage()) + ": valores calculados pelo método simplificado (mesmas fórmulas e tabela oficial).");
                    yield simplificada.calcular(op);
                }
            }
        };
        avisos.addAll(r.avisos());
        return r;
    }

    private ItemCalculo itemCalculo(Item i, Classificacao c) {
        // TODO os 3 clientes de demo são revendedores: no IS (monofásico) o fabricante paga e a revenda usa CST 200 /
        // cClassTrib 200007 (IS zero). Para um cliente fabricante, usar CST 000 / 000001.
        ImpostoSeletivo is = sujeitoIs(i) ? ImpostoSeletivo.REVENDA : null;
        return new ItemCalculo(i.getNItem(), i.getNcm(), i.getQuantidade(), i.getUnidade(), i.getValorTotal(),
                c.getCst(), c.getCClassTrib(), is);
    }

    private Calculo novoCalculo(Nota nota, Item item, ItemCalculado ic, ResultadoCalculo r) {
        ItemTributavel it = ItemTributavel.de(item);
        BigDecimal hoje = regras.pisCofinsHoje(nota.getCliente().getRegime(), nota.getTipo(), it);
        BigDecimal ano2027 = regras.imposto2027(nota.getTipo(), ic.tributos(), item.isCreditavel());
        var a = ic.aliquotas();
        var t = ic.tributos();
        return new Calculo(item, Natureza.de(nota.getTipo()), r.origem(), t.vCbs(), t.vIbsUf(), t.vIbsMun(), t.vIs(),
                a.pCbs(), a.pIbsUf(), a.pIbsMun(), a.reducaoCbs(), a.reducaoIbs(), a.pIs(), sujeitoIs(item),
                hoje, ano2027, r.simulado());
    }

    private boolean sujeitoIs(Item i) {
        return tabelaIs.aliquota(i.getNcm(), props.dataFatoGerador()).isPresent();
    }

    static Comparativo comparativo(Calculo c) {
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
        return new AliquotasNominais(cbsCenario != null ? cbsCenario : a.cbs(), a.ibsUf(), a.ibsMun());
    }

    private List<String> avisosDeAliquota(AliquotasNominais n, BigDecimal cbsCenario) {
        List<String> avisos = new ArrayList<>();
        if (cbsCenario != null) {
            avisos.add("Cenário simulado: CBS de " + cbsCenario.stripTrailingZeros().toPlainString() + "% em 2027.");
        } else if (aliquotas.ano2027().cbsEstimativa()) {
            avisos.add("Alíquota da CBS 2027 (" + n.cbs().stripTrailingZeros().toPlainString()
                    + "%) é estimativa: o valor oficial ainda não foi fixado.");
        }
        return avisos;
    }

    private OffsetDateTime dataFatoGerador() {
        return OffsetDateTime.of(props.dataFatoGerador(), LocalTime.of(10, 0), BRASILIA);
    }
}

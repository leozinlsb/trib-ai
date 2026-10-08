package br.com.tribia.service;

import br.com.tribia.client.calculadora.OrigemCalculo;
import br.com.tribia.config.AliquotasProperties;
import br.com.tribia.dto.ClienteListaDto;
import br.com.tribia.dto.ComparativoDto;
import br.com.tribia.dto.DashboardDto;
import br.com.tribia.dto.DashboardDto.ClienteResumoDto;
import br.com.tribia.dto.DashboardDto.PeriodoDto;
import br.com.tribia.dto.IndicadoresDto;
import br.com.tribia.exception.ApiException;
import br.com.tribia.model.Calculo;
import br.com.tribia.model.Classificacao;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.Item;
import br.com.tribia.model.Nota;
import br.com.tribia.model.TipoNota;
import br.com.tribia.repository.CalculoRepository;
import br.com.tribia.repository.ClassificacaoRepository;
import br.com.tribia.repository.ItemRepository;
import br.com.tribia.service.apuracao.Comparativo;
import br.com.tribia.service.calculo.CalculoService;
import br.com.tribia.service.classificacao.CriterioRevisao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agrega notas, classificações e cálculos de um cliente para o painel. Lê só o que está gravado: para refletir
 * uma classificação nova ou outro cenário de CBS, a nota (ou o cliente) precisa ser recalculada antes.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final String COMPETENCIA = "\\d{4}-(0[1-9]|1[0-2])";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ClienteService clienteService;
    private final ItemRepository itemRepository;
    private final ClassificacaoRepository classificacaoRepository;
    private final CalculoRepository calculoRepository;
    private final CriterioRevisao criterioRevisao;
    private final AliquotasProperties aliquotas;

    public DashboardService(ClienteService clienteService, ItemRepository itemRepository,
                            ClassificacaoRepository classificacaoRepository, CalculoRepository calculoRepository,
                            CriterioRevisao criterioRevisao, AliquotasProperties aliquotas) {
        this.clienteService = clienteService;
        this.itemRepository = itemRepository;
        this.classificacaoRepository = classificacaoRepository;
        this.calculoRepository = calculoRepository;
        this.criterioRevisao = criterioRevisao;
        this.aliquotas = aliquotas;
    }

    /**
     * @param de  competência inicial AAAA-MM (inclusive); null = desde a primeira nota
     * @param ate competência final AAAA-MM (inclusive); null = até a última nota
     */
    public DashboardDto dashboard(Long clienteId, String de, String ate) {
        validarPeriodo(de, ate);
        Cliente cliente = clienteService.buscar(clienteId);
        Agregado a = agregar(clienteId, de, ate);

        String nome = cliente.getNomeFantasia() != null ? cliente.getNomeFantasia() : cliente.getRazaoSocial();
        return new DashboardDto(
                new ClienteResumoDto(cliente.getId(), nome, cliente.getCnpj(), cliente.getRegime()),
                new PeriodoDto(de != null ? de : a.primeiraCompetencia(), ate != null ? ate : a.ultimaCompetencia()),
                a.indicadores(),
                ComparativoDto.de(a.comparativo()),
                a.avisos());
    }

    /** Tela inicial: todos os clientes com os indicadores de todo o período. */
    public List<ClienteListaDto> listarComIndicadores() {
        List<ClienteListaDto> lista = new ArrayList<>();
        for (Cliente c : clienteService.listar()) {
            Agregado a = agregar(c.getId(), null, null);
            lista.add(ClienteListaDto.de(c, a.notas(), a.indicadores()));
        }
        return lista;
    }

    private Agregado agregar(Long clienteId, String de, String ate) {
        List<Item> itens = itemRepository.doClienteNoPeriodo(clienteId, de, ate);
        List<Long> ids = itens.stream().map(Item::getId).toList();
        Map<Long, Classificacao> classificacoes = classificacaoRepository.findByItemIdIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getItem().getId(), Function.identity()));
        Map<Long, Calculo> calculos = calculoRepository.findByItemIdIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getItem().getId(), Function.identity()));

        Map<Long, Nota> notas = new LinkedHashMap<>();
        Comparativo total = Comparativo.ZERO;
        int pendentes = 0;
        int semCalculo = 0;
        int simplificados = 0;
        Set<BigDecimal> aliquotasCbs = new TreeSet<>();
        for (Item item : itens) {
            notas.putIfAbsent(item.getNota().getId(), item.getNota());
            if (criterioRevisao.precisaRevisao(classificacoes.get(item.getId()))) {
                pendentes++;
            }
            Calculo c = calculos.get(item.getId());
            if (c == null) {
                semCalculo++;
                continue;
            }
            total = total.somar(CalculoService.comparativo(c));
            if (c.getOrigemValores() == OrigemCalculo.SIMPLIFICADA) {
                simplificados++;
            }
            if (c.getPCbs() != null) {
                aliquotasCbs.add(c.getPCbs().stripTrailingZeros());
            }
        }

        BigDecimal faturamento = somaNotas(notas.values(), TipoNota.SAIDA);
        BigDecimal compras = somaNotas(notas.values(), TipoNota.ENTRADA);
        IndicadoresDto indicadores = new IndicadoresDto(faturamento, compras, total.hoje().liquido(),
                total.ano2027().liquido(), total.variacaoPct(), total.ano2027().credito(),
                total.ano2027().temSaldoCredor(), pendentes);

        List<String> avisos = avisos(notas.isEmpty(), semCalculo, simplificados, pendentes, aliquotasCbs, total);
        TreeSet<String> competencias = notas.values().stream().map(Nota::getCompetencia)
                .collect(Collectors.toCollection(TreeSet::new));
        return new Agregado(notas.size(), indicadores, total, avisos,
                competencias.isEmpty() ? null : competencias.first(),
                competencias.isEmpty() ? null : competencias.last());
    }

    private List<String> avisos(boolean semNotas, int semCalculo, int simplificados, int pendentes,
                                Set<BigDecimal> aliquotasCbs, Comparativo total) {
        List<String> avisos = new ArrayList<>();
        if (semNotas) {
            avisos.add("Nenhuma nota no período.");
            return avisos;
        }
        BigDecimal configurada = aliquotas.ano2027().cbs().stripTrailingZeros();
        if (aliquotasCbs.size() > 1) {
            avisos.add("As notas foram calculadas com alíquotas de CBS diferentes ("
                    + aliquotasCbs.stream().map(BigDecimal::toPlainString).collect(Collectors.joining("%, "))
                    + "%): recalcule o cliente para comparar no mesmo cenário.");
        } else if (aliquotasCbs.size() == 1 && aliquotasCbs.iterator().next().compareTo(configurada) != 0) {
            avisos.add("Cenário simulado: CBS de " + aliquotasCbs.iterator().next().toPlainString() + "% em 2027.");
        } else if (aliquotas.ano2027().cbsEstimativa()) {
            avisos.add("Alíquota da CBS 2027 (" + configurada.toPlainString()
                    + "%) é estimativa: o valor oficial ainda não foi fixado.");
        }
        if (semCalculo > 0) {
            avisos.add(semCalculo + " item(ns) sem cálculo ficaram fora do comparativo: classifique e calcule as notas.");
        }
        if (simplificados > 0) {
            avisos.add(simplificados + " item(ns) calculados pelo método simplificado, sem a calculadora oficial "
                    + "(mesmas fórmulas e tabelas oficiais).");
        }
        if (pendentes > 0) {
            avisos.add(pendentes + " item(ns) aguardando revisão da classificação.");
        }
        if (total.variacaoPct() == null && total.hoje().liquido().signum() <= 0) {
            avisos.add("Variação não calculada: hoje não há PIS/Cofins líquido a pagar no período.");
        }
        return avisos;
    }

    private static BigDecimal somaNotas(java.util.Collection<Nota> notas, TipoNota tipo) {
        return notas.stream().filter(n -> n.getTipo() == tipo).map(Nota::getValorTotal).filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
    }

    private static void validarPeriodo(String de, String ate) {
        for (String c : new String[]{de, ate}) {
            if (c != null && !c.matches(COMPETENCIA)) {
                throw ApiException.requisicaoInvalida("Competência deve estar no formato AAAA-MM: " + c);
            }
        }
        if (de != null && ate != null && de.compareTo(ate) > 0) {
            throw ApiException.requisicaoInvalida("Período inválido: 'de' (" + de + ") é depois de 'ate' (" + ate + ").");
        }
    }

    private record Agregado(int notas, IndicadoresDto indicadores, Comparativo comparativo, List<String> avisos,
                            String primeiraCompetencia, String ultimaCompetencia) {
    }
}

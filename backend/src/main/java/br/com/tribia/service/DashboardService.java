package br.com.tribia.service;

import br.com.tribia.client.calculadora.OrigemCalculo;
import br.com.tribia.config.AliquotasProperties;
import br.com.tribia.dto.ClienteListaDto;
import br.com.tribia.dto.ComparativoDto;
import br.com.tribia.dto.DashboardDto;
import br.com.tribia.dto.DashboardDto.ClienteResumoDto;
import br.com.tribia.dto.DashboardDto.Faixa;
import br.com.tribia.dto.DashboardDto.FaixaRegimeDto;
import br.com.tribia.dto.DashboardDto.FornecedorDto;
import br.com.tribia.dto.DashboardDto.ItemImpactoDto;
import br.com.tribia.dto.DashboardDto.MesDto;
import br.com.tribia.dto.DashboardDto.PeriodoDto;
import br.com.tribia.dto.IndicadoresDto;
import br.com.tribia.exception.ApiException;
import br.com.tribia.model.Calculo;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.Natureza;
import br.com.tribia.model.Nota;
import br.com.tribia.model.TipoNota;
import br.com.tribia.service.apuracao.Comparativo;
import br.com.tribia.service.calculo.CalculoService;
import br.com.tribia.service.classificacao.CriterioRevisao;
import br.com.tribia.service.painel.DadosApuracao;
import br.com.tribia.service.painel.DadosApuracao.Linha;
import br.com.tribia.util.ChaveClassificacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Agrega notas, classificações e cálculos de um cliente para o painel. Lê só o que está gravado: para refletir
 * uma classificação nova ou outro cenário de CBS, a nota (ou o cliente) precisa ser recalculada antes.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int TOP_ITENS = 10;
    static final int TOP_FORNECEDORES = 5;
    private static final String COMPETENCIA = "\\d{4}-(0[1-9]|1[0-2])";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final ClienteService clienteService;
    private final DadosApuracao dados;
    private final CriterioRevisao criterioRevisao;
    private final AliquotasProperties aliquotas;

    public DashboardService(ClienteService clienteService, DadosApuracao dados, CriterioRevisao criterioRevisao,
                            AliquotasProperties aliquotas) {
        this.clienteService = clienteService;
        this.dados = dados;
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
        List<Linha> linhas = dados.doCliente(clienteId, de, ate);
        Resumo r = resumir(linhas);

        String nome = cliente.getNomeFantasia() != null ? cliente.getNomeFantasia() : cliente.getRazaoSocial();
        return new DashboardDto(
                new ClienteResumoDto(cliente.getId(), nome, cliente.getCnpj(), cliente.getRegime()),
                new PeriodoDto(de != null ? de : r.primeiraCompetencia(), ate != null ? ate : r.ultimaCompetencia()),
                r.indicadores(),
                ComparativoDto.de(r.comparativo()),
                porMes(linhas),
                porRegime(linhas),
                topItens(linhas),
                topFornecedores(linhas),
                r.avisos());
    }

    /** Tela inicial: todos os clientes com os indicadores de todo o período. */
    public List<ClienteListaDto> listarComIndicadores() {
        List<ClienteListaDto> lista = new ArrayList<>();
        for (Cliente c : clienteService.listar()) {
            Resumo r = resumir(dados.doCliente(c.getId(), null, null));
            lista.add(ClienteListaDto.de(c, r.notas(), r.indicadores()));
        }
        return lista;
    }

    // ---------------- prioridade 1: indicadores e comparativo ----------------

    private Resumo resumir(List<Linha> linhas) {
        Map<Long, Nota> notas = new LinkedHashMap<>();
        Comparativo total = Comparativo.ZERO;
        int pendentes = 0;
        int semCalculo = 0;
        int simplificados = 0;
        Set<BigDecimal> aliquotasCbs = new TreeSet<>();
        for (Linha l : linhas) {
            notas.putIfAbsent(l.nota().getId(), l.nota());
            if (criterioRevisao.precisaRevisao(l.classificacao())) {
                pendentes++;
            }
            Calculo c = l.calculo();
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

        IndicadoresDto indicadores = new IndicadoresDto(somaNotas(notas.values(), TipoNota.SAIDA),
                somaNotas(notas.values(), TipoNota.ENTRADA), total.hoje().liquido(), total.ano2027().liquido(),
                total.variacaoPct(), total.ano2027().credito(), total.ano2027().temSaldoCredor(), pendentes);
        List<String> avisos = avisos(notas.isEmpty(), semCalculo, simplificados, pendentes, aliquotasCbs, total);
        TreeSet<String> competencias = notas.values().stream().map(Nota::getCompetencia)
                .collect(Collectors.toCollection(TreeSet::new));
        return new Resumo(notas.size(), indicadores, total, avisos,
                competencias.isEmpty() ? null : competencias.first(),
                competencias.isEmpty() ? null : competencias.last());
    }

    // ---------------- prioridade 2 ----------------

    private static List<MesDto> porMes(List<Linha> linhas) {
        Map<String, Comparativo> comparativos = new TreeMap<>();
        Map<String, Map<Long, Nota>> vendas = new TreeMap<>();
        for (Linha l : linhas) {
            String mes = l.nota().getCompetencia();
            comparativos.putIfAbsent(mes, Comparativo.ZERO);
            if (l.nota().getTipo() == TipoNota.SAIDA) {
                vendas.computeIfAbsent(mes, k -> new LinkedHashMap<>()).putIfAbsent(l.nota().getId(), l.nota());
            }
            if (l.calculo() != null) {
                comparativos.merge(mes, CalculoService.comparativo(l.calculo()), Comparativo::somar);
            }
        }
        return comparativos.entrySet().stream()
                .map(e -> new MesDto(e.getKey(),
                        somaNotas(vendas.getOrDefault(e.getKey(), Map.of()).values(), TipoNota.SAIDA),
                        e.getValue().hoje().liquido(), e.getValue().ano2027().liquido()))
                .toList();
    }

    private static List<FaixaRegimeDto> porRegime(List<Linha> linhas) {
        Map<Faixa, BigDecimal> valores = new EnumMap<>(Faixa.class);
        Map<Faixa, Integer> quantidades = new EnumMap<>(Faixa.class);
        BigDecimal total = ZERO;
        for (Linha l : linhas) {
            if (l.nota().getTipo() != TipoNota.SAIDA) {
                continue;
            }
            Faixa f = Faixa.de(l.classificacao() == null ? null : l.classificacao().getRegime(),
                    l.calculo() != null && l.calculo().isSujeitoIs());
            BigDecimal v = l.item().getValorTotal();
            valores.merge(f, v, BigDecimal::add);
            quantidades.merge(f, 1, Integer::sum);
            total = total.add(v);
        }
        BigDecimal base = total;
        return valores.entrySet().stream()
                .map(e -> new FaixaRegimeDto(e.getKey(), e.getValue(),
                        base.signum() == 0 ? ZERO : e.getValue().multiply(CEM).divide(base, 2, RoundingMode.HALF_EVEN),
                        quantidades.get(e.getKey())))
                .sorted(Comparator.comparing(FaixaRegimeDto::valor).reversed())
                .toList();
    }

    // ---------------- prioridade 3 ----------------

    /** Produtos (NCM + descrição) com maior efeito no imposto líquido, para mais ou para menos. */
    private static List<ItemImpactoDto> topItens(List<Linha> linhas) {
        Map<String, ItemImpactoDto> porProduto = new LinkedHashMap<>();
        for (Linha l : linhas) {
            Calculo c = l.calculo();
            if (c == null) {
                continue;
            }
            // venda soma débito; compra soma crédito, que reduz o imposto líquido
            BigDecimal sinal = c.getNatureza() == Natureza.DEBITO ? BigDecimal.ONE : BigDecimal.ONE.negate();
            BigDecimal hoje = c.getImpostoHoje().multiply(sinal);
            BigDecimal ano2027 = c.getImposto2027().multiply(sinal);
            String chave = ChaveClassificacao.de(l.item().getNcm(), l.item().getDescricao());
            porProduto.merge(chave,
                    new ItemImpactoDto(l.item().getDescricao(), l.item().getNcm(),
                            l.classificacao() == null ? null : l.classificacao().getCClassTrib(),
                            l.classificacao() == null ? null : l.classificacao().getRegime(),
                            hoje, ano2027, ano2027.subtract(hoje)),
                    (a, b) -> new ItemImpactoDto(a.descricao(), a.ncm(), a.cClassTrib(), a.regime(),
                            a.impostoHoje().add(b.impostoHoje()), a.imposto2027().add(b.imposto2027()),
                            a.diferenca().add(b.diferenca())));
        }
        return porProduto.values().stream()
                .filter(i -> i.diferenca().signum() != 0)
                .sorted(Comparator.comparing((ItemImpactoDto i) -> i.diferenca().abs()).reversed())
                .limit(TOP_ITENS)
                .toList();
    }

    /** Fornecedores (notas de entrada) que mais geram crédito em 2027. */
    private static List<FornecedorDto> topFornecedores(List<Linha> linhas) {
        Map<String, FornecedorDto> porCnpj = new LinkedHashMap<>();
        Map<String, Set<Long>> notasContadas = new LinkedHashMap<>();
        for (Linha l : linhas) {
            Nota n = l.nota();
            if (n.getTipo() != TipoNota.ENTRADA) {
                continue;
            }
            String cnpj = Objects.requireNonNullElse(n.getContraparteCnpj(), "");
            boolean notaNova = notasContadas.computeIfAbsent(cnpj, k -> new TreeSet<>()).add(n.getId());
            BigDecimal compras = notaNova && n.getValorTotal() != null ? n.getValorTotal() : ZERO;
            BigDecimal hoje = l.calculo() == null ? ZERO : l.calculo().getImpostoHoje();
            BigDecimal ano2027 = l.calculo() == null ? ZERO : l.calculo().getImposto2027();
            porCnpj.merge(cnpj, new FornecedorDto(cnpj, n.getContraparteNome(), compras, hoje, ano2027),
                    (a, b) -> new FornecedorDto(a.cnpj(), a.nome(), a.compras().add(b.compras()),
                            a.creditoHoje().add(b.creditoHoje()), a.credito2027().add(b.credito2027())));
        }
        return porCnpj.values().stream()
                .sorted(Comparator.comparing(FornecedorDto::credito2027).reversed()
                        .thenComparing(FornecedorDto::compras, Comparator.reverseOrder()))
                .limit(TOP_FORNECEDORES)
                .toList();
    }

    // ---------------- apoio ----------------

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

    private static BigDecimal somaNotas(Collection<Nota> notas, TipoNota tipo) {
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

    private record Resumo(int notas, IndicadoresDto indicadores, Comparativo comparativo, List<String> avisos,
                          String primeiraCompetencia, String ultimaCompetencia) {
    }
}

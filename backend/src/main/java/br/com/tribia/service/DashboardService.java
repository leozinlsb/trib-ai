package br.com.tribia.service;

import br.com.tribia.client.calculadora.OrigemCalculo;
import br.com.tribia.config.AliquotasProperties;
import br.com.tribia.dto.ClienteListaDto;
import br.com.tribia.dto.ComparativoDto;
import br.com.tribia.dto.DashboardDto;
import br.com.tribia.dto.DashboardDto.Agrupamento;
import br.com.tribia.dto.DashboardDto.ClienteResumoDto;
import br.com.tribia.dto.DashboardDto.Faixa;
import br.com.tribia.dto.DashboardDto.FaixaRegimeDto;
import br.com.tribia.dto.DashboardDto.FornecedorDto;
import br.com.tribia.dto.DashboardDto.ItemImpactoDto;
import br.com.tribia.dto.DashboardDto.MesDto;
import br.com.tribia.dto.DashboardDto.PeriodoDto;
import br.com.tribia.dto.DashboardDto.SujeitoIsDto;
import br.com.tribia.dto.IndicadoresDto;
import br.com.tribia.dto.NotaResumoDto;
import br.com.tribia.dto.ResumoNotaDto;
import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.Calculo;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.Natureza;
import br.com.tribia.model.Nota;
import br.com.tribia.repository.NotaRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Agrega notas, classificações e cálculos de um cliente para o painel. Lê só o que está gravado (classificar e revisar
 * já recalculam a nota). Devoluções entram com sinal negativo: o cálculo gravado já vem como estorno.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    public static final int TOP_ITENS = 10;
    public static final int TOP_FORNECEDORES = 5;
    static final int LIMITE_MAXIMO = 50;
    private static final String COMPETENCIA = "\\d{4}-(0[1-9]|1[0-2])";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final ClienteService clienteService;
    private final DadosApuracao dados;
    private final CriterioRevisao criterioRevisao;
    private final AliquotasProperties aliquotas;
    private final NotaRepository notaRepository;

    public DashboardService(ClienteService clienteService, DadosApuracao dados, CriterioRevisao criterioRevisao,
                            AliquotasProperties aliquotas, NotaRepository notaRepository) {
        this.clienteService = clienteService;
        this.dados = dados;
        this.criterioRevisao = criterioRevisao;
        this.aliquotas = aliquotas;
        this.notaRepository = notaRepository;
    }

    public DashboardDto dashboard(Long clienteId, String de, String ate) {
        return dashboard(clienteId, de, ate, Agrupamento.PRODUTO, TOP_ITENS, TOP_FORNECEDORES);
    }

    /**
     * @param de                 competência inicial AAAA-MM (inclusive); null = desde a primeira nota
     * @param ate                competência final AAAA-MM (inclusive); null = até a última nota
     * @param agrupar            ranking topItens por produto (padrão) ou por NCM
     * @param limiteItens        tamanho do topItens (1 a 50)
     * @param limiteFornecedores tamanho do topFornecedores (1 a 50)
     */
    public DashboardDto dashboard(Long clienteId, String de, String ate, Agrupamento agrupar, int limiteItens,
                                  int limiteFornecedores) {
        validarPeriodo(de, ate);
        validarLimite("limiteItens", limiteItens);
        validarLimite("limiteFornecedores", limiteFornecedores);
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
                sujeitoIs(linhas),
                impacto(linhas, agrupar == null ? Agrupamento.PRODUTO : agrupar, limiteItens),
                topFornecedores(linhas, limiteFornecedores),
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

    /** Resumo de uma nota: comparativo, regimes e o efeito de todos os seus produtos. */
    public ResumoNotaDto resumoDaNota(Long notaId) {
        Nota nota = notaRepository.buscarComItens(notaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nota " + notaId + " não encontrada"));
        List<Linha> linhas = dados.daNota(notaId);
        Resumo r = resumir(linhas);
        return new ResumoNotaDto(NotaResumoDto.de(nota), ComparativoDto.de(r.comparativo()), porRegime(linhas),
                sujeitoIs(linhas), impacto(linhas, Agrupamento.PRODUTO, Integer.MAX_VALUE),
                r.indicadores().pendentesRevisao(), r.avisos());
    }

    // ---------------- prioridade 1: indicadores e comparativo ----------------

    private Resumo resumir(List<Linha> linhas) {
        Map<Long, Nota> notas = new LinkedHashMap<>();
        Comparativo total = Comparativo.ZERO;
        BigDecimal icms = ZERO;
        int pendentes = 0;
        int semCalculo = 0;
        int simplificados = 0;
        Set<BigDecimal> aliquotasCbs = new TreeSet<>();
        Set<String> avisosDeNota = new LinkedHashSet<>();
        for (Linha l : linhas) {
            notas.putIfAbsent(l.nota().getId(), l.nota());
            if (criterioRevisao.precisaRevisao(l.classificacao())) {
                pendentes++;
            }
            if (l.nota().getOperacao().natureza() == Natureza.DEBITO) {
                icms = icms.add(nz(l.item().getVIcms()).multiply(sinal(l)));
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
        for (Nota n : notas.values()) {
            if (n.getOperacao().natureza() == Natureza.CREDITO && !n.isPagamentoConfirmado()) {
                avisosDeNota.add("Há compras sem pagamento confirmado: elas não geram crédito de 2027 (LC 214, art. 47).");
            }
            if (n.getOperacao().natureza() == Natureza.CREDITO && n.emitenteDoSimples()) {
                avisosDeNota.add("Há compras de fornecedores do Simples Nacional: o crédito de 2027 delas segue a regra "
                        + "configurada (tribia.calculo.credito-fornecedor-simples).");
            }
            if (n.getOperacao().devolucao()) {
                avisosDeNota.add("Há devoluções no período: elas estornam o faturamento/compras e o débito/crédito.");
            }
        }

        IndicadoresDto indicadores = new IndicadoresDto(somaNotas(notas.values(), Natureza.DEBITO),
                somaNotas(notas.values(), Natureza.CREDITO), total.hoje().liquido(), total.ano2027().liquido(),
                total.variacaoPct(), total.ano2027().credito(), total.ano2027().temSaldoCredor(), pendentes, icms);
        List<String> avisos = avisos(notas.isEmpty(), semCalculo, simplificados, pendentes, aliquotasCbs, total);
        avisos.addAll(avisosDeNota);
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
            if (l.nota().getOperacao().natureza() == Natureza.DEBITO) {
                vendas.computeIfAbsent(mes, k -> new LinkedHashMap<>()).putIfAbsent(l.nota().getId(), l.nota());
            }
            if (l.calculo() != null) {
                comparativos.merge(mes, CalculoService.comparativo(l.calculo()), Comparativo::somar);
            }
        }
        return comparativos.entrySet().stream()
                .map(e -> new MesDto(e.getKey(),
                        somaNotas(vendas.getOrDefault(e.getKey(), Map.of()).values(), Natureza.DEBITO),
                        e.getValue().hoje().liquido(), e.getValue().ano2027().liquido()))
                .toList();
    }

    private static List<FaixaRegimeDto> porRegime(List<Linha> linhas) {
        Map<Faixa, BigDecimal> valores = new EnumMap<>(Faixa.class);
        Map<Faixa, Integer> quantidades = new EnumMap<>(Faixa.class);
        for (Linha l : vendas(linhas)) {
            Faixa f = Faixa.de(l.classificacao() == null ? null : l.classificacao().getRegime());
            valores.merge(f, valorAssinado(l), BigDecimal::add);
            quantidades.merge(f, 1, Integer::sum);
        }
        BigDecimal base = valores.values().stream().reduce(ZERO, BigDecimal::add);
        return valores.entrySet().stream()
                .map(e -> new FaixaRegimeDto(e.getKey(), e.getValue(), percentual(e.getValue(), base),
                        quantidades.get(e.getKey())))
                .sorted(Comparator.comparing(FaixaRegimeDto::valor).reversed())
                .toList();
    }

    private static SujeitoIsDto sujeitoIs(List<Linha> linhas) {
        BigDecimal total = ZERO;
        BigDecimal valor = ZERO;
        int itens = 0;
        for (Linha l : vendas(linhas)) {
            total = total.add(valorAssinado(l));
            if (l.calculo() != null && l.calculo().isSujeitoIs()) {
                valor = valor.add(valorAssinado(l));
                itens++;
            }
        }
        return new SujeitoIsDto(valor, percentual(valor, total), itens);
    }

    // ---------------- prioridade 3 ----------------

    /** Produtos (ou NCMs) com maior efeito no imposto líquido, para mais ou para menos. */
    private static List<ItemImpactoDto> impacto(List<Linha> linhas, Agrupamento agrupar, int limite) {
        Map<String, ItemImpactoDto> grupos = new LinkedHashMap<>();
        Map<String, Set<String>> descricoes = new LinkedHashMap<>();
        for (Linha l : linhas) {
            Calculo c = l.calculo();
            if (c == null) {
                continue;
            }
            // débito soma; crédito reduz o imposto líquido (o valor gravado já tem o sinal da devolução)
            BigDecimal efeito = c.getNatureza() == Natureza.DEBITO ? BigDecimal.ONE : BigDecimal.ONE.negate();
            BigDecimal hoje = c.getImpostoHoje().multiply(efeito);
            BigDecimal ano2027 = c.getImposto2027().multiply(efeito);
            String chaveProduto = ChaveClassificacao.de(l.item().getNcm(), l.item().getDescricao());
            String chave = agrupar == Agrupamento.NCM ? "NCM|" + l.item().getNcm() : chaveProduto;
            descricoes.computeIfAbsent(chave, k -> new LinkedHashSet<>()).add(chaveProduto);
            grupos.merge(chave,
                    new ItemImpactoDto(l.item().getDescricao(), l.item().getNcm(),
                            l.classificacao() == null ? null : l.classificacao().getCClassTrib(),
                            l.classificacao() == null ? null : l.classificacao().getRegime(), 1,
                            hoje, ano2027, ano2027.subtract(hoje)),
                    (a, b) -> new ItemImpactoDto(a.descricao(), a.ncm(), a.cClassTrib(), a.regime(), 1,
                            a.impostoHoje().add(b.impostoHoje()), a.imposto2027().add(b.imposto2027()),
                            a.diferenca().add(b.diferenca())));
        }
        return grupos.entrySet().stream()
                .map(e -> {
                    ItemImpactoDto i = e.getValue();
                    int produtos = descricoes.get(e.getKey()).size();
                    return new ItemImpactoDto(i.descricao(), i.ncm(), i.cClassTrib(), i.regime(), produtos,
                            i.impostoHoje(), i.imposto2027(), i.diferenca());
                })
                .filter(i -> i.diferenca().signum() != 0)
                .sorted(Comparator.comparing((ItemImpactoDto i) -> i.diferenca().abs()).reversed())
                .limit(limite)
                .toList();
    }

    /** Fornecedores (compras menos devoluções de compra) que mais geram crédito em 2027. */
    private static List<FornecedorDto> topFornecedores(List<Linha> linhas, int limite) {
        Map<String, FornecedorDto> porCnpj = new LinkedHashMap<>();
        Map<String, Set<Long>> notasContadas = new LinkedHashMap<>();
        for (Linha l : linhas) {
            Nota n = l.nota();
            if (n.getOperacao().natureza() != Natureza.CREDITO) {
                continue;
            }
            String cnpj = Objects.requireNonNullElse(n.getContraparteCnpj(), "");
            boolean notaNova = notasContadas.computeIfAbsent(cnpj, k -> new TreeSet<>()).add(n.getId());
            BigDecimal compras = notaNova && n.getValorTotal() != null ? n.getValorTotal().multiply(sinal(l)) : ZERO;
            BigDecimal hoje = l.calculo() == null ? ZERO : l.calculo().getImpostoHoje();
            BigDecimal ano2027 = l.calculo() == null ? ZERO : l.calculo().getImposto2027();
            porCnpj.merge(cnpj, new FornecedorDto(cnpj, n.getContraparteNome(), compras, hoje, ano2027),
                    (a, b) -> new FornecedorDto(a.cnpj(), a.nome(), a.compras().add(b.compras()),
                            a.creditoHoje().add(b.creditoHoje()), a.credito2027().add(b.credito2027())));
        }
        return porCnpj.values().stream()
                .sorted(Comparator.comparing(FornecedorDto::credito2027).reversed()
                        .thenComparing(FornecedorDto::compras, Comparator.reverseOrder()))
                .limit(limite)
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
        BigDecimal configurada = aliquotas.ano2027().cbsEfetiva().stripTrailingZeros();
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

    /** Linhas de venda e de devolução de venda (o lado do faturamento). */
    private static List<Linha> vendas(List<Linha> linhas) {
        return linhas.stream().filter(l -> l.nota().getOperacao().natureza() == Natureza.DEBITO).toList();
    }

    private static BigDecimal valorAssinado(Linha l) {
        return nz(l.item().getValorTotal()).multiply(sinal(l));
    }

    private static BigDecimal sinal(Linha l) {
        return BigDecimal.valueOf(l.nota().getOperacao().sinal());
    }

    private static BigDecimal somaNotas(Collection<Nota> notas, Natureza natureza) {
        return notas.stream().filter(n -> n.getOperacao().natureza() == natureza && n.getValorTotal() != null)
                .map(n -> n.getValorTotal().multiply(BigDecimal.valueOf(n.getOperacao().sinal())))
                .reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal percentual(BigDecimal valor, BigDecimal total) {
        return total.signum() == 0 ? ZERO : valor.multiply(CEM).divide(total, 2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
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

    private static void validarLimite(String nome, int valor) {
        if (valor < 1 || valor > LIMITE_MAXIMO) {
            throw ApiException.requisicaoInvalida(nome + " deve estar entre 1 e " + LIMITE_MAXIMO + ": " + valor);
        }
    }

    private record Resumo(int notas, IndicadoresDto indicadores, Comparativo comparativo, List<String> avisos,
                          String primeiraCompetencia, String ultimaCompetencia) {
    }
}

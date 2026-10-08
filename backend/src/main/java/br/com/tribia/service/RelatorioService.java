package br.com.tribia.service;

import br.com.tribia.config.AliquotasProperties;
import br.com.tribia.dto.ClienteDto;
import br.com.tribia.dto.RelatorioDto;
import br.com.tribia.dto.RelatorioDto.ApuracaoDto;
import br.com.tribia.dto.RelatorioDto.Observacao;
import br.com.tribia.dto.RelatorioDto.Referencia;
import br.com.tribia.exception.ApiException;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.Item;
import br.com.tribia.model.Nota;
import br.com.tribia.model.Regime;
import br.com.tribia.model.TipoNota;
import br.com.tribia.repository.NotaRepository;
import br.com.tribia.service.apuracao.Apuracao;
import br.com.tribia.service.apuracao.ClassificacaoXml;
import br.com.tribia.service.apuracao.ItemTributavel;
import br.com.tribia.service.apuracao.RegrasApuracao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Monta o relatório de uma empresa a partir das notas do período. A apuração usa {@link RegrasApuracao}
 * (PIS/Cofins de hoje). As observações são verificações objetivas; o relatório não faz recomendações.
 */
@Service
@Transactional(readOnly = true)
public class RelatorioService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final int MAX_REFERENCIAS = 30;

    private final NotaRepository notas;
    private final RegrasApuracao regras;
    private final AliquotasProperties aliquotas;

    public RelatorioService(NotaRepository notas, RegrasApuracao regras, AliquotasProperties aliquotas) {
        this.notas = notas;
        this.regras = regras;
        this.aliquotas = aliquotas;
    }

    public RelatorioDto gerar(Cliente cliente, String de, String ate) {
        validarCompetencia(de);
        validarCompetencia(ate);
        if (de != null && ate != null && de.compareTo(ate) > 0) {
            throw ApiException.requisicaoInvalida("O início do período deve ser anterior ao fim.");
        }
        List<Nota> lista = notas.buscarComItensNoPeriodo(cliente.getId(), de, ate);
        Regime regime = cliente.getRegime();

        int entradas = 0;
        int saidas = 0;
        int itens = 0;
        int classificados = 0;
        BigDecimal valorEntradas = ZERO;
        BigDecimal valorSaidas = ZERO;
        Apuracao total = Apuracao.ZERO;
        TreeMap<String, Acumulado> porCompetencia = new TreeMap<>();
        Map<String, ContraparteAcc> contrapartes = new LinkedHashMap<>();
        List<RelatorioDto.Documento> documentos = new ArrayList<>();

        List<Referencia> semClassificacao = new ArrayList<>();
        List<Referencia> semNcm = new ArrayList<>();
        List<Referencia> entradaSemCredito = new ArrayList<>();
        int qtdSemClassificacao = 0;
        int qtdSemNcm = 0;
        int qtdEntradaSemCredito = 0;

        for (Nota n : lista) {
            boolean saida = n.getTipo() == TipoNota.SAIDA;
            BigDecimal valor = nz(n.getValorTotal());
            Apuracao daNota = Apuracao.ZERO;
            BigDecimal destacado = ZERO;
            int classificadosNaNota = 0;

            for (Item i : n.getItens()) {
                itens++;
                destacado = destacado.add(nz(i.getVPis())).add(nz(i.getVCofins()));
                BigDecimal imposto = regras.pisCofinsHoje(regime, n.getTipo(), tributavel(i));
                daNota = daNota.somar(saida ? new Apuracao(imposto, ZERO) : new Apuracao(ZERO, imposto));

                if (ClassificacaoXml.de(i).isPresent()) {
                    classificados++;
                    classificadosNaNota++;
                } else {
                    qtdSemClassificacao++;
                    referencia(semClassificacao, n, i);
                }
                if (i.getNcm() == null || i.getNcm().isBlank()) {
                    qtdSemNcm++;
                    referencia(semNcm, n, i);
                }
                if (!saida && regime == Regime.LUCRO_REAL && i.isCreditavel()
                        && !aliquotas.hoje().cstComCredito().contains(i.getCstPisCofins())) {
                    qtdEntradaSemCredito++;
                    referencia(entradaSemCredito, n, i);
                }
            }

            if (saida) {
                saidas++;
                valorSaidas = valorSaidas.add(valor);
            } else {
                entradas++;
                valorEntradas = valorEntradas.add(valor);
            }
            total = total.somar(daNota);
            porCompetencia.computeIfAbsent(n.getCompetencia(), k -> new Acumulado()).somar(saida, valor, daNota);

            String chave = n.getContraparteCnpj() != null ? n.getContraparteCnpj() : "?" + n.getContraparteNome();
            contrapartes.computeIfAbsent(chave, k -> new ContraparteAcc(n.getContraparteCnpj(), n.getContraparteNome()))
                    .somar(saida, valor);

            documentos.add(new RelatorioDto.Documento(n.getId(), n.getTipo(), n.getNumero(), n.getSerie(),
                    n.getDataEmissao(), n.getCompetencia(), n.getContraparteCnpj(), n.getContraparteNome(), valor,
                    n.getItens().size(), classificadosNaNota, destacado, saida ? daNota.debito() : daNota.credito()));
        }

        List<Observacao> observacoes = new ArrayList<>();
        if (qtdSemClassificacao > 0) {
            observacoes.add(new Observacao("ITENS_SEM_CLASSIFICACAO", "ATENCAO",
                    qtdSemClassificacao + " de " + itens + " itens ainda não têm classificação tributária da reforma "
                            + "(CST e cClassTrib).", qtdSemClassificacao, semClassificacao));
        }
        if (qtdSemNcm > 0) {
            observacoes.add(new Observacao("ITENS_SEM_NCM", "ATENCAO",
                    qtdSemNcm + " itens estão sem NCM na nota fiscal.", qtdSemNcm, semNcm));
        }
        if (qtdEntradaSemCredito > 0) {
            observacoes.add(new Observacao("ENTRADAS_SEM_CREDITO", "INFO",
                    qtdEntradaSemCredito + " itens de compra não geram crédito de PIS/Cofins pelo CST informado na nota "
                            + "(por exemplo, alíquota zero ou produto monofásico).", qtdEntradaSemCredito, entradaSemCredito));
        }
        if (regime == Regime.LUCRO_PRESUMIDO && entradas > 0) {
            observacoes.add(new Observacao("PRESUMIDO_SEM_CREDITO", "INFO",
                    "No Lucro Presumido (regime cumulativo), as compras não geram crédito de PIS/Cofins.", entradas,
                    List.of()));
        }
        if (total.temSaldoCredor()) {
            observacoes.add(new Observacao("SALDO_CREDOR", "INFO",
                    "Os créditos superam os débitos no período: há saldo credor de PIS/Cofins.", 1, List.of()));
        }

        String periodoDe = de != null ? de : porCompetencia.isEmpty() ? null : porCompetencia.firstKey();
        String periodoAte = ate != null ? ate : porCompetencia.isEmpty() ? null : porCompetencia.lastKey();
        String situacao = lista.isEmpty() ? "SEM_DADOS" : qtdSemClassificacao == 0 ? "COMPLETO" : "PARCIAL";
        String identificacao = "REL-" + cliente.getId()
                + (periodoDe != null ? "-" + periodoDe.replace("-", "") + "-" + periodoAte.replace("-", "") : "");

        List<RelatorioDto.Competencia> competencias = porCompetencia.entrySet().stream()
                .map(e -> e.getValue().dto(e.getKey()))
                .toList();
        List<RelatorioDto.Contraparte> listaContrapartes = contrapartes.values().stream()
                .map(ContraparteAcc::dto)
                .sorted(Comparator.comparing(RelatorioDto.Contraparte::valor).reversed())
                .toList();

        return new RelatorioDto(identificacao, Instant.now(), ClienteDto.de(cliente), periodoDe, periodoAte, situacao,
                new RelatorioDto.Resumo(lista.size(), entradas, saidas, itens, classificados, valorEntradas,
                        valorSaidas, listaContrapartes.size()),
                ApuracaoDto.de(total), competencias, documentos, listaContrapartes, observacoes);
    }

    private static ItemTributavel tributavel(Item i) {
        return new ItemTributavel(nz(i.getValorTotal()), i.getCstPisCofins(), nz(i.getVPis()), nz(i.getVCofins()),
                i.isCreditavel());
    }

    private static void referencia(List<Referencia> lista, Nota n, Item i) {
        if (lista.size() < MAX_REFERENCIAS) {
            lista.add(new Referencia(n.getId(), n.getNumero(), i.getNItem(), i.getDescricao()));
        }
    }

    private static void validarCompetencia(String c) {
        if (c != null && !c.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw ApiException.requisicaoInvalida("Competência deve estar no formato AAAA-MM: " + c);
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    private static final class Acumulado {
        int notas;
        BigDecimal entradas = ZERO;
        BigDecimal saidas = ZERO;
        Apuracao apuracao = Apuracao.ZERO;

        void somar(boolean saida, BigDecimal valor, Apuracao a) {
            notas++;
            if (saida) {
                saidas = saidas.add(valor);
            } else {
                entradas = entradas.add(valor);
            }
            apuracao = apuracao.somar(a);
        }

        RelatorioDto.Competencia dto(String competencia) {
            return new RelatorioDto.Competencia(competencia, notas, entradas, saidas, ApuracaoDto.de(apuracao));
        }
    }

    private static final class ContraparteAcc {
        final String documento;
        final String nome;
        boolean fornecedor;
        boolean clienteFinal;
        int notas;
        BigDecimal valor = ZERO;

        ContraparteAcc(String documento, String nome) {
            this.documento = documento;
            this.nome = nome;
        }

        void somar(boolean saida, BigDecimal v) {
            if (saida) {
                clienteFinal = true;
            } else {
                fornecedor = true;
            }
            notas++;
            valor = valor.add(v);
        }

        RelatorioDto.Contraparte dto() {
            return new RelatorioDto.Contraparte(documento, nome, fornecedor, clienteFinal, notas, valor);
        }
    }
}

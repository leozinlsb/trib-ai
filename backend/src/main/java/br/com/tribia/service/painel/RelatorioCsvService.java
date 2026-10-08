package br.com.tribia.service.painel;

import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.Calculo;
import br.com.tribia.model.Classificacao;
import br.com.tribia.model.Item;
import br.com.tribia.model.Natureza;
import br.com.tribia.model.Nota;
import br.com.tribia.repository.NotaRepository;
import br.com.tribia.service.ClienteService;
import br.com.tribia.service.apuracao.Apuracao;
import br.com.tribia.service.apuracao.Comparativo;
import br.com.tribia.service.calculo.CalculoService;
import br.com.tribia.service.painel.DadosApuracao.Linha;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Relatório em CSV para abrir no Excel em português: separador ";", vírgula decimal, datas dd/MM/aaaa e UTF-8 com
 * BOM (sem ele o Excel quebra os acentos). Uma linha por item, depois um bloco de resumo hoje x 2027.
 */
@Service
@Transactional(readOnly = true)
public class RelatorioCsvService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    static final List<String> CABECALHO = List.of(
            "Competência", "Data de emissão", "Tipo", "Nota", "Série", "Chave de acesso", "Contraparte (CNPJ/CPF)",
            "Contraparte", "Item", "Código", "Descrição", "NCM", "CFOP", "Quantidade", "Valor", "CST PIS/Cofins",
            "PIS", "Cofins", "Creditável", "CST IBS/CBS", "cClassTrib", "Regime 2027", "Origem da classificação",
            "Confiança", "Aceita", "Revisada", "Natureza", "Imposto hoje (PIS/Cofins)", "CBS 2027", "IBS UF 2027",
            "IBS Mun 2027", "IS 2027", "Imposto 2027", "Diferença", "Origem do cálculo", "Alíquota CBS (%)");

    private final DadosApuracao dados;
    private final ClienteService clienteService;
    private final NotaRepository notaRepository;

    public RelatorioCsvService(DadosApuracao dados, ClienteService clienteService, NotaRepository notaRepository) {
        this.dados = dados;
        this.clienteService = clienteService;
        this.notaRepository = notaRepository;
    }

    public byte[] doCliente(Long clienteId, String de, String ate) {
        clienteService.buscar(clienteId);
        if ((de != null && !de.matches("\\d{4}-\\d{2}")) || (ate != null && !ate.matches("\\d{4}-\\d{2}"))) {
            throw ApiException.requisicaoInvalida("Competências devem estar no formato AAAA-MM.");
        }
        return gerar(dados.doCliente(clienteId, de, ate));
    }

    public byte[] daNota(Long notaId) {
        if (!notaRepository.existsById(notaId)) {
            throw new RecursoNaoEncontradoException("Nota " + notaId + " não encontrada");
        }
        return gerar(dados.daNota(notaId));
    }

    private static byte[] gerar(List<Linha> linhas) {
        StringBuilder sb = new StringBuilder();
        linha(sb, CABECALHO);
        Comparativo total = Comparativo.ZERO;
        for (Linha l : linhas) {
            linha(sb, colunas(l));
            if (l.calculo() != null) {
                total = total.somar(CalculoService.comparativo(l.calculo()));
            }
        }
        sb.append("\r\n");
        linha(sb, List.of("Resumo", "Débito", "Crédito", "Líquido"));
        resumo(sb, "Hoje (PIS/Cofins)", total.hoje());
        resumo(sb, "2027 (CBS/IBS/IS)", total.ano2027());
        linha(sb, List.of("Variação do líquido (%)", num(total.variacaoPct())));
        linha(sb, List.of("Itens no relatório", String.valueOf(linhas.size())));
        linha(sb, List.of("Itens sem cálculo (fora do resumo)",
                String.valueOf(linhas.stream().filter(l -> l.calculo() == null).count())));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(BOM);
        out.writeBytes(sb.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static List<String> colunas(Linha l) {
        Item i = l.item();
        Nota n = l.nota();
        Classificacao c = l.classificacao();
        Calculo k = l.calculo();
        BigDecimal diferenca = k == null ? null : k.getImposto2027().subtract(k.getImpostoHoje());
        List<String> v = new ArrayList<>(CABECALHO.size());
        v.add(n.getCompetencia());
        v.add(n.getDataEmissao() == null ? "" : n.getDataEmissao().format(DATA));
        v.add(n.getTipo().name());
        v.add(String.valueOf(n.getNumero()));
        v.add(String.valueOf(n.getSerie()));
        v.add(n.getChave());
        v.add(nz(n.getContraparteCnpj()));
        v.add(nz(n.getContraparteNome()));
        v.add(String.valueOf(i.getNItem()));
        v.add(nz(i.getCodigo()));
        v.add(nz(i.getDescricao()));
        v.add(nz(i.getNcm()));
        v.add(nz(i.getCfop()));
        v.add(num(i.getQuantidade()));
        v.add(num(i.getValorTotal()));
        v.add(nz(i.getCstPisCofins()));
        v.add(num(i.getVPis()));
        v.add(num(i.getVCofins()));
        v.add(i.isCreditavel() ? "Sim" : "Não");
        v.add(c == null ? "" : c.getCst());
        v.add(c == null ? "" : c.getCClassTrib());
        v.add(c == null ? "" : c.getRegime().name());
        v.add(c == null ? "SEM CLASSIFICAÇÃO" : c.getOrigem().name());
        v.add(c == null ? "" : num(c.getConfianca()));
        v.add(c == null ? "" : (c.isAceita() ? "Sim" : "Não"));
        v.add(c == null ? "" : (c.isRevisada() ? "Sim" : "Não"));
        v.add(k == null ? "" : (k.getNatureza() == Natureza.DEBITO ? "Débito" : "Crédito"));
        v.add(k == null ? "" : num(k.getImpostoHoje()));
        v.add(k == null ? "" : num(k.getVCbs()));
        v.add(k == null ? "" : num(k.getVIbsUf()));
        v.add(k == null ? "" : num(k.getVIbsMun()));
        v.add(k == null ? "" : num(k.getVIs()));
        v.add(k == null ? "" : num(k.getImposto2027()));
        v.add(num(diferenca));
        v.add(k == null ? "" : k.getOrigemValores().name());
        v.add(k == null ? "" : num(k.getPCbs()));
        return v;
    }

    private static void resumo(StringBuilder sb, String rotulo, Apuracao a) {
        linha(sb, List.of(rotulo, num(a.debito()), num(a.credito()), num(a.liquido())));
    }

    private static void linha(StringBuilder sb, List<String> campos) {
        for (int i = 0; i < campos.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(escapar(campos.get(i)));
        }
        sb.append("\r\n");
    }

    /** Aspas em volta (e aspas duplicadas) quando o texto tem ";", aspas ou quebra de linha. */
    static String escapar(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** Vírgula decimal, sem separador de milhar (o Excel em pt-BR lê como número). */
    static String num(BigDecimal v) {
        return v == null ? "" : v.toPlainString().replace('.', ',');
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

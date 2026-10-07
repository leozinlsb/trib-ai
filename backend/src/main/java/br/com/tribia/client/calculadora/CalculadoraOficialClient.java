package br.com.tribia.client.calculadora;

import br.com.tribia.client.calculadora.OperacaoCalculo.ItemCalculo;
import br.com.tribia.client.calculadora.RegimeGeralApi.AliquotasNominais;
import br.com.tribia.client.calculadora.RegimeGeralApi.GrupoIbsCbs;
import br.com.tribia.client.calculadora.RegimeGeralApi.ImpostoSeletivoRequisicao;
import br.com.tribia.client.calculadora.RegimeGeralApi.ItemRequisicao;
import br.com.tribia.client.calculadora.RegimeGeralApi.Objeto;
import br.com.tribia.client.calculadora.RegimeGeralApi.Problema;
import br.com.tribia.client.calculadora.RegimeGeralApi.Reducao;
import br.com.tribia.client.calculadora.RegimeGeralApi.Requisicao;
import br.com.tribia.client.calculadora.RegimeGeralApi.Resposta;
import br.com.tribia.client.calculadora.ResultadoCalculo.AliquotasAplicadas;
import br.com.tribia.client.calculadora.ResultadoCalculo.ItemCalculado;
import br.com.tribia.config.AliquotasProperties;
import br.com.tribia.service.apuracao.Tributos2027;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente da Calculadora RTC oficial (POST /calculadora/regime-geral).
 *
 * - Envia as alíquotas nominais de 2027 (tribia.aliquotas.ano2027), que a calculadora exige a partir de 01/01/2027;
 *   por isso o resultado vem marcado como simulado.
 * - Se a calculadora não conhecer um NCM (ex.: 34022000, extinto na revisão de 2022), reenvia o item sem NCM
 *   e devolve um aviso: uma nota antiga não derruba o cálculo da nota inteira.
 */
@Component
public class CalculadoraOficialClient implements CalculadoraClient {

    private static final Logger log = LoggerFactory.getLogger(CalculadoraOficialClient.class);
    private static final String ENDPOINT = "/calculadora/regime-geral";
    private static final String VERSAO = "1.0.0";
    private static final Pattern NCM_NO_DETALHE = Pattern.compile("NCM de código (\\d+)");

    private final RestClient http;
    private final AliquotasProperties aliquotas;
    private final ObjectMapper json;

    public CalculadoraOficialClient(@Qualifier("calculadoraRestClient") RestClient http,
                                    AliquotasProperties aliquotas, ObjectMapper json) {
        this.http = http;
        this.aliquotas = aliquotas;
        this.json = json;
    }

    @Override
    public ResultadoCalculo calcular(OperacaoCalculo op) {
        List<ItemRequisicao> itens = op.itens().stream().map(this::paraRequisicao).toList();
        List<String> avisos = new ArrayList<>();
        Set<String> ncmsRemovidos = new HashSet<>();

        while (true) {
            Requisicao req = new Requisicao(op.id(), VERSAO, Requisicao.data(op.dataFatoGerador()),
                    Long.valueOf(op.codigoMunicipio()), op.uf(), itens);
            try {
                Resposta resp = http.post().uri(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(req)
                        .retrieve()
                        .body(Resposta.class);
                return converter(resp, op, avisos);
            } catch (RestClientResponseException e) {
                Problema p = problema(e);
                String ncm = p.eNcmNaoEncontrada() ? ncmDoDetalhe(p.detail()) : null;
                if (ncm != null && ncmsRemovidos.add(ncm)) {
                    avisos.add("NCM " + ncm + " não consta na tabela oficial (pode estar extinto). "
                            + "O item foi calculado sem NCM; revise o cadastro do produto.");
                    itens = itens.stream().map(i -> ncm.equals(i.ncm()) ? i.semNcm() : i).toList();
                    continue;
                }
                throw new CalculadoraException(CalculadoraException.Tipo.REJEITADA,
                        "A calculadora oficial recusou a operação: " + p.detail(), e);
            } catch (RestClientException e) {
                log.warn("Calculadora oficial indisponível: {}", e.getMessage());
                throw new CalculadoraException(CalculadoraException.Tipo.INDISPONIVEL,
                        "A calculadora oficial não respondeu. Confira se ela está rodando (ver README).", e);
            }
        }
    }

    private ItemRequisicao paraRequisicao(ItemCalculo i) {
        AliquotasProperties.Ano2027 a = aliquotas.ano2027();
        ImpostoSeletivoRequisicao is = i.impostoSeletivo() == null ? null
                : new ImpostoSeletivoRequisicao(i.impostoSeletivo().cst(), i.impostoSeletivo().cClassTrib(),
                i.baseCalculo(), i.unidade(), i.quantidade());
        return new ItemRequisicao(i.numero(), i.ncm(), i.quantidade(), i.unidade(), i.cst(), i.cClassTrib(),
                i.baseCalculo(), is, new AliquotasNominais(a.cbs(), a.ibsUf(), a.ibsMun()));
    }

    private ResultadoCalculo converter(Resposta resp, OperacaoCalculo op, List<String> avisos) {
        if (resp == null || resp.objetos() == null || resp.objetos().size() != op.itens().size()) {
            throw new CalculadoraException(CalculadoraException.Tipo.INDISPONIVEL,
                    "Resposta inesperada da calculadora oficial (quantidade de itens não confere).", null);
        }
        boolean simulado = false;
        List<ItemCalculado> itens = new ArrayList<>();
        for (Objeto o : resp.objetos()) {
            simulado |= o.calculoSimulado();
            itens.add(item(o));
        }
        return new ResultadoCalculo(OrigemCalculo.CALCULADORA, simulado, itens, List.copyOf(avisos));
    }

    private static ItemCalculado item(Objeto o) {
        var ibsCbs = o.tribCalc() == null ? null : o.tribCalc().ibsCbs();
        GrupoIbsCbs g = ibsCbs == null ? null : ibsCbs.gIBSCBS();
        var is = o.tribCalc() == null ? null : o.tribCalc().impostoSeletivo();

        // Sem grupo gIBSCBS (ex.: CST 410, imunidade): nada a pagar
        BigDecimal vCbs = g == null || g.gCBS() == null ? null : g.gCBS().vCBS();
        BigDecimal vIbsUf = g == null || g.gIBSUF() == null ? null : g.gIBSUF().vIBSUF();
        BigDecimal vIbsMun = g == null || g.gIBSMun() == null ? null : g.gIBSMun().vIBSMun();
        BigDecimal vIs = is == null ? null : is.vIS();
        Tributos2027 tributos = new Tributos2027(dinheiro(vCbs), dinheiro(vIbsUf), dinheiro(vIbsMun), dinheiro(vIs));

        AliquotasAplicadas aliquotas = new AliquotasAplicadas(
                g == null || g.gCBS() == null ? null : g.gCBS().pCBS(),
                g == null || g.gIBSUF() == null ? null : g.gIBSUF().pIBSUF(),
                g == null || g.gIBSMun() == null ? null : g.gIBSMun().pIBSMun(),
                reducao(g == null || g.gCBS() == null ? null : g.gCBS().gRed()),
                reducao(g == null || g.gIBSUF() == null ? null : g.gIBSUF().gRed()),
                is == null ? null : is.pIS());
        return new ItemCalculado(o.nObj(), tributos, aliquotas);
    }

    private static BigDecimal reducao(Reducao r) {
        return r == null ? null : r.pRedAliq();
    }

    private static BigDecimal dinheiro(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, java.math.RoundingMode.HALF_EVEN);
    }

    private Problema problema(RestClientResponseException e) {
        try {
            return json.readValue(e.getResponseBodyAsByteArray(), Problema.class);
        } catch (Exception ignorada) {
            return new Problema(null, null, e.getStatusCode().value(),
                    "HTTP " + e.getStatusCode().value() + " " + e.getResponseBodyAsString());
        }
    }

    private static String ncmDoDetalhe(String detalhe) {
        if (detalhe == null) {
            return null;
        }
        Matcher m = NCM_NO_DETALHE.matcher(detalhe);
        return m.find() ? m.group(1) : null;
    }
}

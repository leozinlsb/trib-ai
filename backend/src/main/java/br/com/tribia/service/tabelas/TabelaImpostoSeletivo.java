package br.com.tribia.service.tabelas;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Alíquotas ad valorem do Imposto Seletivo por NCM (dados-oficiais/aliquotas-is.csv, da base da calculadora oficial).
 * Ex.: 22021000 (bebidas açucaradas) = 10% a partir de 2027.
 */
@Component
public class TabelaImpostoSeletivo {

    private record Regra(String prefixo, BigDecimal aliquota, LocalDate inicio, LocalDate fim, List<String> excecoes) {

        boolean aplica(String ncm, LocalDate data) {
            return ncm.startsWith(prefixo)
                    && excecoes.stream().noneMatch(ncm::startsWith)
                    && (inicio == null || !data.isBefore(inicio))
                    && (fim == null || !data.isAfter(fim));
        }
    }

    private final List<Regra> regras;

    public TabelaImpostoSeletivo() {
        this.regras = CsvOficial.ler("aliquotas-is.csv").stream()
                .map(l -> new Regra(l.get("ncm_prefixo"), new BigDecimal(l.get("aliquota")), data(l.get("inicio_vigencia")),
                        data(l.get("fim_vigencia")),
                        Arrays.stream(l.get("excecoes").split("\\|")).filter(s -> !s.isBlank()).toList()))
                .toList();
    }

    /** Alíquota do IS (em %) para o NCM na data; vazio quando o produto não está no campo do IS. */
    public Optional<BigDecimal> aliquota(String ncm, LocalDate data) {
        if (ncm == null || ncm.isBlank()) {
            return Optional.empty();
        }
        return regras.stream()
                .filter(r -> r.aplica(ncm, data))
                .max(Comparator.comparingInt(r -> r.prefixo().length()))
                .map(Regra::aliquota);
    }

    private static LocalDate data(String s) {
        return s == null || s.isBlank() ? null : LocalDate.parse(s.substring(0, 10));
    }

    int quantidadeDeRegras() {
        return regras.size();
    }
}

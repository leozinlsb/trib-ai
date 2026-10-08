package br.com.tribia.service.tabelas;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regras oficiais "NCM aplicável" (dados-oficiais/ncm-aplicavel.csv, da base da calculadora da Receita): quais
 * cClassTrib a lei associa a um NCM (ex.: arroz 1006.30 → 200003, Anexo I). São PISTAS para a IA, não decisão:
 * ausência de regra não significa tributação integral (medicamentos, por exemplo, dependem do registro na Anvisa).
 */
@Component
public class TabelaNcmAplicavel {

    /** @param prefixo parte inicial do NCM a que a regra se aplica (ex.: "100630") */
    public record Regra(String prefixo, String cClassTrib, String anexo, String itemAnexo) {

        /** Ex.: "200003 (Anexo I, item 1)" */
        public String descricao() {
            return anexo.isBlank() ? cClassTrib : cClassTrib + " (Anexo " + anexo
                    + (itemAnexo.isBlank() ? "" : ", item " + itemAnexo) + ")";
        }
    }

    private record Linha(Regra regra, List<String> excecoes) {
    }

    private final List<Linha> linhas;
    private final Set<String> codigosComLista;

    public TabelaNcmAplicavel() {
        this.linhas = CsvOficial.ler("ncm-aplicavel.csv").stream()
                .map(l -> new Linha(new Regra(l.get("ncm_prefixo"), l.get("cclasstrib"), l.get("anexo"), l.get("item_anexo")),
                        Arrays.stream(l.get("excecoes").split("\\|")).filter(s -> !s.isBlank()).toList()))
                .toList();
        this.codigosComLista = linhas.stream().map(l -> l.regra().cClassTrib()).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * O cClassTrib é um benefício definido por lista de NCMs (anexos da LC 214, ex.: 200003, 200035): só vale para
     * produtos cujo NCM conste na lista. Códigos fora dela (ex.: 200032, medicamentos, que depende de registro na
     * Anvisa) dependem de outros critérios e não têm essa trava.
     */
    public boolean exigeNcmNaLista(String cClassTrib) {
        return codigosComLista.contains(cClassTrib);
    }

    /** A lista oficial do cClassTrib alcança este NCM (por prefixo, descontadas as exceções). */
    public boolean cobre(String ncm, String cClassTrib) {
        return regrasPara(ncm).stream().anyMatch(r -> r.cClassTrib().equals(cClassTrib));
    }

    /** Regras que alcançam o NCM, das mais específicas (prefixo mais longo) para as mais genéricas, sem repetição. */
    public List<Regra> regrasPara(String ncm) {
        if (ncm == null || ncm.isBlank()) {
            return List.of();
        }
        Set<String> vistos = new LinkedHashSet<>();
        return linhas.stream()
                .filter(l -> ncm.startsWith(l.regra().prefixo()) && l.excecoes().stream().noneMatch(ncm::startsWith))
                .map(Linha::regra)
                .sorted(Comparator.comparingInt((Regra r) -> r.prefixo().length()).reversed()
                        .thenComparing(Regra::cClassTrib))
                .filter(r -> vistos.add(r.descricao()))
                .toList();
    }
}

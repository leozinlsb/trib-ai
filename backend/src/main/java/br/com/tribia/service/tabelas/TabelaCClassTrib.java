package br.com.tribia.service.tabelas;

import br.com.tribia.model.RegimeTributario;
import br.com.tribia.service.apuracao.ParametrosClassificacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tabela oficial CST x cClassTrib do IBS/CBS (dados-oficiais/tabela-cclasstrib.csv), carregada na inicialização.
 * É a fonte de verdade para validar qualquer classificação (XML, cache, IA ou manual): código fora dela é rejeitado.
 */
@Component
public class TabelaCClassTrib {

    private static final Logger log = LoggerFactory.getLogger(TabelaCClassTrib.class);

    /**
     * @param aceitaNfe true quando o código pode ser usado em NF-e (modelo 55)
     */
    public record CClassTrib(String cst, String descricaoCst, String codigo, String nome, String descricao,
                             String tipoAliquota, BigDecimal reducaoIbs, BigDecimal reducaoCbs, boolean aceitaNfe,
                             String anexo) {

        public RegimeTributario regime() {
            return RegimeTributario.de(tipoAliquota, reducaoCbs);
        }

        /** Ex.: "Redução de 60%", "Alíquota zero", "Tributação integral". */
        public String descricaoRegime() {
            return switch (regime()) {
                case INTEGRAL -> "Tributação integral";
                case ALIQUOTA_ZERO -> "Alíquota zero (redução de 100%)";
                case REDUZIDA -> "Redução de " + reducaoCbs.stripTrailingZeros().toPlainString() + "%";
                case SEM_INCIDENCIA -> descricaoCst;
                case OUTRO -> tipoAliquota;
            };
        }
    }

    private final Map<String, CClassTrib> porCodigo;

    public TabelaCClassTrib() {
        Map<String, CClassTrib> m = new LinkedHashMap<>();
        for (Map<String, String> l : CsvOficial.ler("tabela-cclasstrib.csv")) {
            CClassTrib c = new CClassTrib(l.get("cst"), l.get("descricao_cst"), l.get("cclasstrib"), l.get("nome"),
                    l.get("descricao"), l.get("tipo_aliquota"), new BigDecimal(l.get("p_red_ibs")),
                    new BigDecimal(l.get("p_red_cbs")), "1".equals(l.get("ind_nfe")), l.get("anexo"));
            m.put(c.codigo(), c);
        }
        this.porCodigo = Collections.unmodifiableMap(m);
        log.info("Tabela cClassTrib carregada: {} códigos ({} aceitos em NF-e)", m.size(),
                m.values().stream().filter(CClassTrib::aceitaNfe).count());
    }

    public Optional<CClassTrib> buscar(String codigo) {
        return Optional.ofNullable(codigo == null ? null : porCodigo.get(codigo.trim()));
    }

    /** O código existe, pertence ao CST informado e pode ser usado em NF-e. */
    public boolean validoParaNfe(String cst, String codigo) {
        return buscar(codigo).filter(c -> c.cst().equals(cst) && c.aceitaNfe()).isPresent();
    }

    public Collection<CClassTrib> todos() {
        return porCodigo.values();
    }

    /**
     * Parâmetros para o cálculo simplificado. "Sem alíquota" (isenção, imunidade...) vira redução de 100%.
     *
     * @throws IllegalArgumentException para código inexistente ou alíquota fixa/uniforme (exige a calculadora oficial)
     */
    public ParametrosClassificacao parametros(String codigo, BigDecimal aliquotaIs) {
        CClassTrib c = buscar(codigo)
                .orElseThrow(() -> new IllegalArgumentException("cClassTrib inexistente na tabela oficial: " + codigo));
        return switch (c.regime()) {
            case SEM_INCIDENCIA -> new ParametrosClassificacao(BigDecimal.valueOf(100), BigDecimal.valueOf(100), aliquotaIs);
            case OUTRO -> throw new IllegalArgumentException("cClassTrib " + codigo + " (" + c.tipoAliquota()
                    + ") não é suportado no cálculo simplificado; use a calculadora oficial");
            default -> new ParametrosClassificacao(c.reducaoCbs(), c.reducaoIbs(), aliquotaIs);
        };
    }
}

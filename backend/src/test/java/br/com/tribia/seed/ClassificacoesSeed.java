package br.com.tribia.seed;

import br.com.tribia.seed.CatalogoSeed.NotaSeed;
import br.com.tribia.service.classificacao.ClassificacoesSeedLoader.Entrada;
import br.com.tribia.util.ChaveClassificacao;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classificação (CST + cClassTrib) de cada NCM do catálogo de demonstração. Gera seed/classificacoes.json,
 * que alimenta o cache global: as notas do seed são classificadas sem chamar a IA.
 *
 * Fonte: regras oficiais de NCM aplicável (dados-oficiais/ncm-aplicavel.csv, base da calculadora da Receita) e a
 * tabela oficial de cClassTrib. Onde a regra por NCM não decide sozinha (ex.: medicamentos, que dependem do
 * registro na Anvisa e da lista do Anexo XIV), a confiança fica abaixo de 0,7 para o item cair na revisão.
 * Não é gabarito tributário: validar com especialista antes de usar com dados reais.
 */
final class ClassificacoesSeed {

    record Regra(String cst, String cClassTrib, String justificativa, BigDecimal confianca) {
    }

    private static final String CESTA = "Cesta Básica Nacional (Anexo I da LC 214/2025): alíquota zero de CBS e IBS. "
            + "A regra oficial por NCM aponta o cClassTrib 200003 para este código.";
    private static final String SEM_REGRA = "NCM sem regra de redução na base oficial (NCM aplicável) e produto sem "
            + "tratamento diferenciado na LC 214/2025: tributação integral.";
    private static final String MEDICAMENTO = "Medicamento registrado na Anvisa: redução de 60% (art. 133 da LC 214/2025, "
            + "cClassTrib 200032). Pode ter alíquota zero se o princípio ativo constar do Anexo XIV (200009): "
            + "confirmar na revisão.";

    static final Map<String, Regra> POR_NCM = Map.ofEntries(
            Map.entry("10063021", new Regra("200", "200003", CESTA, bd("0.95"))),
            Map.entry("07133319", new Regra("200", "200003", CESTA, bd("0.95"))),
            Map.entry("04012010", new Regra("200", "200003", CESTA, bd("0.95"))),
            Map.entry("17019900", new Regra("200", "200003", CESTA, bd("0.95"))),
            Map.entry("09012100", new Regra("200", "200003", CESTA, bd("0.95"))),
            Map.entry("19021900", new Regra("200", "200003", CESTA, bd("0.95"))),
            Map.entry("15079011", new Regra("200", "200034",
                    "Óleo de soja não integra a Cesta Básica Nacional: está no Anexo VII da LC 214/2025 (alimentos "
                            + "destinados ao consumo humano), com redução de 60% (cClassTrib 200034).", bd("0.90"))),
            Map.entry("19053100", new Regra("000", "000001", SEM_REGRA, bd("0.85"))),
            Map.entry("18063210", new Regra("000", "000001", SEM_REGRA, bd("0.90"))),
            Map.entry("22021000", new Regra("000", "000001",
                    "Bebida açucarada: CBS e IBS com tributação integral. Está no campo do Imposto Seletivo (10% em 2027), "
                            + "mas o IS é monofásico: cobrado do fabricante, não na revenda.", bd("0.90"))),
            Map.entry("34025000", new Regra("000", "000001", SEM_REGRA, bd("0.80"))),
            Map.entry("48182000", new Regra("000", "000001",
                    SEM_REGRA + " O Anexo VIII (higiene e limpeza) alcança o papel higiênico, não as toalhas de papel.",
                    bd("0.85"))),
            Map.entry("28289011", new Regra("000", "000001", SEM_REGRA, bd("0.75"))),
            Map.entry("39232190", new Regra("000", "000001", SEM_REGRA, bd("0.90"))),
            Map.entry("96039000", new Regra("000", "000001", SEM_REGRA, bd("0.90"))),
            Map.entry("39249000", new Regra("000", "000001", SEM_REGRA, bd("0.90"))),
            Map.entry("38089419", new Regra("200", "200035",
                    "Desinfetante: produto de limpeza do Anexo VIII da LC 214/2025 (item 5), com redução de 60% "
                            + "(cClassTrib 200035).", bd("0.85"))),
            Map.entry("96190000", new Regra("200", "200035",
                    "Fraldas: produto de higiene pessoal do Anexo VIII da LC 214/2025 (item 7), com redução de 60% "
                            + "(cClassTrib 200035).", bd("0.90"))),
            Map.entry("30059090", new Regra("200", "200030",
                    "Algodão hidrófilo: dispositivo médico do Anexo IV da LC 214/2025 (item 35), com redução de 60% "
                            + "(cClassTrib 200030). O 200005 (100%) vale só para compras da administração pública.",
                    bd("0.85"))),
            Map.entry("40151900", new Regra("200", "200030",
                    "Luvas de procedimento: dispositivo médico do Anexo IV da LC 214/2025 (item 58), com redução de 60% "
                            + "(cClassTrib 200030).", bd("0.85"))),
            Map.entry("30049069", new Regra("200", "200032", MEDICAMENTO, bd("0.65"))),
            Map.entry("30049099", new Regra("200", "200032", MEDICAMENTO, bd("0.65"))),
            Map.entry("21069030", new Regra("000", "000001",
                    "Suplemento alimentar: as únicas regras oficiais para o NCM (200043/200044) tratam de fornecimento à "
                            + "administração pública. Na venda a uma clínica, tributação integral. Se o produto tiver "
                            + "registro de medicamento na Anvisa, seria 200032 (60%): confirmar na revisão.", bd("0.60")))
    );

    private ClassificacoesSeed() {
    }

    /**
     * Uma entrada por produto (NCM + descrição normalizada) das notas do seed. As notas do upload ao vivo
     * ficam de fora de propósito: o que só aparece nelas vai para a IA na demonstração.
     */
    static List<Entrada> entradasDoSeed() {
        Map<String, Entrada> porChave = new LinkedHashMap<>();
        for (NotaSeed s : CatalogoSeed.notas()) {
            if (s.aoVivo()) {
                continue;
            }
            for (GeradorNfe.ItemNfe i : s.nota().itens()) {
                Regra r = POR_NCM.get(i.ncm());
                if (r == null) {
                    throw new IllegalStateException("NCM sem classificação no seed: " + i.ncm() + " " + i.descricao());
                }
                porChave.putIfAbsent(ChaveClassificacao.de(i.ncm(), i.descricao()),
                        new Entrada(i.ncm(), i.descricao(), r.cst(), r.cClassTrib(), r.justificativa(), r.confianca()));
            }
        }
        return List.copyOf(porChave.values());
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}

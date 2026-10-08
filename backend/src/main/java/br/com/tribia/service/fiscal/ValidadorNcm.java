package br.com.tribia.service.fiscal;

import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Fonte;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Pontuacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.ResultadoVerificacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.SituacaoValidacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Validacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Verificacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Vigencia;
import br.com.tribia.service.fiscal.PesquisaNcmIa.Candidata;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaNcmAplicavel;
import br.com.tribia.service.tabelas.TabelaNcmVigente;
import br.com.tribia.service.tabelas.TabelaNcmVigente.Consulta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Verificações automáticas da NCM sugerida, só com dado oficial que o projeto tem. Existência e vigência são
 * conferidas na NCM vigente do Portal Único Siscomex ({@link TabelaNcmVigente}), com a versão da tabela no detalhe.
 * A fonte só traz códigos vigentes na data da extração: código ausente "não consta" (extinto ou inexistente).
 * Validade da NCM não é enquadramento tributário. "Validado pelas verificações disponíveis" nunca equivale a
 * aprovação da Receita Federal.
 */
@Component
public class ValidadorNcm {

    static final String VERIFICACAO_VIGENCIA = "Existência e vigência na NCM";
    static final String VERIFICACAO_JEV = "Avaliação da JEV AI";
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TabelaNcmAplicavel regrasNcm;
    private final TabelaCClassTrib tabela;
    private final TabelaNcmVigente ncmVigente;
    private final BigDecimal confiancaMinima;
    /** diferença de confiança abaixo da qual a segunda candidata é considerada próxima demais */
    private final BigDecimal margemAlternativa;
    /** depois de tantos dias da extração, a tabela pode estar desatualizada: vira alerta */
    private final int diasValidadeTabela;
    /** nota da JEV abaixo da qual a sugestão vai para revisão */
    private final BigDecimal jevLimiteBaixo;
    /** quanto outra candidata precisa superar a sugestão, na nota da JEV, para ser divergência */
    private final BigDecimal jevMargemDivergencia;

    public ValidadorNcm(TabelaNcmAplicavel regrasNcm, TabelaCClassTrib tabela, TabelaNcmVigente ncmVigente,
                        @Value("${tribia.fiscal.confianca-minima:0.70}") BigDecimal confiancaMinima,
                        @Value("${tribia.fiscal.margem-alternativa:0.10}") BigDecimal margemAlternativa,
                        @Value("${tribia.fiscal.ncm-dias-validade-tabela:120}") int diasValidadeTabela,
                        @Value("${tribia.jev.limite-baixo:0.50}") BigDecimal jevLimiteBaixo,
                        @Value("${tribia.jev.margem-divergencia:0.20}") BigDecimal jevMargemDivergencia) {
        this.regrasNcm = regrasNcm;
        this.tabela = tabela;
        this.ncmVigente = ncmVigente;
        this.confiancaMinima = confiancaMinima;
        this.margemAlternativa = margemAlternativa;
        this.diasValidadeTabela = diasValidadeTabela;
        this.jevLimiteBaixo = jevLimiteBaixo;
        this.jevMargemDivergencia = jevMargemDivergencia;
    }

    /**
     * @param descricaoOficial texto oficial (hierarquia da NCM) da sugestão principal; null se ela não consta da tabela
     */
    public record Resultado(Validacao validacao, boolean usouRegrasDaReforma, String descricaoOficial,
                            List<Fonte> fontes) {
    }

    /** @param dataFiscal data em que a vigência é avaliada (a data da análise) */
    public Resultado validar(List<Candidata> candidatas, String ncmAtual, LocalDate dataFiscal) {
        return validar(candidatas, ncmAtual, dataFiscal, Map.of());
    }

    /**
     * @param pontuacoes pontuação da JEV AI por NCM (vazio = JEV não usada). Só sinaliza: discordância ou nota baixa
     *                   viram ALERTA (e a análise vai para revisão); concordância nunca confirma a classificação.
     */
    public Resultado validar(List<Candidata> candidatas, String ncmAtual, LocalDate dataFiscal,
                             Map<String, Pontuacao> pontuacoes) {
        Candidata escolhida = candidatas.get(0);
        String ncm = escolhida.ncm();
        List<Verificacao> verificacoes = new ArrayList<>();
        List<String> divergencias = new ArrayList<>();
        List<String> pendencias = new ArrayList<>();

        verificacoes.add(new Verificacao("Formato da NCM", ResultadoVerificacao.OK, "8 dígitos: " + formatar(ncm) + "."));

        // existência e vigência, com a versão da tabela
        TabelaNcmVigente.Versao versao = ncmVigente.versao();
        String daFonte = " Fonte: NCM " + versao.situacao().toLowerCase() + " (" + versao.ato() + "), extraída em "
                + data(versao.extraidoEm()) + ".";
        Consulta consulta = ncmVigente.consultar(ncm, dataFiscal);
        Vigencia vigencia = null;
        String situacaoNcm;
        switch (consulta.situacao()) {
            case VIGENTE -> {
                vigencia = new Vigencia(iso(consulta.codigo().inicio()), iso(consulta.codigo().fim()));
                situacaoNcm = "Consta da NCM vigente";
                verificacoes.add(new Verificacao(VERIFICACAO_VIGENCIA, ResultadoVerificacao.OK,
                        "Consta da NCM desde " + data(consulta.codigo().inicio()) + inicioPor(consulta) + "." + daFonte));
            }
            case NAO_VIGENTE_NA_DATA -> {
                vigencia = new Vigencia(iso(consulta.codigo().inicio()), iso(consulta.codigo().fim()));
                situacaoNcm = "Fora de vigência na data da análise";
                verificacoes.add(new Verificacao(VERIFICACAO_VIGENCIA, ResultadoVerificacao.ALERTA,
                        "O código só vale a partir de " + data(consulta.codigo().inicio()) + inicioPor(consulta)
                                + "; em " + data(dataFiscal) + " ainda não estava em vigor." + daFonte));
                pendencias.add("Conferir qual NCM vale em " + data(dataFiscal) + ": a sugerida só entra em vigor em "
                        + data(consulta.codigo().inicio()) + ".");
            }
            default -> {
                situacaoNcm = "Não consta da NCM vigente";
                verificacoes.add(new Verificacao(VERIFICACAO_VIGENCIA, ResultadoVerificacao.FALHA,
                        "O código " + formatar(ncm) + " não consta da NCM vigente: pode ter sido extinto ou não existir "
                                + "(a fonte não traz códigos extintos)." + daFonte));
                divergencias.add("A NCM sugerida (" + formatar(ncm) + ") não consta da NCM vigente: não use sem conferir.");
            }
        }
        LocalDate extracao = dataOuNull(versao.extraidoEm());
        if (extracao != null && dataFiscal.isAfter(extracao.plusDays(diasValidadeTabela))) {
            verificacoes.add(new Verificacao("Atualização da tabela NCM", ResultadoVerificacao.ALERTA,
                    "A tabela foi extraída em " + data(extracao) + "; pode haver alteração posterior da NCM."));
            pendencias.add("Atualizar a tabela da NCM (ferramentas/atualizar_ncm.mjs) e refazer a conferência.");
        }

        if (escolhida.confianca().compareTo(confiancaMinima) < 0) {
            verificacoes.add(new Verificacao("Segurança da sugestão", ResultadoVerificacao.ALERTA,
                    "Confiança da análise " + escolhida.confianca() + ", abaixo de " + confiancaMinima + "."));
            pendencias.add("A sugestão tem confiança baixa: complete as informações ou confirme com um especialista.");
        } else {
            verificacoes.add(new Verificacao("Segurança da sugestão", ResultadoVerificacao.OK,
                    "Confiança da análise " + escolhida.confianca() + "."));
        }

        if (candidatas.size() > 1
                && escolhida.confianca().subtract(candidatas.get(1).confianca()).compareTo(margemAlternativa) < 0) {
            Candidata segunda = candidatas.get(1);
            verificacoes.add(new Verificacao("Distância para a alternativa", ResultadoVerificacao.ALERTA,
                    "A alternativa " + formatar(segunda.ncm()) + " ficou próxima (" + segunda.confianca() + ")."));
            pendencias.add("Decidir entre " + formatar(ncm) + " e " + formatar(segunda.ncm())
                    + " com base nas características que as diferenciam.");
        } else {
            verificacoes.add(new Verificacao("Distância para a alternativa", ResultadoVerificacao.OK, null));
        }
        avaliacaoDaJev(ncm, pontuacoes, verificacoes, divergencias, pendencias);

        for (Candidata alternativa : candidatas.subList(1, candidatas.size())) {
            if (ncmVigente.consultar(alternativa.ncm(), dataFiscal).situacao() == TabelaNcmVigente.Situacao.NAO_CONSTA) {
                divergencias.add("A alternativa " + formatar(alternativa.ncm()) + " não consta da NCM vigente.");
            }
        }

        String atual = ncmAtual == null ? "" : ncmAtual.replaceAll("\\D", "");
        if (atual.isEmpty()) {
            verificacoes.add(new Verificacao("Comparação com a NCM usada hoje", ResultadoVerificacao.NAO_REALIZADA,
                    "A empresa não informou a NCM que usa."));
        } else if (atual.equals(ncm)) {
            verificacoes.add(new Verificacao("Comparação com a NCM usada hoje", ResultadoVerificacao.OK,
                    "Coincide com a NCM informada."));
        } else {
            verificacoes.add(new Verificacao("Comparação com a NCM usada hoje", ResultadoVerificacao.ALERTA,
                    "A empresa usa " + formatar(atual) + "."));
            divergencias.add("A NCM usada hoje (" + formatar(atual) + ") difere da sugerida (" + formatar(ncm)
                    + "). Uma NCM errada afeta tributos e benefícios: confira antes de alterar o cadastro.");
        }
        if (atual.length() == 8 && ncmVigente.consultar(atual, dataFiscal).situacao() == TabelaNcmVigente.Situacao.NAO_CONSTA) {
            divergencias.add("A NCM usada hoje (" + formatar(atual) + ") não consta da NCM vigente: o cadastro "
                    + "provavelmente está desatualizado.");
        }

        List<String> regrasAplicaveis = regrasNcm.regrasPara(ncm).stream()
                .map(r -> r.descricao() + tabela.buscar(r.cClassTrib()).map(c -> " – " + c.descricaoRegime()).orElse(""))
                .distinct()
                .toList();
        verificacoes.add(new Verificacao("Regras da reforma tributária para a NCM", ResultadoVerificacao.OK,
                regrasAplicaveis.isEmpty()
                        ? "Nenhum benefício da LC 214/2025 associado a esta NCM na tabela oficial; a ausência não significa tributação integral."
                        : regrasAplicaveis.size() + " regra(s) da tabela oficial associada(s) a esta NCM."));

        boolean falhou = verificacoes.stream().anyMatch(v -> v.resultado() == ResultadoVerificacao.FALHA);
        boolean alerta = verificacoes.stream().anyMatch(v -> v.resultado() == ResultadoVerificacao.ALERTA);
        SituacaoValidacao situacao = falhou ? SituacaoValidacao.INCONSISTENCIA
                : alerta ? SituacaoValidacao.PENDENTE_REVISAO
                : SituacaoValidacao.VALIDADO_VERIFICACOES;

        List<Fonte> fontes = new ArrayList<>();
        fontes.add(new Fonte("Nomenclatura Comum do Mercosul (NCM)", versao.fonte(),
                versao.situacao() + " — " + versao.ato() + " (extraída em " + data(versao.extraidoEm()) + ")",
                consulta.descricaoCompleta(), versao.url()));
        return new Resultado(new Validacao(situacao, situacaoNcm, vigencia, verificacoes, regrasAplicaveis, divergencias,
                pendencias), !regrasAplicaveis.isEmpty(), consulta.descricaoCompleta(), fontes);
    }

    private void avaliacaoDaJev(String ncm, Map<String, Pontuacao> pontuacoes, List<Verificacao> verificacoes,
                                List<String> divergencias, List<String> pendencias) {
        if (pontuacoes == null || pontuacoes.isEmpty()) {
            return;
        }
        Pontuacao daSugestao = pontuacoes.get(ncm);
        if (daSugestao == null || daSugestao.valor() == null) {
            verificacoes.add(new Verificacao(VERIFICACAO_JEV, ResultadoVerificacao.NAO_REALIZADA,
                    "A JEV AI não pontuou a sugestão principal."));
            return;
        }
        Map.Entry<String, Pontuacao> melhor = pontuacoes.entrySet().stream()
                .filter(e -> e.getValue().valor() != null)
                .max(Map.Entry.comparingByValue(java.util.Comparator.comparing(Pontuacao::valor)))
                .orElseThrow();
        String nota = valor(daSugestao);
        if (!melhor.getKey().equals(ncm)
                && melhor.getValue().valor().subtract(daSugestao.valor()).compareTo(jevMargemDivergencia) >= 0) {
            String texto = "Divergência entre a análise (Gemini) e a JEV AI: a JEV pontuou " + formatar(melhor.getKey())
                    + " com " + valor(melhor.getValue()) + " e a sugestão " + formatar(ncm) + " com " + nota + ".";
            verificacoes.add(new Verificacao(VERIFICACAO_JEV, ResultadoVerificacao.ALERTA, texto));
            divergencias.add(texto);
            pendencias.add("Decidir entre " + formatar(ncm) + " (sugestão da análise) e " + formatar(melhor.getKey())
                    + " (melhor avaliada pela JEV AI).");
        } else if (daSugestao.valor().compareTo(jevLimiteBaixo) < 0) {
            verificacoes.add(new Verificacao(VERIFICACAO_JEV, ResultadoVerificacao.ALERTA,
                    "A JEV AI considerou a sugestão pouco compatível com a descrição (" + nota + ", abaixo de "
                            + jevLimiteBaixo.toPlainString().replace('.', ',') + ")."));
            pendencias.add("A JEV AI deu nota baixa à sugestão " + formatar(ncm) + ": confira a descrição e as alternativas.");
        } else {
            verificacoes.add(new Verificacao(VERIFICACAO_JEV, ResultadoVerificacao.OK,
                    "A JEV AI considerou a sugestão compatível (" + nota + "). Isso não confirma a classificação fiscal."));
        }
    }

    private static String valor(Pontuacao p) {
        return p.valor().toPlainString().replace('.', ',') + (p.escala() == null ? "" : " na escala " + p.escala());
    }

    static String formatar(String ncm) {
        return ncm.length() == 8 ? ncm.substring(0, 4) + "." + ncm.substring(4, 6) + "." + ncm.substring(6) : ncm;
    }

    private static String inicioPor(Consulta c) {
        return c.codigo().ato() == null || c.codigo().ato().isBlank() ? "" : " (" + c.codigo().ato() + ")";
    }

    private static String iso(LocalDate d) {
        return d == null ? null : d.toString();
    }

    private static String data(LocalDate d) {
        return d == null ? "data não informada" : BR.format(d);
    }

    private static String data(String iso) {
        LocalDate d = dataOuNull(iso);
        return d == null ? iso : BR.format(d);
    }

    private static LocalDate dataOuNull(String iso) {
        try {
            return iso == null || iso.isBlank() ? null : LocalDate.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

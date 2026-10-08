package br.com.tribia.service.fiscal;

import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.ResultadoVerificacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.SituacaoValidacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Validacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Verificacao;
import br.com.tribia.service.fiscal.PesquisaNcmIa.Candidata;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaNcmAplicavel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Verificações automáticas da NCM sugerida, só com o que o projeto tem de dado oficial. A base da TIPI (existência e
 * vigência do código) não está disponível: essa verificação aparece como NAO_REALIZADA e vira pendência, em vez de
 * ser presumida. "Validado pelas verificações disponíveis" nunca equivale a aprovação da Receita Federal.
 */
@Component
public class ValidadorNcm {

    static final String VIGENCIA_NAO_VERIFICADA = "Vigência não verificada: a base da TIPI não está disponível neste ambiente.";

    private final TabelaNcmAplicavel regrasNcm;
    private final TabelaCClassTrib tabela;
    private final BigDecimal confiancaMinima;
    /** diferença de confiança abaixo da qual a segunda candidata é considerada próxima demais */
    private final BigDecimal margemAlternativa;

    public ValidadorNcm(TabelaNcmAplicavel regrasNcm, TabelaCClassTrib tabela,
                        @Value("${tribia.fiscal.confianca-minima:0.70}") BigDecimal confiancaMinima,
                        @Value("${tribia.fiscal.margem-alternativa:0.10}") BigDecimal margemAlternativa) {
        this.regrasNcm = regrasNcm;
        this.tabela = tabela;
        this.confiancaMinima = confiancaMinima;
        this.margemAlternativa = margemAlternativa;
    }

    public record Resultado(Validacao validacao, boolean usouRegrasDaReforma) {
    }

    public Resultado validar(List<Candidata> candidatas, String ncmAtual) {
        Candidata escolhida = candidatas.get(0);
        String ncm = escolhida.ncm();
        List<Verificacao> verificacoes = new ArrayList<>();
        List<String> divergencias = new ArrayList<>();
        List<String> pendencias = new ArrayList<>();

        verificacoes.add(new Verificacao("Formato da NCM", ResultadoVerificacao.OK, "8 dígitos: " + formatar(ncm) + "."));

        verificacoes.add(new Verificacao("Existência e vigência na TIPI", ResultadoVerificacao.NAO_REALIZADA,
                VIGENCIA_NAO_VERIFICADA));
        pendencias.add("Confirmar na TIPI vigente que a NCM " + formatar(ncm) + " existe e está em vigor.");

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
        return new Resultado(new Validacao(situacao, "Vigência não verificada (base da TIPI indisponível)", null,
                verificacoes, regrasAplicaveis, divergencias, pendencias), !regrasAplicaveis.isEmpty());
    }

    static String formatar(String ncm) {
        return ncm.length() == 8 ? ncm.substring(0, 4) + "." + ncm.substring(4, 6) + "." + ncm.substring(6) : ncm;
    }
}

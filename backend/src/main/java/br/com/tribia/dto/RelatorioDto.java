package br.com.tribia.dto;

import br.com.tribia.model.TipoNota;
import br.com.tribia.service.apuracao.Apuracao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Relatório de uma empresa num período, gerado na hora a partir das notas importadas (não é armazenado).
 * Tudo aqui é dado extraído das notas ou calculado pelas regras de apuração; não há conteúdo gerado por IA.
 *
 * @param identificacao REL-{empresa}-{AAAAMM inicial}-{AAAAMM final}
 * @param situacao      SEM_DADOS (nenhuma nota), PARCIAL (há itens sem classificação) ou COMPLETO
 */
public record RelatorioDto(
        String identificacao,
        Instant geradoEm,
        ClienteDto empresa,
        String periodoDe,
        String periodoAte,
        String situacao,
        Resumo resumo,
        ApuracaoDto apuracaoPisCofins,
        List<Competencia> competencias,
        List<Documento> documentos,
        List<Contraparte> contrapartes,
        List<Observacao> observacoes
) {

    public record Resumo(int notas, int entradas, int saidas, int itens, int itensClassificados,
                         BigDecimal valorEntradas, BigDecimal valorSaidas, int contrapartes) {
    }

    /** PIS + Cofins de hoje: débito das saídas, crédito das entradas (conforme o regime). */
    public record ApuracaoDto(BigDecimal debito, BigDecimal credito, BigDecimal liquido, BigDecimal aPagar,
                              BigDecimal saldoCredor) {
        public static ApuracaoDto de(Apuracao a) {
            return new ApuracaoDto(a.debito(), a.credito(), a.liquido(), a.aPagar(), a.saldoCredor());
        }
    }

    public record Competencia(String competencia, int notas, BigDecimal valorEntradas, BigDecimal valorSaidas,
                              ApuracaoDto apuracao) {
    }

    /**
     * @param pisCofinsDestacado PIS + Cofins que vieram na nota
     * @param pisCofinsApurado   débito (saída) ou crédito (entrada) que a nota gera na apuração de hoje
     */
    public record Documento(Long id, TipoNota tipo, Long numero, Integer serie, LocalDate dataEmissao,
                            String competencia, String contraparteDocumento, String contraparteNome,
                            BigDecimal valorTotal, int itens, int itensClassificados,
                            BigDecimal pisCofinsDestacado, BigDecimal pisCofinsApurado) {
    }

    public record Contraparte(String documento, String nome, boolean fornecedor, boolean clienteFinal, int notas,
                              BigDecimal valor) {
    }

    /**
     * Verificação automática feita pelas regras do sistema, com as notas/itens de origem.
     *
     * @param nivel ATENCAO ou INFO
     * @param total quantidade de ocorrências (as referências podem vir limitadas)
     */
    public record Observacao(String codigo, String nivel, String mensagem, int total, List<Referencia> referencias) {
    }

    public record Referencia(Long notaId, Long numero, Integer item, String descricao) {
    }
}

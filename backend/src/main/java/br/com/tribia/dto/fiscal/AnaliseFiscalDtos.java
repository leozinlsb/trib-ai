package br.com.tribia.dto.fiscal;

import br.com.tribia.model.StatusAnalise;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.Instant;
import java.util.List;

/** Respostas da Inteligência Fiscal, nos formatos do contrato do front. */
public final class AnaliseFiscalDtos {

    private AnaliseFiscalDtos() {
    }

    public record AnaliseResumo(Long id, Long clienteId, String mercadoria, String ncmSugerida, StatusAnalise status,
                                Instant criadaEm, Instant atualizadaEm, boolean relatorioDisponivel) {
    }

    public record Anexo(String nome, long tamanho, String tipo) {
    }

    public record Etapa(StatusAnalise status, Instant em) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Relatorio(boolean disponivel, String downloadUrl) {
    }

    /** Resumo + entrada, anexos, histórico e, quando houver, o resultado (campos achatados como no contrato). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnaliseDetalhe(@JsonUnwrapped AnaliseResumo resumo, MercadoriaEntradaDto entrada, List<Anexo> anexos,
                                 List<Etapa> historico, @JsonUnwrapped ResultadoAnaliseFiscal analise,
                                 String mensagem, Relatorio relatorio) {
    }

    public record Indicadores(long total, long concluidas, long emProcessamento, long aguardandoRevisao) {
    }

    public record Pagina<T>(List<T> itens, long total, int pagina, int tamanho) {
    }
}

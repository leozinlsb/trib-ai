package br.com.tribia.apipublica.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Gestão das chaves da API pública pelo administrador (rotas internas, sessão + CSRF). */
public final class ChaveApiDtos {

    private ChaveApiDtos() {
    }

    public record CriarChaveForm(
            @NotNull(message = "Informe a empresa (clienteId).") Long clienteId,
            @NotBlank(message = "Informe o nome do integrador.") @Size(max = 100, message = "Nome com até 100 caracteres.")
            String nomeIntegrador,
            @Schema(description = "ANALISES_CRIAR, ANALISES_LER, NOTAS_ENVIAR e/ou NOTAS_LER; vazio = todos")
            List<String> escopos,
            @Schema(description = "Validade em dias; vazio = padrão (tribia.api-publica.validade-maxima-dias)")
            @Min(value = 1, message = "validadeDias mínimo 1.") Integer validadeDias,
            @Min(value = 1, message = "Mínimo 1.") @Max(value = 10_000, message = "Máximo 10000.") Integer requisicoesPorMinuto,
            @Min(value = 1, message = "Mínimo 1.") @Max(value = 100_000, message = "Máximo 100000.") Integer cotaDiariaAnalises,
            @Min(value = 1, message = "Mínimo 1.") @Max(value = 100, message = "Máximo 100.") Integer maxAnalisesSimultaneas,
            @Schema(description = "Itens de NF-e enviados à IA por dia; vazio = padrão (500)")
            @Min(value = 1, message = "Mínimo 1.") @Max(value = 1_000_000, message = "Máximo 1000000.") Integer cotaDiariaItensIa) {
    }

    public record ChaveResumo(Long id, String prefixo, String nomeIntegrador, Long clienteId, String empresa,
                              List<String> escopos, String situacao, Instant criadaEm, String criadaPor,
                              Instant expiraEm, Instant revogadaEm, String revogadaPor, Instant ultimoUsoEm,
                              Integer requisicoesPorMinuto, Integer cotaDiariaAnalises, Integer maxAnalisesSimultaneas,
                              Integer cotaDiariaItensIa) {
    }

    @Schema(description = "A chave completa aparece só aqui, uma vez. Não é recuperável depois.")
    public record ChaveCriada(String chave, String aviso, ChaveResumo dados) {
    }
}

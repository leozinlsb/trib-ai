package br.com.tribia.dto.fiscal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** O que a pessoa informa sobre a mercadoria (parte "dados" do envio). */
public record MercadoriaEntradaDto(
        @NotBlank(message = "Informe o nome da mercadoria.") @Size(max = 200, message = "Nome com até 200 caracteres.")
        String nome,
        @NotBlank(message = "Descreva a mercadoria.")
        @Size(min = 20, max = 4000, message = "Descreva a mercadoria com 20 a 4000 caracteres.")
        String descricao,
        @Size(max = 2000, message = "Composição com até 2000 caracteres.") String composicao,
        @Size(max = 2000, message = "Finalidade com até 2000 caracteres.") String finalidade,
        @Size(max = 2000, message = "Características com até 2000 caracteres.") String caracteristicas,
        /** aceita com ou sem pontos; o serviço guarda só os 8 dígitos */
        @Pattern(regexp = "^$|^\\d{4}\\.?\\d{2}\\.?\\d{2}$", message = "A NCM tem 8 dígitos.") String ncmAtual
) {
}

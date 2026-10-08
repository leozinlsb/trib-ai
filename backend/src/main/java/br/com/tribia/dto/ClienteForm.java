package br.com.tribia.dto;

import br.com.tribia.model.Cliente;
import br.com.tribia.model.Regime;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro/edição de empresa. Obrigatórios só os que as regras usam: razão social, CNPJ (vincula as notas)
 * e regime (define a apuração de PIS/Cofins).
 */
public record ClienteForm(
        @NotBlank(message = "Informe a razão social.") @Size(max = 255) String razaoSocial,
        @Size(max = 255) String nomeFantasia,
        @NotBlank(message = "Informe o CNPJ.") String cnpj,
        @NotNull(message = "Informe o regime de apuração.") Regime regime,
        @Size(max = 255) String setor,
        @Pattern(regexp = "^$|[A-Za-z]{2}", message = "A UF deve ter 2 letras.") String uf,
        @Size(max = 255) String municipio,
        @Pattern(regexp = "^$|\\d{7}", message = "O código IBGE do município deve ter 7 dígitos.") String codigoMunicipio,
        @Email(message = "E-mail inválido.") @Size(max = 255) String email,
        @Size(max = 30, message = "Telefone muito longo.") String telefone,
        @Size(max = 255) String responsavel,
        @Size(max = 2000, message = "Observações: no máximo 2000 caracteres.") String observacoes
) {
    public Cliente.Dados dados() {
        return new Cliente.Dados(razaoSocial.trim(), vazioNull(nomeFantasia), regime, vazioNull(setor),
                uf == null || uf.isBlank() ? null : uf.toUpperCase(), vazioNull(municipio), vazioNull(codigoMunicipio),
                vazioNull(email), vazioNull(telefone), vazioNull(responsavel), vazioNull(observacoes));
    }

    private static String vazioNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

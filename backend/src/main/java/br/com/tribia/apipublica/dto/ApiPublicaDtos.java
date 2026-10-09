package br.com.tribia.apipublica.dto;

import br.com.tribia.model.StatusAnalise;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Contrato público v1. Independente dos DTOs internos da tela: mudanças na plataforma não podem mudar estes nomes.
 * Campos novos podem ser acrescentados em v1 (o integrador deve ignorar campos desconhecidos); remover ou mudar o
 * significado de um campo exige /api/v2.
 */
public final class ApiPublicaDtos {

    private ApiPublicaDtos() {
    }

    // ---------------- entrada ----------------

    @Schema(description = "Pedido de análise fiscal (sugestão de NCM) de uma mercadoria.")
    public record SolicitacaoAnalise(
            @Schema(description = "Identificador do produto no sistema de origem (SKU, código interno). Opcional; "
                    + "volta em todas as respostas e permite filtrar a listagem.", example = "ERP-SKU-000123",
                    maxLength = 100)
            @Size(max = 100, message = "referenciaExterna com até 100 caracteres.")
            @Pattern(regexp = "^[A-Za-z0-9._:/#-]*$",
                    message = "referenciaExterna aceita letras, números e . _ : / # -")
            String referenciaExterna,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "Informe a mercadoria.") @Valid
            Mercadoria mercadoria) {
    }

    @Schema(description = "O que se sabe da mercadoria. Quanto mais concreto (material, uso, apresentação), melhor a análise.")
    public record Mercadoria(
            @Schema(example = "Sabonete de glicerina 90 g", maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "Informe o nome da mercadoria.")
            @Size(max = 200, message = "nome com até 200 caracteres.")
            String nome,
            @Schema(example = "Sabonete em barra de glicerina para higiene pessoal, embalado individualmente.",
                    minLength = 20, maxLength = 4000, requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "Descreva a mercadoria.")
            @Size(min = 20, max = 4000, message = "descricao com 20 a 4000 caracteres.")
            String descricao,
            @Schema(example = "glicerina, óleo vegetal", maxLength = 2000)
            @Size(max = 2000, message = "composicao com até 2000 caracteres.")
            String composicao,
            @Schema(example = "higiene pessoal", maxLength = 2000)
            @Size(max = 2000, message = "finalidade com até 2000 caracteres.")
            String finalidade,
            @Schema(example = "barra de 90 g, embalagem individual de papel", maxLength = 2000)
            @Size(max = 2000, message = "caracteristicas com até 2000 caracteres.")
            String caracteristicas,
            @Schema(description = "NCM usada hoje pelo integrador, se houver (8 dígitos, com ou sem pontos). "
                    + "É comparada com a sugestão.", example = "3401.11.90")
            @Pattern(regexp = "^$|^\\d{4}\\.?\\d{2}\\.?\\d{2}$", message = "ncmInformada deve ter 8 dígitos.")
            String ncmInformada) {
    }

    // ---------------- saída ----------------

    @Schema(description = """
            Situação pública da análise (estável). RECEBIDA: na fila. EM_PROCESSAMENTO: IA/validações em curso.
            CONCLUIDA: há resultado e as verificações automáticas não apontaram pendência, ou uma pessoa já revisou.
            AGUARDANDO_REVISAO: há resultado, mas com divergência/pendência para revisão humana.
            INFORMACOES_INSUFICIENTES: os dados não bastam (ver erro.mensagem). FALHOU: erro no processamento.""")
    public enum StatusPublico {
        RECEBIDA, EM_PROCESSAMENTO, CONCLUIDA, AGUARDANDO_REVISAO, INFORMACOES_INSUFICIENTES, FALHOU;

        public static StatusPublico de(StatusAnalise interno) {
            return switch (interno) {
                case AGUARDANDO -> RECEBIDA;
                case INTERPRETANDO, PESQUISANDO_NCM, AVALIANDO, VALIDANDO, GERANDO_RELATORIO -> EM_PROCESSAMENTO;
                case CONCLUIDA -> CONCLUIDA;
                case AGUARDANDO_REVISAO -> AGUARDANDO_REVISAO;
                case INFORMACOES_INSUFICIENTES -> INFORMACOES_INSUFICIENTES;
                case FALHA -> FALHOU;
            };
        }

        public boolean finalizada() {
            return this != RECEBIDA && this != EM_PROCESSAMENTO;
        }
    }

    @Schema(description = "Análise fiscal vista pela API pública.")
    public record AnalisePublica(
            @Schema(description = "Identificador público (UUID).", example = "3f1c2a9e-8d4b-4c11-9a57-2b6f0e7d1c34")
            String id,
            String referenciaExterna,
            StatusPublico status,
            @Schema(description = "Etapa interna do motor (informativa; pode ganhar valores novos).",
                    example = "PESQUISANDO_NCM")
            String etapa,
            @Schema(description = "true quando o status não muda mais sozinho (pode mudar por revisão humana na plataforma).")
            boolean finalizada,
            Instant criadaEm,
            Instant atualizadaEm,
            MercadoriaRecebida mercadoria,
            @Schema(description = "Presente quando status é CONCLUIDA ou AGUARDANDO_REVISAO; null antes disso.")
            ResultadoPublico resultado,
            RevisaoHumanaPublica revisaoHumana,
            @Schema(description = "Presente quando status é FALHOU ou INFORMACOES_INSUFICIENTES.")
            ErroAnalise erro,
            @Schema(description = "Pontos a conferir apontados pelo motor (divergências, pendências).")
            String mensagem,
            @Schema(description = "Avisos fixos de uso responsável do resultado.")
            List<String> avisos) {
    }

    public record MercadoriaRecebida(String nome, String descricao, String composicao, String finalidade,
                                     String caracteristicas, String ncmInformada) {
    }

    @Schema(description = "Resultado da análise. Sugestão de IA conferida contra a NCM vigente: não é classificação definitiva.")
    public record ResultadoPublico(
            @Schema(description = """
                    De onde vem o resultado: SUGESTAO_AUTOMATICA_VERIFICADA (IA + verificações sem pendência),
                    SUGESTAO_AUTOMATICA_PENDENTE_REVISAO (IA com divergência/pendência, aguardando pessoa) ou
                    DECISAO_REVISAO_HUMANA (uma pessoa da empresa decidiu na plataforma: ver revisaoHumana).""")
            String natureza,
            @Schema(description = "NCM sugerida pelo motor (8 dígitos). Não muda com a revisão humana.", example = "34011190")
            String ncmSugerida,
            @Schema(example = "3401.11.90") String ncmSugeridaFormatada,
            @Schema(description = "Texto oficial da NCM pela hierarquia (ou o da IA, se o código não consta da tabela).")
            String descricaoOficial,
            @Schema(description = "VALIDADO_VERIFICACOES, PENDENTE_REVISAO, INFORMACOES_INSUFICIENTES ou INCONSISTENCIA.")
            String situacaoValidacao,
            @Schema(description = "Quando o motor concluiu (ISO-8601, UTC): referência temporal da vigência conferida.")
            String analisadaEm,
            FundamentacaoPublica fundamentacao,
            List<AlternativaPublica> alternativas,
            ValidacaoPublica validacao,
            List<FontePublica> fontes) {
    }

    public record FundamentacaoPublica(List<String> caracteristicasIdentificadas, List<String> motivos,
                                       List<String> regrasConsideradas, List<String> observacoes,
                                       List<String> limitacoes) {
    }

    @Schema(description = "NCM candidata (a primeira é a sugerida).")
    public record AlternativaPublica(String ncm, String ncmFormatada, String descricao, String avaliacao,
                                     @Schema(description = "Pontuação da JEV AI quando ligada; null quando indisponível.")
                                     PontuacaoCompatibilidade pontuacaoCompatibilidade) {
    }

    @Schema(description = "Compatibilidade texto x NCM medida pela JEV AI. NÃO é probabilidade de acerto fiscal.")
    public record PontuacaoCompatibilidade(BigDecimal valor, String escala, String significado) {
    }

    public record ValidacaoPublica(String situacao, String situacaoNcm, VigenciaPublica vigencia,
                                   List<VerificacaoPublica> verificacoes, List<String> regrasAplicaveis,
                                   List<String> divergencias, List<String> pendencias) {
    }

    @Schema(description = "Verificação feita pelo motor. resultado: OK, ALERTA, FALHA ou NAO_REALIZADA.")
    public record VerificacaoPublica(String nome, String resultado, String detalhe) {
    }

    public record VigenciaPublica(String inicio, String fim) {
    }

    public record FontePublica(String titulo, String identificacao, String versao, String trecho, String url) {
    }

    @Schema(description = "Revisão humana registrada na plataforma TribIA por uma pessoa da empresa.")
    public record RevisaoHumanaPublica(
            @Schema(description = "PENDENTE (motor pediu revisão), REALIZADA, NAO_SOLICITADA (sem pendência "
                    + "automática; ainda assim não é classificação definitiva) ou NAO_APLICAVEL (sem resultado).")
            String situacao,
            @Schema(description = "ACEITA (manteve a sugestão) ou ALTERADA (escolheu outra NCM). Só em REALIZADA.")
            String decisao,
            @Schema(description = "NCM decidida pela pessoa na última revisão.") String ncmDecidida,
            String observacao,
            String revisadaEm,
            int totalRevisoes) {
    }

    @Schema(description = "Por que a análise terminou sem resultado.")
    public record ErroAnalise(
            @Schema(description = "ANALISE_FALHOU ou INFORMACOES_INSUFICIENTES") String codigo,
            String mensagem,
            @Schema(description = "Se faz sentido enviar de novo (com nova Idempotency-Key e, se for o caso, mais dados).")
            boolean podeRepetir) {
    }

    public record ResumoAnalise(String id, String referenciaExterna, StatusPublico status, String ncmSugerida,
                                Instant criadaEm, Instant atualizadaEm) {
    }

    public record PaginaPublica<T>(List<T> itens, long total, int pagina, int tamanho) {
    }

    @Schema(description = "Consumo e limites da chave usada na requisição.")
    public record UsoChave(ChaveInfo chave, EmpresaInfo empresa, Limites limites, Consumo consumo) {
    }

    public record ChaveInfo(String prefixo, String integrador, List<String> escopos, Instant expiraEm) {
    }

    public record EmpresaInfo(String cnpj, String razaoSocial) {
    }

    public record Limites(int requisicoesPorMinuto, int cotaDiariaAnalises, int maxAnalisesSimultaneas,
                          @Schema(description = "Itens de NF-e enviados à IA por dia") int cotaDiariaItensIa) {
    }

    @Schema(description = "Contagem do dia corrente no fuso de Brasília.")
    public record Consumo(String dia, long analisesCriadasHoje, long restantesHoje, long emProcessamento,
                          @Schema(description = "Itens de NF-e enviados à IA hoje") long itensIaHoje,
                          long itensIaRestantesHoje,
                          @Schema(description = "Notas enviadas pela API ainda em processamento") long notasEmProcessamento) {
    }
}

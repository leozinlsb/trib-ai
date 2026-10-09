package br.com.tribia.apipublica.servico;

import br.com.tribia.apipublica.dto.ApiPublicaDtos.AlternativaPublica;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.AnalisePublica;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.ErroAnalise;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.FontePublica;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.FundamentacaoPublica;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.MercadoriaRecebida;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.PontuacaoCompatibilidade;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.ResultadoPublico;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.ResumoAnalise;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.RevisaoHumanaPublica;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.StatusPublico;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.ValidacaoPublica;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.VerificacaoPublica;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.VigenciaPublica;
import br.com.tribia.apipublica.model.SolicitacaoApi;
import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.AnaliseDetalhe;
import br.com.tribia.dto.fiscal.MercadoriaEntradaDto;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal;
import br.com.tribia.model.AnaliseFiscal;
import br.com.tribia.model.StatusAnalise;

import java.util.ArrayList;
import java.util.List;

/**
 * Converte a análise do motor (a mesma da plataforma) para o contrato público. Não cria informação: todo campo
 * vem do que o motor gravou; o que não existe fica null. Nome e e-mail de quem revisou não saem (minimização).
 */
final class MapeadorAnalisePublica {

    static final String AVISO_IA = "Sugestão gerada por IA e conferida contra a NCM vigente embarcada: não é "
            + "classificação fiscal definitiva nem decisão da Receita Federal; deve ser conferida por profissional habilitado.";
    static final String AVISO_TRIBUTACAO = "A validade da NCM não define o tratamento tributário (IPI, ICMS, "
            + "PIS/Cofins, CBS/IBS, Imposto Seletivo, benefícios).";
    static final String AVISO_JEV = "A pontuação de compatibilidade da JEV AI mede aderência entre o texto e a NCM; "
            + "não é probabilidade de acerto fiscal.";

    private MapeadorAnalisePublica() {
    }

    static AnalisePublica analise(SolicitacaoApi s, AnaliseDetalhe d) {
        StatusAnalise interno = d.resumo().status();
        StatusPublico status = StatusPublico.de(interno);
        ResultadoAnaliseFiscal r = d.analise();
        boolean temResultado = r != null && r.resultado() != null;
        List<ResultadoAnaliseFiscal.RevisaoHumana> revisoes = r == null || r.revisoes() == null ? List.of() : r.revisoes();

        List<String> avisos = new ArrayList<>(List.of(AVISO_IA, AVISO_TRIBUTACAO));
        if (temResultado && r.alternativas() != null && r.alternativas().stream().anyMatch(a -> a.pontuacao() != null)) {
            avisos.add(AVISO_JEV);
        }
        boolean semResultado = interno == StatusAnalise.FALHA || interno == StatusAnalise.INFORMACOES_INSUFICIENTES;
        return new AnalisePublica(s.getPublicoId(), s.getReferenciaExterna(), status, interno.name(), status.finalizada(),
                d.resumo().criadaEm(), d.resumo().atualizadaEm(), mercadoria(d.entrada()),
                temResultado ? resultado(r, interno, revisoes) : null,
                revisao(interno, temResultado, revisoes),
                semResultado ? erro(interno, d.mensagem()) : null,
                semResultado ? null : d.mensagem(),
                List.copyOf(avisos));
    }

    static ResumoAnalise resumo(SolicitacaoApi s) {
        AnaliseFiscal a = s.getAnalise();
        return new ResumoAnalise(s.getPublicoId(), s.getReferenciaExterna(), StatusPublico.de(a.getStatus()),
                a.getNcmSugerida(), a.getCriadaEm(), a.getAtualizadaEm());
    }

    private static MercadoriaRecebida mercadoria(MercadoriaEntradaDto e) {
        return new MercadoriaRecebida(e.nome(), e.descricao(), e.composicao(), e.finalidade(), e.caracteristicas(),
                e.ncmAtual());
    }

    private static ResultadoPublico resultado(ResultadoAnaliseFiscal r, StatusAnalise interno,
                                              List<ResultadoAnaliseFiscal.RevisaoHumana> revisoes) {
        String natureza = !revisoes.isEmpty() ? "DECISAO_REVISAO_HUMANA"
                : interno == StatusAnalise.CONCLUIDA ? "SUGESTAO_AUTOMATICA_VERIFICADA"
                : "SUGESTAO_AUTOMATICA_PENDENTE_REVISAO";
        ResultadoAnaliseFiscal.Resultado res = r.resultado();
        ResultadoAnaliseFiscal.Fundamentacao f = r.fundamentacao();
        return new ResultadoPublico(natureza, res.ncm(), formatar(res.ncm()), res.descricaoOficial(),
                res.situacaoValidacao() == null ? null : res.situacaoValidacao().name(), res.analisadaEm(),
                f == null ? null : new FundamentacaoPublica(f.caracteristicas(), f.motivos(), f.regrasConsideradas(),
                        f.observacoes(), f.limitacoes()),
                r.alternativas() == null ? List.of() : r.alternativas().stream().map(MapeadorAnalisePublica::alternativa).toList(),
                validacao(r.validacao()),
                r.fontes() == null ? List.of() : r.fontes().stream()
                        .map(x -> new FontePublica(x.titulo(), x.identificacao(), x.versao(), x.trecho(), x.url())).toList());
    }

    private static AlternativaPublica alternativa(ResultadoAnaliseFiscal.Alternativa a) {
        ResultadoAnaliseFiscal.Pontuacao p = a.pontuacao();
        return new AlternativaPublica(a.ncm(), formatar(a.ncm()), a.descricao(), a.avaliacao(),
                p == null ? null : new PontuacaoCompatibilidade(p.valor(), p.escala(), p.significado()));
    }

    private static ValidacaoPublica validacao(ResultadoAnaliseFiscal.Validacao v) {
        if (v == null) {
            return null;
        }
        return new ValidacaoPublica(v.situacao() == null ? null : v.situacao().name(), v.situacaoNcm(),
                v.vigencia() == null ? null : new VigenciaPublica(v.vigencia().inicio(), v.vigencia().fim()),
                v.verificacoes() == null ? List.of() : v.verificacoes().stream()
                        .map(x -> new VerificacaoPublica(x.nome(), x.resultado() == null ? null : x.resultado().name(),
                                x.detalhe())).toList(),
                v.regrasAplicaveis(), v.divergencias(), v.pendencias());
    }

    private static RevisaoHumanaPublica revisao(StatusAnalise interno, boolean temResultado,
                                                List<ResultadoAnaliseFiscal.RevisaoHumana> revisoes) {
        if (!revisoes.isEmpty()) {
            ResultadoAnaliseFiscal.RevisaoHumana ultima = revisoes.get(revisoes.size() - 1);
            return new RevisaoHumanaPublica("REALIZADA", ultima.decisao(), ultima.ncm(), ultima.observacao(),
                    ultima.revisadaEm(), revisoes.size());
        }
        String situacao = !temResultado ? "NAO_APLICAVEL"
                : interno == StatusAnalise.AGUARDANDO_REVISAO ? "PENDENTE" : "NAO_SOLICITADA";
        return new RevisaoHumanaPublica(situacao, null, null, null, null, 0);
    }

    private static ErroAnalise erro(StatusAnalise interno, String mensagem) {
        if (interno == StatusAnalise.INFORMACOES_INSUFICIENTES) {
            return new ErroAnalise("INFORMACOES_INSUFICIENTES", mensagem, true);
        }
        return new ErroAnalise("ANALISE_FALHOU",
                mensagem == null ? "A análise não pôde ser concluída." : mensagem, true);
    }

    static String formatar(String ncm) {
        return ncm == null || ncm.length() != 8 ? ncm
                : ncm.substring(0, 4) + "." + ncm.substring(4, 6) + "." + ncm.substring(6);
    }
}

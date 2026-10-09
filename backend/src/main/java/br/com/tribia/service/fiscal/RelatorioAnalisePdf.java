package br.com.tribia.service.fiscal;

import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.AnaliseDetalhe;
import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.Anexo;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Alternativa;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Fonte;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Fundamentacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Validacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Verificacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.SituacaoValidacao;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.StatusAnalise;
import br.com.tribia.util.CnpjUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Relatório da análise fiscal em PDF (A4), montado só com o que a análise registrou: dados informados, resultado,
 * verificações, alternativas (com a pontuação da JEV quando houver), fundamentação, limitações e fontes com versão.
 * Campos ausentes não aparecem ou aparecem como "não informado"; nada é completado. O aviso de que é sugestão para
 * revisão profissional, não classificação definitiva, vai no topo e no rodapé de todas as páginas.
 *
 * Usa as fontes padrão do PDF (Helvetica, codificação WinAnsi): caracteres fora dela viram "?" em vez de quebrar.
 */
@Component
public class RelatorioAnalisePdf {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(BRASILIA);
    static final String AVISO = "Sugestão gerada com apoio de IA e verificações automáticas. Não é classificação fiscal "
            + "definitiva nem decisão da Receita Federal: confira com profissional habilitado antes de usar.";

    private static final Color VERDE = new Color(0x0F, 0x76, 0x6E);
    private static final Color MARINHO = new Color(0x12, 0x30, 0x6A);

    private static byte[] logotipo() {
        try (var in = RelatorioAnalisePdf.class.getResourceAsStream("/brand/logo-escuro.png")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }
    private static final Color CINZA = new Color(0x55, 0x5F, 0x6D);
    private static final Color CLARO = new Color(0xE6, 0xF2, 0xF1);
    private static final Color ALERTA = new Color(0xB4, 0x53, 0x09);
    private static final Color TEXTO = new Color(0x1F, 0x29, 0x37);

    public byte[] gerar(AnaliseDetalhe a, Cliente cliente, Instant geradoEm) {
        try (PDDocument doc = new PDDocument()) {
            Escritor e = new Escritor(doc);
            escrever(e, a, cliente);
            e.rodapes(a.resumo().id(), DATA_HORA.format(geradoEm));

            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("TribIA - Análise fiscal nº " + a.resumo().id());
            info.setSubject("Sugestão de NCM para revisão profissional");
            info.setProducer("TribIA");
            Calendar c = new GregorianCalendar(TimeZone.getTimeZone(BRASILIA));
            c.setTimeInMillis(geradoEm.toEpochMilli());
            info.setCreationDate(c);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void escrever(Escritor e, AnaliseDetalhe a, Cliente cliente) throws IOException {
        ResultadoAnaliseFiscal r = a.analise();

        e.faixaTitulo("Relatório de análise fiscal de mercadoria", "Análise nº " + a.resumo().id()
                + " · " + nomeStatus(a.resumo().status()));
        e.caixa(AVISO, ALERTA);

        e.secao("Identificação");
        e.campo("Empresa", cliente.getRazaoSocial() + " (CNPJ " + CnpjUtil.formatar(cliente.getCnpj()) + ")");
        e.campo("Análise", "nº " + a.resumo().id());
        e.campo("Solicitada em", DATA_HORA.format(a.resumo().criadaEm()));
        e.campo("Última atualização", DATA_HORA.format(a.resumo().atualizadaEm()));
        e.campo("Situação", nomeStatus(a.resumo().status()));
        boolean revisao = r == null || r.resultado() == null
                || r.resultado().situacaoValidacao() != SituacaoValidacao.VALIDADO_VERIFICACOES;
        boolean revisada = r != null && r.revisoes() != null && !r.revisoes().isEmpty();
        e.campo("Revisão humana", revisada ? "Registrada (ver seção \"Revisão humana\")"
                : revisao ? "Necessária antes de usar o resultado"
                : "Recomendada (as verificações automáticas não apontaram pendência)");

        e.secao("Mercadoria analisada");
        e.campo("Nome", a.entrada().nome());
        e.campo("Descrição", a.entrada().descricao());
        e.campoOpcional("Composição", a.entrada().composicao());
        e.campoOpcional("Finalidade", a.entrada().finalidade());
        e.campoOpcional("Características", a.entrada().caracteristicas());
        e.campo("NCM informada pela empresa", a.entrada().ncmAtual() == null || a.entrada().ncmAtual().isBlank()
                ? "não informada" : ValidadorNcm.formatar(a.entrada().ncmAtual()));
        if (a.anexos() != null && !a.anexos().isEmpty()) {
            List<String> nomes = new ArrayList<>();
            for (Anexo x : a.anexos()) {
                nomes.add(x.nome() + " (" + tamanho(x.tamanho()) + ")");
            }
            e.campo("Anexos enviados", String.join("; ", nomes));
        }

        if (r != null && r.resultado() != null) {
            e.secao("Resultado");
            e.destaque("NCM sugerida: " + ValidadorNcm.formatar(r.resultado().ncm()));
            e.campo("Descrição", r.resultado().descricaoOficial());
            e.campo("Situação da validação", nomeSituacao(r.resultado().situacaoValidacao()));
            if (r.validacao() != null) {
                Validacao v = r.validacao();
                e.campoOpcional("Situação da NCM", v.situacaoNcm());
                if (v.vigencia() != null && v.vigencia().inicio() != null) {
                    e.campo("Vigência", "desde " + dataIso(v.vigencia().inicio())
                            + (v.vigencia().fim() == null ? " (sem data de término na fonte)" : " até " + dataIso(v.vigencia().fim())));
                }
            }
        } else if (a.mensagem() != null) {
            e.secao("Resultado");
            e.paragrafo(a.mensagem());
        }

        if (r != null && r.revisoes() != null && !r.revisoes().isEmpty()) {
            e.secao("Revisão humana");
            e.paragrafoMenor("Registro interno de quem conferiu a sugestão. Não é parecer nem decisão da Receita Federal.");
            for (ResultadoAnaliseFiscal.RevisaoHumana rev : r.revisoes()) {
                String quando = rev.revisadaEm() == null ? "" : DATA_HORA.format(Instant.parse(rev.revisadaEm()));
                e.item(("ACEITA".equals(rev.decisao()) ? "Sugestão aceita: " : "NCM alterada para ")
                        + ValidadorNcm.formatar(rev.ncm())
                        + ("ACEITA".equals(rev.decisao()) || rev.ncmSugerida() == null ? ""
                        : " (sugestão era " + ValidadorNcm.formatar(rev.ncmSugerida()) + ")")
                        + " — " + rev.revisadaPor() + ", " + quando
                        + (rev.observacao() == null ? "" : ". Justificativa: " + rev.observacao()));
            }
        }

        if (r != null && r.validacao() != null) {
            Validacao v = r.validacao();
            e.secao("Verificações automáticas");
            for (Verificacao ver : v.verificacoes()) {
                e.item(nomeResultado(ver.resultado()) + " — " + ver.nome() + (ver.detalhe() == null ? "" : ": " + ver.detalhe()));
            }
            e.lista("Divergências", v.divergencias());
            e.lista("Pendências", v.pendencias());
            if (v.regrasAplicaveis() != null && !v.regrasAplicaveis().isEmpty()) {
                e.secao("Reforma tributária: cClassTrib associados à NCM");
                e.paragrafoMenor("Associação da tabela oficial NCM x cClassTrib (LC 214/2025). Indica possíveis "
                        + "tratamentos de IBS/CBS; o enquadramento depende da operação e não foi decidido nesta análise.");
                for (String regra : v.regrasAplicaveis()) {
                    e.item(regra);
                }
            }
        }

        if (r != null && r.alternativas() != null && !r.alternativas().isEmpty()) {
            e.secao("Classificações avaliadas");
            boolean algumaPontuacao = r.alternativas().stream().anyMatch(x -> x.pontuacao() != null);
            for (Alternativa alt : r.alternativas()) {
                String pont = alt.pontuacao() == null ? "sem pontuação da JEV AI"
                        : "pontuação JEV " + alt.pontuacao().valor().toPlainString().replace('.', ',')
                        + " (escala " + alt.pontuacao().escala() + ")";
                e.item(ValidadorNcm.formatar(alt.ncm()) + " — " + alt.descricao() + ". " + alt.avaliacao() + ". " + pont + ".");
            }
            if (algumaPontuacao) {
                Alternativa com = r.alternativas().stream().filter(x -> x.pontuacao() != null).findFirst().orElseThrow();
                e.paragrafoMenor("Sobre a pontuação: " + com.pontuacao().significado());
            }
        }

        if (r != null && r.fundamentacao() != null) {
            Fundamentacao f = r.fundamentacao();
            e.secao("Fundamentação (análise com IA — Gemini)");
            e.lista("Características consideradas", f.caracteristicas());
            e.lista("Motivos da sugestão", f.motivos());
            e.lista("Regras de interpretação consideradas", f.regrasConsideradas());
            e.lista("Observações", f.observacoes());
            e.lista("Limitações", f.limitacoes());
        }

        if (r != null && r.fontes() != null && !r.fontes().isEmpty()) {
            e.secao("Fontes e versões utilizadas");
            for (Fonte fonte : r.fontes()) {
                StringBuilder sb = new StringBuilder(fonte.titulo());
                if (fonte.identificacao() != null) {
                    sb.append(" — ").append(fonte.identificacao());
                }
                if (fonte.versao() != null) {
                    sb.append(". Versão: ").append(fonte.versao());
                }
                if (fonte.url() != null) {
                    sb.append(". ").append(fonte.url());
                }
                e.item(sb.toString());
                if (fonte.trecho() != null && !fonte.trecho().isBlank()) {
                    e.paragrafoMenor("Texto: " + fonte.trecho());
                }
            }
        }
    }

    // ---------------- textos ----------------

    static String nomeStatus(StatusAnalise s) {
        return switch (s) {
            case AGUARDANDO -> "Aguardando processamento";
            case INTERPRETANDO, PESQUISANDO_NCM, AVALIANDO, VALIDANDO, GERANDO_RELATORIO -> "Em processamento";
            case CONCLUIDA -> "Concluída";
            case FALHA -> "Falha";
            case INFORMACOES_INSUFICIENTES -> "Informações insuficientes";
            case AGUARDANDO_REVISAO -> "Aguardando revisão";
        };
    }

    private static String nomeSituacao(SituacaoValidacao s) {
        return switch (s) {
            case VALIDADO_VERIFICACOES -> "Validado pelas verificações disponíveis (não equivale a aprovação da Receita Federal)";
            case PENDENTE_REVISAO -> "Pendente de revisão";
            case INFORMACOES_INSUFICIENTES -> "Informações insuficientes";
            case INCONSISTENCIA -> "Inconsistência encontrada";
        };
    }

    private static String nomeResultado(ResultadoAnaliseFiscal.ResultadoVerificacao r) {
        return switch (r) {
            case OK -> "[OK]";
            case ALERTA -> "[ALERTA]";
            case FALHA -> "[FALHA]";
            case NAO_REALIZADA -> "[NÃO REALIZADA]";
        };
    }

    private static String dataIso(String iso) {
        return iso.length() == 10 ? iso.substring(8) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4) : iso;
    }

    private static String tamanho(long bytes) {
        return bytes < 1024 ? bytes + " B" : bytes < 1024 * 1024 ? (bytes / 1024) + " KB"
                : String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0).replace('.', ',');
    }

    // ---------------- layout ----------------

    /** Escreve de cima para baixo, quebrando linhas pela largura e abrindo páginas quando necessário. */
    private static final class Escritor {
        private static final PDRectangle A4 = PDRectangle.A4;
        private static final float MARGEM = 50;
        private static final float RODAPE = 40;
        private static final float LARGURA = A4.getWidth() - 2 * MARGEM;

        private final PDDocument doc;
        private final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final Map<Character, Boolean> suportado = new HashMap<>();
        private PDPageContentStream cs;
        private float y;

        Escritor(PDDocument doc) throws IOException {
            this.doc = doc;
            novaPagina();
        }

        private void novaPagina() throws IOException {
            if (cs != null) {
                cs.close();
            }
            PDPage p = new PDPage(A4);
            doc.addPage(p);
            cs = new PDPageContentStream(doc, p);
            y = A4.getHeight() - MARGEM;
        }

        private void garantir(float altura) throws IOException {
            if (y - altura < MARGEM + RODAPE) {
                novaPagina();
            }
        }

        void faixaTitulo(String titulo, String sub) throws IOException {
            float h = 72;
            cs.setNonStrokingColor(MARINHO);
            cs.addRect(0, A4.getHeight() - h, A4.getWidth(), h);
            cs.fill();
            // logotipo oficial (branco, fundo transparente) à direita; se o recurso faltar, cai no texto
            byte[] png = logotipo();
            if (png != null) {
                PDImageXObject img = PDImageXObject.createFromByteArray(doc, png, "logo");
                float alturaLogo = 34;
                float larguraLogo = alturaLogo * img.getWidth() / img.getHeight();
                cs.drawImage(img, A4.getWidth() - MARGEM - larguraLogo, A4.getHeight() - h + (h - alturaLogo) / 2,
                        larguraLogo, alturaLogo);
            } else {
                texto("TribIA", negrito, 14, Color.WHITE, A4.getWidth() - MARGEM - 50, A4.getHeight() - 40);
            }
            texto(titulo, negrito, 16, Color.WHITE, MARGEM, A4.getHeight() - 33);
            texto(sub, normal, 10, Color.WHITE, MARGEM, A4.getHeight() - 52);
            y = A4.getHeight() - h - 18;
        }

        void caixa(String txt, Color cor) throws IOException {
            List<String> linhas = quebrar(txt, normal, 9, LARGURA - 16);
            float h = linhas.size() * 12 + 12;
            garantir(h + 8);
            cs.setNonStrokingColor(new Color(0xFE, 0xF3, 0xC7));
            cs.addRect(MARGEM, y - h, LARGURA, h);
            cs.fill();
            float yy = y - 14;
            for (String l : linhas) {
                texto(l, normal, 9, cor, MARGEM + 8, yy);
                yy -= 12;
            }
            y -= h + 10;
        }

        void secao(String nome) throws IOException {
            garantir(40);
            y -= 8;
            cs.setNonStrokingColor(CLARO);
            cs.addRect(MARGEM, y - 18, LARGURA, 20);
            cs.fill();
            texto(nome, negrito, 11, VERDE, MARGEM + 6, y - 12);
            y -= 28;
        }

        void destaque(String txt) throws IOException {
            garantir(20);
            texto(limpar(txt, negrito), negrito, 13, TEXTO, MARGEM, y);
            y -= 20;
        }

        void campo(String rotulo, String valor) throws IOException {
            String v = valor == null || valor.isBlank() ? "não informado" : valor;
            float colRotulo = 150;
            List<String> linhas = quebrar(v, normal, 10, LARGURA - colRotulo);
            garantir(13);
            texto(limpar(rotulo, negrito), negrito, 9, CINZA, MARGEM, y);
            for (String l : linhas) {
                garantir(13);
                texto(l, normal, 10, TEXTO, MARGEM + colRotulo, y);
                y -= 13;
            }
            y -= 2;
        }

        void campoOpcional(String rotulo, String valor) throws IOException {
            if (valor != null && !valor.isBlank()) {
                campo(rotulo, valor);
            }
        }

        void paragrafo(String txt) throws IOException {
            for (String l : quebrar(txt, normal, 10, LARGURA)) {
                garantir(13);
                texto(l, normal, 10, TEXTO, MARGEM, y);
                y -= 13;
            }
            y -= 4;
        }

        void paragrafoMenor(String txt) throws IOException {
            for (String l : quebrar(txt, normal, 8.5f, LARGURA - 12)) {
                garantir(11);
                texto(l, normal, 8.5f, CINZA, MARGEM + 12, y);
                y -= 11;
            }
            y -= 4;
        }

        void item(String txt) throws IOException {
            List<String> linhas = quebrar(txt, normal, 9.5f, LARGURA - 14);
            for (int i = 0; i < linhas.size(); i++) {
                garantir(12.5f);
                if (i == 0) {
                    texto("•", normal, 9.5f, VERDE, MARGEM + 2, y);
                }
                texto(linhas.get(i), normal, 9.5f, TEXTO, MARGEM + 14, y);
                y -= 12.5f;
            }
            y -= 2;
        }

        void lista(String titulo, List<String> itens) throws IOException {
            if (itens == null || itens.isEmpty()) {
                return;
            }
            garantir(30);
            y -= 2;
            texto(limpar(titulo, negrito), negrito, 9.5f, CINZA, MARGEM, y);
            y -= 13;
            for (String i : itens) {
                item(i);
            }
        }

        /** Rodapé de todas as páginas (aviso, nº da análise, data de geração, página x de n). */
        void rodapes(Long analiseId, String geradoEm) throws IOException {
            cs.close();
            int total = doc.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                try (PDPageContentStream r = new PDPageContentStream(doc, doc.getPage(i),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs = r;
                    cs.setStrokingColor(CLARO);
                    cs.moveTo(MARGEM, MARGEM + 14);
                    cs.lineTo(A4.getWidth() - MARGEM, MARGEM + 14);
                    cs.stroke();
                    texto("TribIA · Análise nº " + analiseId + " · gerado em " + geradoEm
                            + " · sugestão para revisão profissional", normal, 7.5f, CINZA, MARGEM, MARGEM);
                    String pag = "página " + (i + 1) + " de " + total;
                    texto(pag, normal, 7.5f, CINZA, A4.getWidth() - MARGEM - largura(pag, normal, 7.5f), MARGEM);
                }
            }
            cs = null;
        }

        // ---------------- apoio ----------------

        private void texto(String s, PDType1Font fonte, float tamanho, Color cor, float x, float yy) throws IOException {
            cs.beginText();
            cs.setFont(fonte, tamanho);
            cs.setNonStrokingColor(cor);
            cs.newLineAtOffset(x, yy);
            cs.showText(limpar(s, fonte));
            cs.endText();
        }

        private List<String> quebrar(String txt, PDType1Font fonte, float tamanho, float largura) throws IOException {
            List<String> linhas = new ArrayList<>();
            for (String paragrafo : limpar(txt, fonte).split("\n")) {
                StringBuilder linha = new StringBuilder();
                for (String palavra : paragrafo.split(" ")) {
                    String tentativa = linha.isEmpty() ? palavra : linha + " " + palavra;
                    if (largura(tentativa, fonte, tamanho) <= largura) {
                        linha.setLength(0);
                        linha.append(tentativa);
                        continue;
                    }
                    if (!linha.isEmpty()) {
                        linhas.add(linha.toString());
                        linha.setLength(0);
                    }
                    // palavra maior que a linha (ex.: URL): corta em pedaços
                    String resto = palavra;
                    while (largura(resto, fonte, tamanho) > largura && resto.length() > 1) {
                        int n = resto.length();
                        while (n > 1 && largura(resto.substring(0, n), fonte, tamanho) > largura) {
                            n--;
                        }
                        linhas.add(resto.substring(0, n));
                        resto = resto.substring(n);
                    }
                    linha.append(resto);
                }
                linhas.add(linha.toString());
            }
            return linhas;
        }

        private float largura(String s, PDType1Font fonte, float tamanho) throws IOException {
            return fonte.getStringWidth(limpar(s, fonte)) / 1000 * tamanho;
        }

        /** Troca o que a codificação WinAnsi não tem (ex.: emojis) por "?" e normaliza espaços. */
        private String limpar(String s, PDType1Font fonte) {
            if (s == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder(s.length());
            for (char ch : s.replace('\t', ' ').replace('\r', ' ').toCharArray()) {
                if (ch == '\n') {
                    sb.append(ch);
                    continue;
                }
                boolean ok = suportado.computeIfAbsent(ch, c -> {
                    try {
                        fonte.encode(String.valueOf(c));
                        return true;
                    } catch (IOException | IllegalArgumentException e) {
                        return false;
                    }
                });
                sb.append(ok ? ch : '?');
            }
            return sb.toString();
        }
    }
}

package br.com.tribia.service.fiscal;

import br.com.tribia.service.fiscal.LeitorAnexos.AnexoRecusadoException;
import br.com.tribia.service.fiscal.LeitorAnexos.Leitura;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Arquivos montados no próprio teste (PDF pelo PDFBox, docx/xlsx como zip), sem fixtures binárias. */
public class LeitorAnexosTest {

    final LeitorAnexos leitor = new LeitorAnexos();

    public static byte[] pdf(String... linhas) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage p = new PDPage();
            doc.addPage(p);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                for (String l : linhas) {
                    cs.showText(l);
                    cs.newLineAtOffset(0, -16);
                }
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    static byte[] zip(Map<String, byte[]> partes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (var e : partes.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    static byte[] docx(String documentXml) throws IOException {
        Map<String, byte[]> p = new LinkedHashMap<>();
        p.put("[Content_Types].xml", "<Types/>".getBytes(StandardCharsets.UTF_8));
        p.put("word/document.xml", documentXml.getBytes(StandardCharsets.UTF_8));
        return zip(p);
    }

    static final String W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";

    @Test
    void txtEmUtf8EEmWindows1252() {
        assertThat(leitor.ler("a.txt", "txt", "pH 9, glicerina".getBytes(StandardCharsets.UTF_8)).texto()).isEqualTo("pH 9, glicerina");
        assertThat(leitor.ler("b.txt", "txt", "composição".getBytes(Charset.forName("windows-1252"))).texto()).isEqualTo("composição");
    }

    @Test
    void pdfComTextoELido() throws IOException {
        Leitura l = leitor.ler("ficha.pdf", "pdf", pdf("Ficha tecnica: sabonete de glicerina", "Peso liquido 90 g"));
        assertThat(l.lido()).isTrue();
        assertThat(l.texto()).contains("sabonete de glicerina").contains("90 g");
        assertThat(l.observacao()).isNull();
    }

    @Test
    void pdfSemTextoNaoInventaConteudo() throws IOException {
        Leitura l = leitor.ler("scan.pdf", "pdf", pdf());
        assertThat(l.lido()).isFalse();
        assertThat(l.observacao()).contains("sem texto extraível").contains("OCR");
    }

    @Test
    void pdfProtegidoPorSenhaNaoELido() throws IOException {
        byte[] protegido;
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf("segredo"))) {
            StandardProtectionPolicy p = new StandardProtectionPolicy("dono", "usuario", new AccessPermission());
            p.setEncryptionKeyLength(128);
            doc.protect(p);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            protegido = out.toByteArray();
        }
        Leitura l = leitor.ler("x.pdf", "pdf", protegido);
        assertThat(l.lido()).isFalse();
        assertThat(l.observacao()).contains("senha");
    }

    @Test
    void pdfCorrompidoNaoDerrubaAAnalise() {
        Leitura l = leitor.ler("x.pdf", "pdf", "%PDF-1.7\nlixo sem estrutura".getBytes(StandardCharsets.US_ASCII));
        assertThat(l.lido()).isFalse();
        assertThat(l.observacao()).contains("corrompido");
    }

    @Test
    void docxLeParagrafosETabulacoes() throws IOException {
        Leitura l = leitor.ler("ficha.docx", "docx", docx("<w:document " + W + "><w:body>"
                + "<w:p><w:r><w:t>Composição:</w:t><w:tab/><w:t>glicerina</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>Uso: higiene pessoal</w:t></w:r></w:p></w:body></w:document>"));
        assertThat(l.texto()).isEqualTo("Composição: glicerina\nUso: higiene pessoal");
    }

    @Test
    void xlsxLeCelulasComTextosCompartilhadosENumeros() throws IOException {
        Map<String, byte[]> p = new LinkedHashMap<>();
        p.put("xl/sharedStrings.xml", ("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<si><t>Componente</t></si><si><t>Glicerina</t></si></sst>").getBytes(StandardCharsets.UTF_8));
        p.put("xl/worksheets/sheet1.xml", ("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + "<row><c t=\"s\"><v>0</v></c><c t=\"inlineStr\"><is><t>Percentual</t></is></c></row>"
                + "<row><c t=\"s\"><v>1</v></c><c><v>35.5</v></c></row></sheetData></worksheet>").getBytes(StandardCharsets.UTF_8));
        Leitura l = leitor.ler("comp.xlsx", "xlsx", zip(p));
        assertThat(l.texto()).isEqualTo("Componente | Percentual\nGlicerina | 35.5");
    }

    @Test
    void docxComEntidadeExternaNaoLeArquivoDoServidor() throws IOException {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///C:/Windows/win.ini\">]>"
                + "<w:document " + W + "><w:body><w:p><w:r><w:t>&x;</w:t></w:r></w:p></w:body></w:document>";
        Leitura l = leitor.ler("x.docx", "docx", docx(xxe));
        assertThat(l.texto() == null ? "" : l.texto()).doesNotContainIgnoringCase("fonts").doesNotContainIgnoringCase("extensions");
    }

    @Test
    void zipQueDescompactaDemaisERecusado() throws IOException {
        byte[] enorme = new byte[(int) LeitorAnexos.MAX_DESCOMPACTADO_PARTE + 1024]; // zeros: compacta para quase nada
        byte[] bomba = zip(Map.of("word/document.xml", enorme));
        assertThat(bomba.length).isLessThan(100_000);
        assertThatThrownBy(() -> leitor.ler("b.docx", "docx", bomba))
                .isInstanceOf(AnexoRecusadoException.class).hasMessageContaining("anormal");
    }

    @Test
    void conteudoQueNaoCorrespondeAExtensaoERecusado() throws IOException {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        assertThatThrownBy(() -> leitor.ler("ficha.pdf", "pdf", png)).isInstanceOf(AnexoRecusadoException.class)
                .hasMessageContaining("não corresponde");
        assertThatThrownBy(() -> leitor.ler("nota.txt", "txt", new byte[]{'M', 'Z', 0, 0, 3})).isInstanceOf(AnexoRecusadoException.class);
        assertThatThrownBy(() -> leitor.ler("planilha.xlsx", "xlsx", pdf("x"))).isInstanceOf(AnexoRecusadoException.class);
    }

    @Test
    void imagemEOfficeAntigoSaoAceitosMasNaoLidosComMotivo() {
        Leitura img = leitor.ler("foto.png", "png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        assertThat(img.lido()).isFalse();
        assertThat(img.observacao()).contains("OCR");
        Leitura doc = leitor.ler("a.doc", "doc", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
        assertThat(doc.observacao()).contains(".docx");
    }

    @Test
    void textoLongoETruncadoComAviso() {
        Leitura l = leitor.ler("longo.txt", "txt", "a ".repeat(LeitorAnexos.MAX_CARACTERES).getBytes(StandardCharsets.UTF_8));
        assertThat(l.texto()).hasSize(LeitorAnexos.MAX_CARACTERES);
        assertThat(l.observacao()).contains("primeiros");
    }
}

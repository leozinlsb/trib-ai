package br.com.tribia.service.fiscal;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Extrai o texto dos anexos da análise fiscal, com limites de segurança. O conteúdo é conferido contra a extensão
 * (assinatura do arquivo): arquivo disfarçado é recusado. O que não dá para ler vira motivo explícito, nunca texto
 * inventado.
 *
 * <ul>
 *   <li>.txt: UTF-8 (ou Windows-1252, comum em arquivos brasileiros); binário é recusado.</li>
 *   <li>.pdf: texto das primeiras {@value #MAX_PAGINAS_PDF} páginas (PDFBox). PDF protegido por senha, corrompido ou só
 *       com imagem (digitalizado) não tem texto extraível: OCR não faz parte desta versão.</li>
 *   <li>.docx/.xlsx: XML do pacote Office, lido com StAX sem DTD nem entidades externas (XXE) e com teto de bytes
 *       descompactados por arquivo (zip-bomb).</li>
 *   <li>Imagens (.png/.jpg/.jpeg/.webp) e Office antigo (.doc/.xls): aceitos e listados, mas não lidos.</li>
 * </ul>
 */
@Component
public class LeitorAnexos {

    static final int MAX_CARACTERES = 20_000;
    static final int MAX_PAGINAS_PDF = 30;
    /** teto de bytes descompactados por parte do docx/xlsx e no total do pacote */
    static final long MAX_DESCOMPACTADO_PARTE = 8L * 1024 * 1024;
    static final long MAX_DESCOMPACTADO_TOTAL = 30L * 1024 * 1024;
    static final int MAX_ENTRADAS_ZIP = 2_000;

    /** Resultado da leitura de um anexo: texto (null se não lido) e o motivo quando não lido ou truncado. */
    public record Leitura(String nome, String texto, String observacao) {
        public boolean lido() {
            return texto != null && !texto.isBlank();
        }
    }

    /** Conteúdo que não corresponde à extensão ou arquivo malicioso/malformado de forma grave. */
    public static class AnexoRecusadoException extends RuntimeException {
        public AnexoRecusadoException(String mensagem) {
            super(mensagem);
        }
    }

    public Leitura ler(String nome, String extensao, byte[] conteudo) {
        String ext = extensao == null ? "" : extensao.toLowerCase(Locale.ROOT);
        conferirAssinatura(nome, ext, conteudo);
        return switch (ext) {
            case "txt" -> limitar(nome, texto(nome, conteudo), null);
            case "pdf" -> pdf(nome, conteudo);
            case "docx" -> office(nome, conteudo, true);
            case "xlsx" -> office(nome, conteudo, false);
            case "png", "jpg", "jpeg", "webp" -> new Leitura(nome, null,
                    "imagem: leitura de texto em imagem (OCR) não disponível nesta versão");
            case "doc", "xls" -> new Leitura(nome, null,
                    "formato antigo do Office: salve como ." + ext + "x ou PDF para que o texto seja lido");
            default -> new Leitura(nome, null, "formato não lido");
        };
    }

    // ---------------- assinatura (o conteúdo é mesmo do tipo declarado?) ----------------

    static void conferirAssinatura(String nome, String ext, byte[] b) {
        boolean ok = switch (ext) {
            case "pdf" -> comeca(b, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "png" -> comeca(b, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            case "jpg", "jpeg" -> comeca(b, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "webp" -> b.length >= 12 && comeca(b, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && Arrays.equals(Arrays.copyOfRange(b, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII));
            case "docx", "xlsx" -> comeca(b, new byte[]{'P', 'K', 0x03, 0x04});
            case "doc", "xls" -> comeca(b, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
            case "txt" -> !binario(b);
            default -> false;
        };
        if (!ok) {
            throw new AnexoRecusadoException("O conteúdo de \"" + nome + "\" não corresponde a um arquivo ." + ext
                    + ". Envie o arquivo original, sem renomear a extensão.");
        }
    }

    private static boolean comeca(byte[] b, byte[] prefixo) {
        return b.length >= prefixo.length && Arrays.equals(Arrays.copyOf(b, prefixo.length), prefixo);
    }

    /** Texto não tem byte nulo; um arquivo binário renomeado para .txt costuma ter. */
    private static boolean binario(byte[] b) {
        int n = Math.min(b.length, 8192);
        for (int i = 0; i < n; i++) {
            if (b[i] == 0) {
                return true;
            }
        }
        return false;
    }

    // ---------------- leitores ----------------

    static String texto(String nome, byte[] b) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, Charset.forName("windows-1252"));
        }
    }

    private Leitura pdf(String nome, byte[] b) {
        try (PDDocument doc = Loader.loadPDF(b)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(MAX_PAGINAS_PDF);
            String t = stripper.getText(doc);
            String obs = doc.getNumberOfPages() > MAX_PAGINAS_PDF
                    ? "só as primeiras " + MAX_PAGINAS_PDF + " de " + doc.getNumberOfPages() + " páginas foram lidas" : null;
            if (t == null || t.isBlank()) {
                return new Leitura(nome, null, "PDF sem texto extraível (provavelmente digitalizado; OCR não disponível)");
            }
            return limitar(nome, t, obs);
        } catch (InvalidPasswordException e) {
            return new Leitura(nome, null, "PDF protegido por senha: não lido");
        } catch (IOException | RuntimeException e) {
            return new Leitura(nome, null, "PDF corrompido ou ilegível: não lido");
        }
    }

    private Leitura office(String nome, byte[] b, boolean docx) {
        try {
            Map<String, byte[]> partes = partes(b, docx);
            String t = docx ? docx(partes) : xlsx(partes);
            if (t.isBlank()) {
                return new Leitura(nome, null, "documento sem texto");
            }
            return limitar(nome, t, null);
        } catch (AnexoRecusadoException e) {
            throw e;
        } catch (IOException | XMLStreamException | RuntimeException e) {
            return new Leitura(nome, null, "arquivo " + (docx ? "Word" : "Excel") + " corrompido ou ilegível: não lido");
        }
    }

    /** Só as partes de texto do pacote, com teto de entradas e de bytes descompactados. */
    private static Map<String, byte[]> partes(byte[] b, boolean docx) throws IOException {
        Map<String, byte[]> partes = new LinkedHashMap<>();
        long total = 0;
        int entradas = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(b))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (++entradas > MAX_ENTRADAS_ZIP) {
                    throw new AnexoRecusadoException("Arquivo com estrutura anormal (entradas demais).");
                }
                String n = e.getName();
                boolean interessa = docx ? n.equals("word/document.xml")
                        : n.equals("xl/sharedStrings.xml") || n.matches("xl/worksheets/sheet\\d+\\.xml");
                if (!interessa) {
                    continue;
                }
                byte[] parte = lerLimitado(zip);
                total += parte.length;
                if (total > MAX_DESCOMPACTADO_TOTAL) {
                    throw new AnexoRecusadoException("Arquivo descompacta para um tamanho anormal.");
                }
                partes.put(n, parte);
            }
        } catch (ZipException e) {
            throw new IOException(e);
        }
        if (partes.isEmpty()) {
            throw new IOException("pacote Office sem as partes de texto");
        }
        return partes;
    }

    private static byte[] lerLimitado(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long lidos = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            lidos += n;
            if (lidos > MAX_DESCOMPACTADO_PARTE) {
                throw new AnexoRecusadoException("Arquivo descompacta para um tamanho anormal.");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static XMLStreamReader xml(byte[] b) throws XMLStreamException {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        return f.createXMLStreamReader(new ByteArrayInputStream(b));
    }

    /** Texto dos parágrafos (w:p) do corpo; tabulações e quebras viram espaço e nova linha. */
    static String docx(Map<String, byte[]> partes) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        XMLStreamReader r = xml(partes.get("word/document.xml"));
        while (r.hasNext() && sb.length() <= MAX_CARACTERES) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                switch (r.getLocalName()) {
                    case "t" -> sb.append(r.getElementText());
                    case "tab" -> sb.append(' ');
                    case "br" -> sb.append('\n');
                    default -> {
                    }
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT && "p".equals(r.getLocalName())) {
                sb.append('\n');
            }
        }
        r.close();
        return sb.toString();
    }

    /** Células de cada planilha, uma linha por linha da planilha, separadas por " | ". */
    static String xlsx(Map<String, byte[]> partes) throws XMLStreamException {
        List<String> compartilhados = new ArrayList<>();
        byte[] ss = partes.get("xl/sharedStrings.xml");
        if (ss != null) {
            XMLStreamReader r = xml(ss);
            StringBuilder atual = null;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT && "si".equals(r.getLocalName())) {
                    atual = new StringBuilder();
                } else if (ev == XMLStreamConstants.START_ELEMENT && "t".equals(r.getLocalName()) && atual != null) {
                    atual.append(r.getElementText());
                } else if (ev == XMLStreamConstants.END_ELEMENT && "si".equals(r.getLocalName()) && atual != null) {
                    compartilhados.add(atual.toString());
                    atual = null;
                }
            }
            r.close();
        }
        StringBuilder sb = new StringBuilder();
        for (var parte : partes.entrySet()) {
            if (!parte.getKey().startsWith("xl/worksheets/")) {
                continue;
            }
            XMLStreamReader r = xml(parte.getValue());
            List<String> linha = new ArrayList<>();
            String tipo = null;
            while (r.hasNext() && sb.length() <= MAX_CARACTERES) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "c" -> tipo = r.getAttributeValue(null, "t");
                        case "v" -> {
                            String v = r.getElementText();
                            if ("s".equals(tipo)) {
                                int i = Integer.parseInt(v.trim());
                                linha.add(i >= 0 && i < compartilhados.size() ? compartilhados.get(i) : "");
                            } else {
                                linha.add(v);
                            }
                        }
                        case "t" -> linha.add(r.getElementText()); // inlineStr
                        default -> {
                        }
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT && "row".equals(r.getLocalName())) {
                    if (linha.stream().anyMatch(c -> !c.isBlank())) {
                        sb.append(String.join(" | ", linha)).append('\n');
                    }
                    linha.clear();
                }
            }
            r.close();
        }
        return sb.toString();
    }

    private static Leitura limitar(String nome, String texto, String observacao) {
        String t = texto.replace("\u0000", "").replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n{3,}", "\n\n").strip();
        if (t.isEmpty()) {
            return new Leitura(nome, null, "arquivo sem texto");
        }
        if (t.length() > MAX_CARACTERES) {
            String obs = "texto longo: só os primeiros " + MAX_CARACTERES + " caracteres foram considerados";
            return new Leitura(nome, t.substring(0, MAX_CARACTERES), observacao == null ? obs : observacao + "; " + obs);
        }
        return new Leitura(nome, t, observacao);
    }
}

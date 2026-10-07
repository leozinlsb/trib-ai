package br.com.tribia.service;

import br.com.tribia.exception.NotaRejeitadaException;
import br.com.tribia.model.IbsCbsDestacado;
import br.com.tribia.service.nfe.ItemLido;
import br.com.tribia.service.nfe.NfeLida;
import br.com.tribia.service.nfe.NfeLida.Participante;
import br.com.tribia.util.ChaveAcessoUtil;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lê o XML da NF-e (modelo 55, layout 4.00) e devolve {@link NfeLida}.
 * Não aplica regras de negócio: só leitura e validação estrutural.
 * Aceita tanto {@code <NFe>} quanto {@code <nfeProc>} como raiz.
 */
@Service
public class ParserNfeService {

    public static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";

    private static final NamespaceContext NAMESPACES = new NamespaceContext() {
        @Override
        public String getNamespaceURI(String prefix) {
            return "nfe".equals(prefix) ? NS_NFE : XMLConstants.NULL_NS_URI;
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return NS_NFE.equals(namespaceURI) ? "nfe" : null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return List.of("nfe").iterator();
        }
    };

    private final DocumentBuilderFactory factory = criarFactory();

    public NfeLida ler(byte[] xml) {
        Document doc = parse(xml);
        String raiz = doc.getDocumentElement().getLocalName();
        if (!"NFe".equals(raiz) && !"nfeProc".equals(raiz)) {
            throw NotaRejeitadaException.invalida("O arquivo não é uma NF-e: raiz <" + raiz + "> (esperado <NFe> ou <nfeProc>).");
        }
        // XPath não é thread-safe: um por leitura
        Leitor x = new Leitor(XPathFactory.newInstance().newXPath());
        Element inf = (Element) x.no(doc, "//nfe:infNFe");
        if (inf == null) {
            throw NotaRejeitadaException.invalida(
                    "Grupo infNFe não encontrado. Confira se o XML usa o namespace " + NS_NFE + ".");
        }

        String chave = chave(inf, x, doc);
        List<ItemLido> itens = itens(inf, x);
        if (itens.isEmpty()) {
            throw NotaRejeitadaException.invalida("A NF-e não tem itens (det).");
        }

        return new NfeLida(
                chave,
                x.texto(inf, "nfe:ide/nfe:mod"),
                x.inteiro(inf, "nfe:ide/nfe:serie"),
                x.longo(inf, "nfe:ide/nfe:nNF"),
                x.inteiro(inf, "nfe:ide/nfe:finNFe"),
                x.inteiro(inf, "nfe:ide/nfe:tpNF"),
                x.inteiro(inf, "nfe:ide/nfe:tpAmb"),
                dataEmissao(inf, x),
                participante(inf, x, "nfe:emit", "nfe:enderEmit"),
                participante(inf, x, "nfe:dest", "nfe:enderDest"),
                x.valor(inf, "nfe:total/nfe:ICMSTot/nfe:vProd"),
                x.valor(inf, "nfe:total/nfe:ICMSTot/nfe:vNF"),
                itens);
    }

    private String chave(Element inf, Leitor x, Document doc) {
        String id = inf.getAttribute("Id");
        String chave = id.startsWith("NFe") ? id.substring(3) : x.texto(doc, "//nfe:protNFe/nfe:infProt/nfe:chNFe");
        if (!ChaveAcessoUtil.valida(chave)) {
            throw NotaRejeitadaException.invalida("Chave de acesso inválida: " + chave);
        }
        return chave;
    }

    private LocalDate dataEmissao(Element inf, Leitor x) {
        String dhEmi = x.texto(inf, "nfe:ide/nfe:dhEmi");
        try {
            if (dhEmi != null) {
                // mantém a data no fuso da própria nota (competência não pode mudar de mês por conversão)
                return OffsetDateTime.parse(dhEmi).toLocalDate();
            }
            String dEmi = x.texto(inf, "nfe:ide/nfe:dEmi");
            if (dEmi != null) {
                return LocalDate.parse(dEmi);
            }
        } catch (DateTimeParseException e) {
            throw NotaRejeitadaException.invalida("Data de emissão inválida: " + e.getParsedString());
        }
        throw NotaRejeitadaException.invalida("Data de emissão (dhEmi) não encontrada.");
    }

    private Participante participante(Element inf, Leitor x, String grupo, String endereco) {
        Node no = x.no(inf, grupo);
        if (no == null) {
            return null;
        }
        String doc = x.texto(no, "nfe:CNPJ");
        if (doc == null) {
            doc = x.texto(no, "nfe:CPF");
        }
        return new Participante(doc, x.texto(no, "nfe:xNome"), x.texto(no, endereco + "/nfe:UF"));
    }

    private List<ItemLido> itens(Element inf, Leitor x) {
        NodeList dets = x.lista(inf, "nfe:det");
        List<ItemLido> itens = new ArrayList<>(dets.getLength());
        for (int i = 0; i < dets.getLength(); i++) {
            Element det = (Element) dets.item(i);
            String nItem = det.getAttribute("nItem");
            itens.add(new ItemLido(
                    nItem.isBlank() ? i + 1 : Integer.parseInt(nItem),
                    x.texto(det, "nfe:prod/nfe:cProd"),
                    x.texto(det, "nfe:prod/nfe:xProd"),
                    x.texto(det, "nfe:prod/nfe:NCM"),
                    x.texto(det, "nfe:prod/nfe:CFOP"),
                    x.texto(det, "nfe:prod/nfe:uCom"),
                    x.valor(det, "nfe:prod/nfe:qCom"),
                    x.valor(det, "nfe:prod/nfe:vUnCom"),
                    x.valor(det, "nfe:prod/nfe:vProd"),
                    // qualquer grupo de ICMS (ICMS00, ICMS20, ICMS40...) e de PIS/COFINS (Aliq, NT, Outr, Qtde)
                    x.valorOuZero(det, "nfe:imposto/nfe:ICMS/*/nfe:vICMS"),
                    x.texto(det, "nfe:imposto/nfe:PIS/*/nfe:CST"),
                    x.valorOuZero(det, "nfe:imposto/nfe:PIS/*/nfe:vPIS"),
                    x.texto(det, "nfe:imposto/nfe:COFINS/*/nfe:CST"),
                    x.valorOuZero(det, "nfe:imposto/nfe:COFINS/*/nfe:vCOFINS"),
                    ibsCbs(det, x)));
        }
        return itens;
    }

    /** Tags conferidas contra o modelo da calculadora oficial (ver {@link IbsCbsDestacado}). */
    private IbsCbsDestacado ibsCbs(Element det, Leitor x) {
        Node g = x.no(det, "nfe:imposto/nfe:IBSCBS");
        if (g == null) {
            return null;
        }
        return new IbsCbsDestacado(
                x.texto(g, "nfe:CST"),
                x.texto(g, "nfe:cClassTrib"),
                x.valor(g, "nfe:gIBSCBS/nfe:vBC"),
                x.valor(g, "nfe:gIBSCBS/nfe:gIBSUF/nfe:pIBSUF"),
                x.valor(g, "nfe:gIBSCBS/nfe:gIBSUF/nfe:vIBSUF"),
                x.valor(g, "nfe:gIBSCBS/nfe:gIBSMun/nfe:pIBSMun"),
                x.valor(g, "nfe:gIBSCBS/nfe:gIBSMun/nfe:vIBSMun"),
                x.valor(g, "nfe:gIBSCBS/nfe:vIBS"),
                x.valor(g, "nfe:gIBSCBS/nfe:gCBS/nfe:pCBS"),
                x.valor(g, "nfe:gIBSCBS/nfe:gCBS/nfe:vCBS"));
    }

    private Document parse(byte[] xml) {
        if (xml == null || xml.length == 0) {
            throw NotaRejeitadaException.invalida("Arquivo vazio.");
        }
        try {
            DocumentBuilder builder;
            synchronized (factory) { // a factory não é thread-safe; o builder criado é usado só aqui
                builder = factory.newDocumentBuilder();
            }
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (SAXException e) {
            throw NotaRejeitadaException.invalida("XML malformado: " + e.getMessage());
        } catch (IOException | ParserConfigurationException e) {
            throw NotaRejeitadaException.invalida("Não foi possível ler o XML: " + e.getMessage());
        }
    }

    /** Namespace ligado e proteção contra XXE (o XML vem do usuário). */
    private static DocumentBuilderFactory criarFactory() {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Atalhos de XPath com o prefixo "nfe" já configurado. */
    private static final class Leitor {

        private final XPath xpath;

        Leitor(XPath xpath) {
            this.xpath = xpath;
            this.xpath.setNamespaceContext(NAMESPACES);
        }

        Node no(Object ctx, String expr) {
            return (Node) avaliar(ctx, expr, XPathConstants.NODE);
        }

        NodeList lista(Object ctx, String expr) {
            return (NodeList) avaliar(ctx, expr, XPathConstants.NODESET);
        }

        /** Texto do primeiro nó, sem espaços nas pontas; null se ausente ou vazio. */
        String texto(Object ctx, String expr) {
            Node n = no(ctx, expr);
            if (n == null) {
                return null;
            }
            String t = n.getTextContent().trim();
            return t.isEmpty() ? null : t;
        }

        Integer inteiro(Object ctx, String expr) {
            String t = texto(ctx, expr);
            try {
                return t == null ? null : Integer.valueOf(t);
            } catch (NumberFormatException e) {
                throw NotaRejeitadaException.invalida("Valor inteiro inválido em " + expr + ": " + t);
            }
        }

        Long longo(Object ctx, String expr) {
            String t = texto(ctx, expr);
            try {
                return t == null ? null : Long.valueOf(t);
            } catch (NumberFormatException e) {
                throw NotaRejeitadaException.invalida("Valor inteiro inválido em " + expr + ": " + t);
            }
        }

        /** Decimal com ponto, como no layout da NF-e. Mantém a escala do XML. */
        BigDecimal valor(Object ctx, String expr) {
            String t = texto(ctx, expr);
            try {
                return t == null ? null : new BigDecimal(t);
            } catch (NumberFormatException e) {
                throw NotaRejeitadaException.invalida("Valor numérico inválido em " + expr + ": " + t);
            }
        }

        BigDecimal valorOuZero(Object ctx, String expr) {
            BigDecimal v = valor(ctx, expr);
            return v == null ? BigDecimal.ZERO.setScale(2) : v;
        }

        private Object avaliar(Object ctx, String expr, javax.xml.namespace.QName tipo) {
            try {
                return xpath.evaluate(expr, ctx, tipo);
            } catch (XPathExpressionException e) {
                throw new IllegalStateException("XPath inválido: " + expr, e);
            }
        }
    }
}

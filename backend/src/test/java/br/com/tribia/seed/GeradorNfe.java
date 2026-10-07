package br.com.tribia.seed;

import br.com.tribia.util.ChaveAcessoUtil;
import br.com.tribia.util.CnpjUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Gera XMLs de NF-e de teste no mesmo layout do nfe_teste_hackathon.xml:
 * NF-e 4.00, modelo 55, homologação (tpAmb=2), sem assinatura, chave de acesso válida.
 * Os totais são calculados a partir dos itens.
 */
public final class GeradorNfe {

    private static final Map<String, String> CODIGO_UF = Map.of("SP", "35", "RJ", "33", "MG", "31", "PR", "41");
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    /** Situação de PIS/Cofins do item, do ponto de vista do emitente. */
    public enum PisCofins {
        /** Alíquota zero (cesta básica etc.): PISNT/COFINSNT, CST 06. */
        ALIQUOTA_ZERO("06", null, null),
        /** Revenda de produto monofásico: PISNT/COFINSNT, CST 04. */
        MONOFASICO_REVENDA("04", null, null),
        /** Emitente do Lucro Real: CST 01, 1,65% + 7,6%. */
        NAO_CUMULATIVO("01", "1.65", "7.60"),
        /** Emitente do Lucro Presumido: CST 01, 0,65% + 3%. */
        CUMULATIVO("01", "0.65", "3.00");

        final String cst;
        final BigDecimal pPis;
        final BigDecimal pCofins;

        PisCofins(String cst, String pPis, String pCofins) {
            this.cst = cst;
            this.pPis = pPis == null ? null : new BigDecimal(pPis);
            this.pCofins = pCofins == null ? null : new BigDecimal(pCofins);
        }

        boolean tributado() {
            return pPis != null;
        }
    }

    public record Participante(String cnpj, String nome, String uf, String codigoMunicipio, String municipio) {
    }

    /** @param aliquotaIcms zero gera ICMS40 (isento) */
    public record ItemNfe(String codigo, String descricao, String ncm, String unidade,
                          BigDecimal quantidade, BigDecimal valorUnitario,
                          BigDecimal aliquotaIcms, PisCofins pisCofins) {

        BigDecimal valorTotal() {
            return quantidade.multiply(valorUnitario).setScale(2, RoundingMode.HALF_EVEN);
        }
    }

    public record NotaNfe(long numero, OffsetDateTime dataEmissao, Participante emitente,
                          Participante destinatario, List<ItemNfe> itens) {
    }

    private GeradorNfe() {
    }

    public static ItemNfe item(String codigo, String descricao, String ncm, String unidade,
                               int quantidade, String valorUnitario, int aliquotaIcms, PisCofins pisCofins) {
        return new ItemNfe(codigo, descricao, ncm, unidade, BigDecimal.valueOf(quantidade),
                new BigDecimal(valorUnitario), BigDecimal.valueOf(aliquotaIcms), pisCofins);
    }

    public static String chave(NotaNfe n) {
        return ChaveAcessoUtil.montar(codigoUf(n.emitente().uf()),
                n.dataEmissao().format(DateTimeFormatter.ofPattern("yyMM")),
                n.emitente().cnpj(), "55", 1, n.numero(), 1, codigoNumerico(n));
    }

    public static String gerar(NotaNfe n) {
        String chave = chave(n);
        Totais t = new Totais();
        StringBuilder det = new StringBuilder();
        int nItem = 1;
        for (ItemNfe i : n.itens()) {
            det.append(detalhe(nItem++, i, t));
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- NF-e fictícia gerada por GeradorNfe (testes/seed do TribIA). Homologação, sem valor fiscal. -->
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe">
                  <infNFe versao="4.00" Id="NFe%s">
                    <ide>
                      <cUF>%s</cUF>
                      <cNF>%08d</cNF>
                      <natOp>VENDA DE MERCADORIA</natOp>
                      <mod>55</mod>
                      <serie>1</serie>
                      <nNF>%d</nNF>
                      <dhEmi>%s</dhEmi>
                      <tpNF>1</tpNF>
                      <idDest>1</idDest>
                      <cMunFG>%s</cMunFG>
                      <tpImp>1</tpImp>
                      <tpEmis>1</tpEmis>
                      <cDV>%s</cDV>
                      <tpAmb>2</tpAmb>
                      <finNFe>1</finNFe>
                      <indFinal>0</indFinal>
                      <indPres>1</indPres>
                      <procEmi>0</procEmi>
                      <verProc>TribIA-gerador</verProc>
                    </ide>
                    <emit>
                      <CNPJ>%s</CNPJ>
                      <xNome>%s</xNome>
                      <enderEmit>%s</enderEmit>
                      <IE>111111111111</IE>
                      <CRT>3</CRT>
                    </emit>
                    <dest>
                      <CNPJ>%s</CNPJ>
                      <xNome>%s</xNome>
                      <enderDest>%s</enderDest>
                      <indIEDest>1</indIEDest>
                      <IE>222222222222</IE>
                    </dest>
                %s    <total>
                      <ICMSTot>
                        <vBC>%s</vBC>
                        <vICMS>%s</vICMS>
                        <vICMSDeson>0.00</vICMSDeson>
                        <vFCP>0.00</vFCP>
                        <vBCST>0.00</vBCST>
                        <vST>0.00</vST>
                        <vFCPST>0.00</vFCPST>
                        <vFCPSTRet>0.00</vFCPSTRet>
                        <vProd>%s</vProd>
                        <vFrete>0.00</vFrete>
                        <vSeg>0.00</vSeg>
                        <vDesc>0.00</vDesc>
                        <vII>0.00</vII>
                        <vIPI>0.00</vIPI>
                        <vIPIDevol>0.00</vIPIDevol>
                        <vPIS>%s</vPIS>
                        <vCOFINS>%s</vCOFINS>
                        <vOutro>0.00</vOutro>
                        <vNF>%s</vNF>
                      </ICMSTot>
                    </total>
                    <transp><modFrete>9</modFrete></transp>
                    <pag><detPag><tPag>15</tPag><vPag>%s</vPag></detPag></pag>
                    <infAdic><infCpl>NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL</infCpl></infAdic>
                  </infNFe>
                </NFe>
                """.formatted(
                chave, codigoUf(n.emitente().uf()), codigoNumerico(n), n.numero(),
                n.dataEmissao().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                n.emitente().codigoMunicipio(), chave.substring(43),
                n.emitente().cnpj(), esc(n.emitente().nome()), endereco(n.emitente()),
                n.destinatario().cnpj(), esc(n.destinatario().nome()), endereco(n.destinatario()),
                det, t.vBc, t.vIcms, t.vProd, t.vPis, t.vCofins, t.vProd, t.vProd);
    }

    private static String detalhe(int nItem, ItemNfe i, Totais t) {
        BigDecimal vProd = i.valorTotal();
        t.vProd = t.vProd.add(vProd);

        String icms;
        if (i.aliquotaIcms().signum() > 0) {
            BigDecimal vIcms = pct(vProd, i.aliquotaIcms());
            t.vBc = t.vBc.add(vProd);
            t.vIcms = t.vIcms.add(vIcms);
            icms = "<ICMS00><orig>0</orig><CST>00</CST><modBC>3</modBC><vBC>%s</vBC><pICMS>%s</pICMS><vICMS>%s</vICMS></ICMS00>"
                    .formatted(vProd, i.aliquotaIcms().setScale(2), vIcms);
        } else {
            icms = "<ICMS40><orig>0</orig><CST>40</CST></ICMS40>";
        }

        PisCofins pc = i.pisCofins();
        String pis;
        String cofins;
        if (pc.tributado()) {
            BigDecimal vPis = pct(vProd, pc.pPis);
            BigDecimal vCofins = pct(vProd, pc.pCofins);
            t.vPis = t.vPis.add(vPis);
            t.vCofins = t.vCofins.add(vCofins);
            pis = "<PISAliq><CST>%s</CST><vBC>%s</vBC><pPIS>%s</pPIS><vPIS>%s</vPIS></PISAliq>"
                    .formatted(pc.cst, vProd, pc.pPis, vPis);
            cofins = "<COFINSAliq><CST>%s</CST><vBC>%s</vBC><pCOFINS>%s</pCOFINS><vCOFINS>%s</vCOFINS></COFINSAliq>"
                    .formatted(pc.cst, vProd, pc.pCofins, vCofins);
        } else {
            pis = "<PISNT><CST>%s</CST></PISNT>".formatted(pc.cst);
            cofins = "<COFINSNT><CST>%s</CST></COFINSNT>".formatted(pc.cst);
        }

        String qCom = i.quantidade().setScale(4).toPlainString();
        String vUnCom = i.valorUnitario().setScale(10).toPlainString();
        return """
                    <det nItem="%d">
                      <prod>
                        <cProd>%s</cProd>
                        <cEAN>SEM GTIN</cEAN>
                        <xProd>%s</xProd>
                        <NCM>%s</NCM>
                        <CFOP>5102</CFOP>
                        <uCom>%s</uCom>
                        <qCom>%s</qCom>
                        <vUnCom>%s</vUnCom>
                        <vProd>%s</vProd>
                        <cEANTrib>SEM GTIN</cEANTrib>
                        <uTrib>%s</uTrib>
                        <qTrib>%s</qTrib>
                        <vUnTrib>%s</vUnTrib>
                        <indTot>1</indTot>
                      </prod>
                      <imposto>
                        <ICMS>%s</ICMS>
                        <PIS>%s</PIS>
                        <COFINS>%s</COFINS>
                      </imposto>
                    </det>
                """.formatted(nItem, esc(i.codigo()), esc(i.descricao()), i.ncm(), i.unidade(), qCom, vUnCom,
                vProd, i.unidade(), qCom, vUnCom, icms, pis, cofins);
    }

    private static String endereco(Participante p) {
        return "<xLgr>RUA FICTICIA</xLgr><nro>100</nro><xBairro>CENTRO</xBairro><cMun>%s</cMun><xMun>%s</xMun>"
                .formatted(p.codigoMunicipio(), esc(p.municipio()))
                + "<UF>%s</UF><CEP>01001000</CEP><cPais>1058</cPais><xPais>BRASIL</xPais>".formatted(p.uf());
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal aliquota) {
        return base.multiply(aliquota).divide(CEM, 2, RoundingMode.HALF_EVEN);
    }

    /** Determinístico e diferente do número da nota. */
    private static int codigoNumerico(NotaNfe n) {
        return (int) ((n.numero() * 7919L + Long.parseLong(n.emitente().cnpj().substring(0, 8))) % 100_000_000L);
    }

    private static String codigoUf(String uf) {
        String c = CODIGO_UF.get(uf);
        if (c == null) {
            throw new IllegalArgumentException("UF sem código cadastrado no gerador: " + uf);
        }
        return c;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** CNPJ fictício válido a partir dos 12 primeiros dígitos. */
    public static String cnpj(String base12) {
        return CnpjUtil.completarDigitos(base12);
    }

    private static final class Totais {
        BigDecimal vBc = BigDecimal.ZERO.setScale(2);
        BigDecimal vIcms = BigDecimal.ZERO.setScale(2);
        BigDecimal vProd = BigDecimal.ZERO.setScale(2);
        BigDecimal vPis = BigDecimal.ZERO.setScale(2);
        BigDecimal vCofins = BigDecimal.ZERO.setScale(2);
    }
}

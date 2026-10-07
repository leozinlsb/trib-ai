package br.com.tribia.seed;

import br.com.tribia.seed.GeradorNfe.NotaNfe;
import br.com.tribia.seed.GeradorNfe.Participante;

import java.time.OffsetDateTime;
import java.util.List;

import static br.com.tribia.seed.GeradorNfe.PisCofins.ALIQUOTA_ZERO;
import static br.com.tribia.seed.GeradorNfe.PisCofins.CUMULATIVO;
import static br.com.tribia.seed.GeradorNfe.PisCofins.MONOFASICO_REVENDA;
import static br.com.tribia.seed.GeradorNfe.PisCofins.NAO_CUMULATIVO;
import static br.com.tribia.seed.GeradorNfe.cnpj;
import static br.com.tribia.seed.GeradorNfe.item;

/**
 * Notas de demonstração dos 3 clientes (adendo, seção 7): 3 saídas e 3 entradas por cliente,
 * de agosto a outubro de 2026, mais 1 nota por cliente reservada para o upload ao vivo.
 *
 * Todos os nomes e CNPJs são fictícios. NCMs e CSTs de PIS/Cofins são plausíveis, mas ilustrativos:
 * não são gabarito tributário.
 * Farmácia e loja vendem por NF-e modelo 55 para empresas (clínicas, condomínios), porque a venda ao
 * consumidor sai em NFC-e, que está fora do MVP.
 */
public final class CatalogoSeed {

    /** @param aoVivo true = fica fora do seed, para o upload na demo */
    public record NotaSeed(String clienteSlug, String cnpjCliente, NotaNfe nota, boolean aoVivo) {
    }

    // ---- Clientes (iguais ao data.sql) ----
    static final Participante DISTRIBUIDORA = sp("10433218000193", "DISTRIBUIDORA FICTICIA DE ALIMENTOS LTDA");
    static final Participante FARMACIA = new Participante("45723174000110", "FARMACIA FICTICIA SAUDE LTDA",
            "SP", "3509502", "CAMPINAS");
    static final Participante LOJA = sp("31592846000191", "UTILIDADES E LIMPEZA FICTICIA LTDA");

    // ---- Contrapartes da distribuidora ----
    static final Participante MERCADO_BOM_PRECO = sp(cnpj("278653450001"), "MERCADO FICTICIO BOM PRECO LTDA");
    static final Participante SUPERMERCADO_VILA_NOVA = sp(cnpj("384729150001"), "SUPERMERCADO FICTICIO VILA NOVA LTDA");
    static final Participante MERCEARIA_SAO_JOAO = sp(cnpj("413857290001"), "MERCEARIA FICTICIA SAO JOAO LTDA");
    static final Participante CEREALISTA = sp(cnpj("562398410001"), "CEREALISTA FICTICIA GRAOS DO SUL LTDA");
    static final Participante LATICINIOS = sp(cnpj("609174530001"), "LATICINIOS FICTICIOS SERRA AZUL LTDA");
    static final Participante INDUSTRIA_DOCES = sp(cnpj("731846200001"), "INDUSTRIA FICTICIA DE DOCES E BISCOITOS LTDA");

    // ---- Contrapartes da farmácia ----
    static final Participante CLINICA = sp(cnpj("274190360001"), "CLINICA FICTICIA BEM ESTAR LTDA");
    static final Participante LAR_IDOSOS = sp(cnpj("358201740001"), "LAR DE IDOSOS FICTICIO RECANTO LTDA");
    static final Participante CONSULTORIO = sp(cnpj("469315820001"), "CONSULTORIO ODONTOLOGICO FICTICIO SORRISO LTDA");
    static final Participante DISTRIB_MEDICAMENTOS = sp(cnpj("581027430001"), "DISTRIBUIDORA FICTICIA DE MEDICAMENTOS PHARMA LTDA");
    static final Participante ATACADO_HIGIENE = sp(cnpj("692138540001"), "ATACADO FICTICIO DE HIGIENE E BEM-ESTAR LTDA");

    // ---- Contrapartes da loja ----
    static final Participante CONDOMINIO = sp(cnpj("152846930001"), "CONDOMINIO FICTICIO JARDIM DAS FLORES");
    static final Participante ESCRITORIO = sp(cnpj("263957140001"), "ESCRITORIO FICTICIO CONTABIL EXEMPLO LTDA");
    static final Participante RESTAURANTE = sp(cnpj("374068250001"), "RESTAURANTE FICTICIO SABOR CASEIRO LTDA");
    static final Participante QUIMICA_LIMPA = sp(cnpj("485179360001"), "INDUSTRIA FICTICIA QUIMICA LIMPA LTDA");
    static final Participante ATACADO_UTILIDADES = sp(cnpj("596280470001"), "ATACADO FICTICIO DE UTILIDADES LTDA");

    private CatalogoSeed() {
    }

    public static List<NotaSeed> notas() {
        return List.of(
                // ================= 1. Distribuidora (Lucro Real): cesta básica com alíquota zero =================
                distribuidora(nota(1001, "2026-08-05", DISTRIBUIDORA, MERCADO_BOM_PRECO,
                        item("ARZ001", "ARROZ TIPO 1 5KG", "10063021", "UN", 80, "27.90", 0, ALIQUOTA_ZERO),
                        item("FEJ001", "FEIJAO CARIOCA 1KG", "07133319", "UN", 100, "8.49", 0, ALIQUOTA_ZERO),
                        item("OLE001", "OLEO DE SOJA 900ML", "15079011", "UN", 60, "7.99", 0, ALIQUOTA_ZERO),
                        item("ACU001", "ACUCAR CRISTAL 1KG", "17019900", "UN", 50, "4.59", 0, ALIQUOTA_ZERO),
                        item("BIS001", "BISCOITO RECHEADO CHOCOLATE 130G", "19053100", "UN", 120, "3.49", 18, NAO_CUMULATIVO),
                        item("CHO001", "CHOCOLATE AO LEITE 90G", "18063210", "UN", 80, "5.99", 18, NAO_CUMULATIVO))),
                distribuidora(nota(1002, "2026-09-10", DISTRIBUIDORA, SUPERMERCADO_VILA_NOVA,
                        item("ARZ001", "ARROZ TIPO 1 5KG", "10063021", "UN", 120, "28.50", 0, ALIQUOTA_ZERO),
                        item("LEI001", "LEITE UHT INTEGRAL 1L", "04012010", "UN", 240, "5.29", 0, ALIQUOTA_ZERO),
                        item("CAF001", "CAFE TORRADO E MOIDO 500G", "09012100", "UN", 80, "18.90", 0, ALIQUOTA_ZERO),
                        item("MAC001", "MACARRAO ESPAGUETE 500G", "19021900", "UN", 150, "4.29", 0, ALIQUOTA_ZERO),
                        item("REF001", "REFRIGERANTE COLA 2L", "22021000", "UN", 96, "9.99", 18, MONOFASICO_REVENDA),
                        item("CHO001", "CHOCOLATE AO LEITE 90G", "18063210", "UN", 100, "5.99", 18, NAO_CUMULATIVO))),
                distribuidora(nota(1003, "2026-10-08", DISTRIBUIDORA, MERCEARIA_SAO_JOAO,
                        item("ARZ001", "ARROZ TIPO 1 5KG", "10063021", "UN", 40, "28.90", 0, ALIQUOTA_ZERO),
                        item("FEJ001", "FEIJAO CARIOCA 1KG", "07133319", "UN", 60, "8.69", 0, ALIQUOTA_ZERO),
                        item("LEI001", "LEITE UHT INTEGRAL 1L", "04012010", "UN", 120, "5.49", 0, ALIQUOTA_ZERO),
                        item("BIS001", "BISCOITO RECHEADO CHOCOLATE 130G", "19053100", "UN", 80, "3.59", 18, NAO_CUMULATIVO),
                        item("DET001", "DETERGENTE LIQUIDO NEUTRO 500ML", "34022000", "UN", 72, "2.79", 18, NAO_CUMULATIVO))),
                distribuidora(nota(40211, "2026-08-02", CEREALISTA, DISTRIBUIDORA,
                        item("AR5KG", "ARROZ BRANCO TIPO 1 PACOTE 5KG", "10063021", "UN", 200, "21.50", 0, ALIQUOTA_ZERO),
                        item("FJC1", "FEIJAO CARIOCA TIPO 1 1KG", "07133319", "UN", 150, "6.20", 0, ALIQUOTA_ZERO),
                        item("AC1", "ACUCAR CRISTAL 1KG", "17019900", "UN", 100, "3.40", 0, ALIQUOTA_ZERO))),
                distribuidora(nota(8812, "2026-09-03", LATICINIOS, DISTRIBUIDORA,
                        item("UHT-INT", "LEITE UHT INTEGRAL CAIXA 1L", "04012010", "UN", 480, "3.95", 0, ALIQUOTA_ZERO))),
                distribuidora(nota(15530, "2026-10-01", INDUSTRIA_DOCES, DISTRIBUIDORA,
                        item("BRC130", "BISCOITO RECHEADO SABOR CHOCOLATE 130G", "19053100", "UN", 300, "2.30", 18, NAO_CUMULATIVO),
                        item("CHL90", "CHOCOLATE AO LEITE BARRA 90G", "18063210", "UN", 240, "3.90", 18, NAO_CUMULATIVO))),

                // ================= 2. Farmácia (Lucro Presumido): medicamentos monofásicos =================
                farmacia(nota(501, "2026-08-12", FARMACIA, CLINICA,
                        item("MED001", "DIPIRONA SODICA 500MG 10 COMPRIMIDOS", "30049069", "CX", 90, "6.90", 18, MONOFASICO_REVENDA),
                        item("MED002", "PARACETAMOL 750MG 20 COMPRIMIDOS", "30049069", "CX", 75, "7.50", 18, MONOFASICO_REVENDA),
                        item("MED003", "IBUPROFENO 400MG 10 CAPSULAS", "30049069", "CX", 60, "12.90", 18, MONOFASICO_REVENDA),
                        item("SUP001", "VITAMINA C 1G 30 COMPRIMIDOS EFERVESCENTES", "21069030", "UN", 45, "24.90", 18, CUMULATIVO),
                        item("HIG001", "ALGODAO HIDROFILO 500G", "30059090", "UN", 60, "5.90", 18, CUMULATIVO))),
                farmacia(nota(502, "2026-09-15", FARMACIA, LAR_IDOSOS,
                        item("HIG002", "FRALDA GERIATRICA G 8 UNIDADES", "96190000", "PCT", 120, "39.90", 18, CUMULATIVO),
                        item("MED004", "LOSARTANA POTASSICA 50MG 30 COMPRIMIDOS", "30049099", "CX", 150, "9.90", 18, MONOFASICO_REVENDA),
                        item("MED001", "DIPIRONA SODICA 500MG 10 COMPRIMIDOS", "30049069", "CX", 90, "6.90", 18, MONOFASICO_REVENDA),
                        item("SUP001", "VITAMINA C 1G 30 COMPRIMIDOS EFERVESCENTES", "21069030", "UN", 60, "24.90", 18, CUMULATIVO))),
                farmacia(nota(503, "2026-10-14", FARMACIA, CONSULTORIO,
                        item("MED003", "IBUPROFENO 400MG 10 CAPSULAS", "30049069", "CX", 45, "12.90", 18, MONOFASICO_REVENDA),
                        item("HIG003", "LUVA DE PROCEDIMENTO LATEX M CX 100", "40151900", "CX", 75, "29.90", 18, CUMULATIVO),
                        item("HIG001", "ALGODAO HIDROFILO 500G", "30059090", "UN", 45, "5.90", 18, CUMULATIVO))),
                farmacia(nota(70114, "2026-08-04", DISTRIB_MEDICAMENTOS, FARMACIA,
                        item("7891-DIP", "DIPIRONA SODICA 500MG CX 10 COMP", "30049069", "CX", 150, "3.80", 18, MONOFASICO_REVENDA),
                        item("7891-PAR", "PARACETAMOL 750MG CX 20 COMP", "30049069", "CX", 120, "4.10", 18, MONOFASICO_REVENDA),
                        item("7891-IBU", "IBUPROFENO 400MG CX 10 CAPS", "30049069", "CX", 100, "7.20", 18, MONOFASICO_REVENDA))),
                farmacia(nota(3307, "2026-09-02", ATACADO_HIGIENE, FARMACIA,
                        item("FG-G8", "FRALDA GERIATRICA TAMANHO G PCT 8", "96190000", "PCT", 120, "24.50", 18, NAO_CUMULATIVO),
                        item("VITC-30", "VITAMINA C 1G EFERVESCENTE 30 COMP", "21069030", "UN", 80, "14.90", 18, NAO_CUMULATIVO),
                        item("ALG-500", "ALGODAO HIDROFILO ROLO 500G", "30059090", "UN", 100, "3.20", 18, NAO_CUMULATIVO))),
                farmacia(nota(70388, "2026-10-03", DISTRIB_MEDICAMENTOS, FARMACIA,
                        item("7891-LOS", "LOSARTANA POTASSICA 50MG CX 30 COMP", "30049099", "CX", 200, "5.40", 18, MONOFASICO_REVENDA),
                        item("7891-DIP", "DIPIRONA SODICA 500MG CX 10 COMP", "30049069", "CX", 100, "3.85", 18, MONOFASICO_REVENDA),
                        item("LUV-M100", "LUVA PROCEDIMENTO LATEX M CX 100", "40151900", "CX", 80, "18.90", 18, NAO_CUMULATIVO))),

                // ================= 3. Loja de utilidades e limpeza (Lucro Real): tributação cheia =================
                loja(nota(2101, "2026-08-18", LOJA, CONDOMINIO,
                        item("LIM001", "AGUA SANITARIA 5L", "28289011", "UN", 100, "14.90", 18, NAO_CUMULATIVO),
                        item("LIM002", "DESINFETANTE LAVANDA 2L", "38089419", "UN", 125, "9.90", 18, NAO_CUMULATIVO),
                        item("UTI001", "SACO DE LIXO 100L ROLO 20 UNIDADES", "39232190", "UN", 150, "19.90", 18, NAO_CUMULATIVO),
                        item("UTI002", "VASSOURA DE NYLON", "96039000", "UN", 25, "24.90", 18, NAO_CUMULATIVO))),
                loja(nota(2102, "2026-09-16", LOJA, ESCRITORIO,
                        item("PAP001", "PAPEL TOALHA 2 ROLOS", "48182000", "PCT", 200, "6.49", 18, NAO_CUMULATIVO),
                        item("LIM003", "DETERGENTE LIQUIDO NEUTRO 500ML", "34022000", "UN", 120, "2.79", 18, NAO_CUMULATIVO),
                        item("UTI003", "ESPONJA DUPLA FACE", "39249000", "UN", 150, "2.49", 18, NAO_CUMULATIVO),
                        item("UTI001", "SACO DE LIXO 100L ROLO 20 UNIDADES", "39232190", "UN", 100, "19.90", 18, NAO_CUMULATIVO))),
                loja(nota(2103, "2026-10-12", LOJA, RESTAURANTE,
                        item("LIM003", "DETERGENTE LIQUIDO NEUTRO 500ML", "34022000", "UN", 300, "2.79", 18, NAO_CUMULATIVO),
                        item("LIM002", "DESINFETANTE LAVANDA 2L", "38089419", "UN", 100, "9.90", 18, NAO_CUMULATIVO),
                        item("UTI004", "BALDE PLASTICO 10L", "39249000", "UN", 25, "19.90", 18, NAO_CUMULATIVO),
                        item("UTI003", "ESPONJA DUPLA FACE", "39249000", "UN", 250, "2.49", 18, NAO_CUMULATIVO),
                        item("LIM001", "AGUA SANITARIA 5L", "28289011", "UN", 75, "14.90", 18, NAO_CUMULATIVO))),
                loja(nota(90045, "2026-08-03", QUIMICA_LIMPA, LOJA,
                        item("QL-DET500", "DETERGENTE NEUTRO 500ML", "34022000", "UN", 400, "1.70", 18, NAO_CUMULATIVO),
                        item("QL-DES2L", "DESINFETANTE LAVANDA 2L", "38089419", "UN", 200, "5.60", 18, NAO_CUMULATIVO),
                        item("QL-AS5L", "AGUA SANITARIA 5L", "28289011", "UN", 150, "8.20", 18, NAO_CUMULATIVO))),
                loja(nota(61290, "2026-09-04", ATACADO_UTILIDADES, LOJA,
                        item("AU-VAS", "VASSOURA NYLON CABO 1,20M", "96039000", "UN", 30, "13.50", 18, NAO_CUMULATIVO),
                        item("AU-BAL10", "BALDE PLASTICO 10 LITROS", "39249000", "UN", 30, "10.90", 18, NAO_CUMULATIVO),
                        item("AU-SL100", "SACO LIXO 100L RL 20UN", "39232190", "UN", 200, "11.40", 18, NAO_CUMULATIVO),
                        item("AU-ESP", "ESPONJA DUPLA FACE", "39249000", "UN", 300, "1.20", 18, NAO_CUMULATIVO),
                        item("AU-PT2", "PAPEL TOALHA PCT 2 ROLOS", "48182000", "PCT", 200, "4.10", 18, NAO_CUMULATIVO))),
                loja(nota(90311, "2026-10-02", QUIMICA_LIMPA, LOJA,
                        item("QL-DET500", "DETERGENTE NEUTRO 500ML", "34022000", "UN", 300, "1.72", 18, NAO_CUMULATIVO),
                        item("QL-DES2L", "DESINFETANTE LAVANDA 2L", "38089419", "UN", 120, "5.70", 18, NAO_CUMULATIVO))),

                // ================= Reservadas para o upload ao vivo (fora do seed) =================
                // A da distribuidora é o próprio nfe_teste_hackathon.xml (copiado pelo GerarArquivosSeedTest).
                aoVivo(farmacia(nota(504, "2026-10-22", FARMACIA, CLINICA,
                        item("MED001", "DIPIRONA SODICA 500MG 10 COMPRIMIDOS", "30049069", "CX", 40, "6.90", 18, MONOFASICO_REVENDA),
                        item("SUP001", "VITAMINA C 1G 30 COMPRIMIDOS EFERVESCENTES", "21069030", "UN", 20, "24.90", 18, CUMULATIVO),
                        item("HIG003", "LUVA DE PROCEDIMENTO LATEX M CX 100", "40151900", "CX", 20, "29.90", 18, CUMULATIVO)))),
                aoVivo(loja(nota(2104, "2026-10-25", LOJA, CONDOMINIO,
                        item("LIM001", "AGUA SANITARIA 5L", "28289011", "UN", 30, "14.90", 18, NAO_CUMULATIVO),
                        item("LIM002", "DESINFETANTE LAVANDA 2L", "38089419", "UN", 40, "9.90", 18, NAO_CUMULATIVO),
                        item("UTI001", "SACO DE LIXO 100L ROLO 20 UNIDADES", "39232190", "UN", 50, "19.90", 18, NAO_CUMULATIVO))))
        );
    }

    private static NotaSeed distribuidora(NotaNfe n) {
        return new NotaSeed("1-distribuidora", DISTRIBUIDORA.cnpj(), n, false);
    }

    private static NotaSeed farmacia(NotaNfe n) {
        return new NotaSeed("2-farmacia", FARMACIA.cnpj(), n, false);
    }

    private static NotaSeed loja(NotaNfe n) {
        return new NotaSeed("3-loja", LOJA.cnpj(), n, false);
    }

    private static NotaSeed aoVivo(NotaSeed s) {
        return new NotaSeed(s.clienteSlug(), s.cnpjCliente(), s.nota(), true);
    }

    private static NotaNfe nota(long numero, String data, Participante emitente, Participante destinatario,
                                GeradorNfe.ItemNfe... itens) {
        OffsetDateTime dhEmi = OffsetDateTime.parse(data + "T10:00:00-03:00");
        return new NotaNfe(numero, dhEmi, emitente, destinatario, List.of(itens));
    }

    private static Participante sp(String cnpj, String nome) {
        return new Participante(cnpj, nome, "SP", "3550308", "SAO PAULO");
    }
}

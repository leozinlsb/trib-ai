"""
Extrai os dados oficiais usados pelo TribIA para CSVs em src/main/resources/dados-oficiais/.

Fontes (pacote da Calculadora RTC, portal piloto-cbs.tributos.gov.br):
  - planilha CST_cClassTrib_*.xlsx                      -> tabela-cclasstrib.csv
  - codigo-fonte-backend.zip: calculadora/db/calculadora-pro.db (SQLite)
        NCM_APLICAVEL + EXCECAO_NCM_APLICAVEL + ANEXO   -> ncm-aplicavel.csv
        ALIQUOTA_AD_VALOREM(_PRODUTO) do Imposto Seletivo -> aliquotas-is.csv

Uso (só biblioteca padrão; rode a partir de backend/):
  python -I ferramentas/extrair_dados_oficiais.py <planilha.xlsx> <calculadora-pro.db>

Os campos de texto são limpos (sem ';', aspas ou quebras de linha) para o Java ler com split simples.
"""
import csv
import re
import sqlite3
import sys
import zipfile
import xml.etree.ElementTree as ET
from datetime import date, timedelta
from pathlib import Path

SAIDA = Path("src/main/resources/dados-oficiais")
NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
      "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships"}
VIGENTE_EM = "2027-01-01"


def limpar(texto):
    return re.sub(r"\s+", " ", (texto or "").replace(";", ",").replace('"', "'")).strip()


def data_excel(serial):
    serial = (serial or "").strip()
    return str(date(1899, 12, 30) + timedelta(days=int(float(serial)))) if serial else ""


def ler_aba(xlsx, nome_aba):
    with zipfile.ZipFile(xlsx) as z:
        shared = []
        if "xl/sharedStrings.xml" in z.namelist():
            for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si", NS):
                shared.append("".join(t.text or "" for t in si.iter("{%s}t" % NS["m"])))
        wb = ET.fromstring(z.read("xl/workbook.xml"))
        rels = {r.get("Id"): r.get("Target") for r in ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))}
        sheet = next(s for s in wb.find("m:sheets", NS) if s.get("name") == nome_aba)
        caminho = "xl/" + rels[sheet.get("{%s}id" % NS["r"])].lstrip("/").removeprefix("xl/")
        linhas = []
        for row in ET.fromstring(z.read(caminho)).iter("{%s}row" % NS["m"]):
            valores = {}
            for c in row.findall("m:c", NS):
                v = c.find("m:v", NS)
                if c.get("t") == "s" and v is not None:
                    val = shared[int(v.text)]
                elif c.get("t") == "inlineStr":
                    val = "".join(t.text or "" for t in c.iter("{%s}t" % NS["m"]))
                else:
                    val = v.text if v is not None else ""
                letras = re.match(r"[A-Z]+", c.get("r")).group(0)
                idx = 0
                for ch in letras:
                    idx = idx * 26 + ord(ch) - 64
                valores[idx - 1] = val
            if valores:
                linhas.append([valores.get(i, "") for i in range(max(valores) + 1)])
        return linhas


def gravar(nome, cabecalho, linhas, origem):
    linhas = list(dict.fromkeys(tuple(l) for l in linhas))  # sem duplicadas, mantendo a ordem
    SAIDA.mkdir(parents=True, exist_ok=True)
    with open(SAIDA / nome, "w", newline="", encoding="utf-8") as f:
        f.write(f"# {origem}\n")
        w = csv.writer(f, delimiter=";", quoting=csv.QUOTE_NONE, escapechar="\\")
        w.writerow(cabecalho)
        w.writerows(linhas)
    print(f"{nome}: {len(linhas)} linhas")


def tabela_cclasstrib(xlsx):
    linhas = ler_aba(xlsx, "cClassTrib")
    cab = linhas[0]
    i = {n: cab.index(n) for n in cab}
    saida = []
    for l in linhas[1:]:
        if len(l) <= i["cClassTrib"] or not l[i["cClassTrib"]].strip():
            continue
        l = l + [""] * (len(cab) - len(l))
        saida.append([
            l[i["CST-IBS/CBS"]].strip(), limpar(l[i["Descrição CST-IBS/CBS"]]), l[i["cClassTrib"]].strip(),
            limpar(l[i["Nome cClassTrib"]]), limpar(l[i["Descrição cClassTrib"]]), limpar(l[i["Tipo de Alíquota"]]),
            l[i["pRedIBS"]].strip() or "0", l[i["pRedCBS"]].strip() or "0", l[i["indNFe"]].strip() or "0",
            limpar(l[i["ANEXO"]]), data_excel(l[i["dIniVig"]]), data_excel(l[i["dFimVig"]]),
        ])
    gravar("tabela-cclasstrib.csv",
           ["cst", "descricao_cst", "cclasstrib", "nome", "descricao", "tipo_aliquota", "p_red_ibs", "p_red_cbs",
            "ind_nfe", "anexo", "inicio_vigencia", "fim_vigencia"],
           saida, f"Fonte: {Path(xlsx).name} (tabela oficial CST x cClassTrib do IBS/CBS)")
    return {l[2] for l in saida}


def ncm_aplicavel(db, codigos_ibs_cbs):
    con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    excecoes = {}
    for ncm, ncma_id in con.execute("select ENCM_NCM_CD, ENCM_NCMA_ID from EXCECAO_NCM_APLICAVEL "
                                    "where ENCM_FIM_VIGENCIA is null or ENCM_FIM_VIGENCIA >= ?", (VIGENTE_EM,)):
        excecoes.setdefault(ncma_id, []).append(str(ncm))
    linhas = []
    for ncma_id, ncm, cclass, anexo, item in con.execute("""
            select n.NCMA_ID, n.NCMA_NCM_CD, c.CLTR_CD, a.ANXO_NUMERO, a.ANXO_NUMERO_ITEM
            from NCM_APLICAVEL n
            join CLASSIFICACAO_TRIBUTARIA c on c.CLTR_ID = n.NCMA_CLTR_ID
            left join ANEXO a on a.ANXO_ID = n.NCMA_ANXO_ID
            where n.NCMA_FIM_VIGENCIA is null or n.NCMA_FIM_VIGENCIA >= ?
            order by n.NCMA_NCM_CD, c.CLTR_CD""", (VIGENTE_EM,)):
        if cclass in codigos_ibs_cbs:
            linhas.append([str(ncm), cclass, anexo or "", item or "", "|".join(sorted(excecoes.get(ncma_id, [])))])
    gravar("ncm-aplicavel.csv", ["ncm_prefixo", "cclasstrib", "anexo", "item_anexo", "excecoes"], linhas,
           "Fonte: calculadora-pro.db (NCM_APLICAVEL). NCM que começa com o prefixo pode usar o cClassTrib; "
           "ausência de regra NÃO significa tributação integral (ex.: medicamentos 200032 valem por registro na Anvisa)")
    return con


def aliquotas_is(con):
    excecoes = {}
    for ncm, aavp_id in con.execute("select EAVP_NCM_CD, EAVP_AAVP_ID from EXCECAO_AD_VALOREM_PRODUTO"):
        excecoes.setdefault(aavp_id, []).append(str(ncm))
    linhas = []
    for aavp_id, ncm, valor, ini, fim in con.execute("""
            select p.AAVP_ID, p.AAVP_NCM_CD, v.AADV_VALOR, v.AADV_INICIO_VIGENCIA, v.AADV_FIM_VIGENCIA
            from ALIQUOTA_AD_VALOREM_PRODUTO p join ALIQUOTA_AD_VALOREM v on v.AADV_ID = p.AAVP_AADV_ID
            join TRIBUTO t on t.TBTO_ID = v.AADV_TBTO_ID
            where t.TBTO_SIGLA = 'IS' order by p.AAVP_NCM_CD, v.AADV_INICIO_VIGENCIA"""):
        linhas.append([str(ncm), str(valor), ini or "", fim or "", "|".join(sorted(excecoes.get(aavp_id, [])))])
    gravar("aliquotas-is.csv", ["ncm_prefixo", "aliquota", "inicio_vigencia", "fim_vigencia", "excecoes"], linhas,
           "Fonte: calculadora-pro.db (ALIQUOTA_AD_VALOREM_PRODUTO, tributo IS). Alíquota em %")


if __name__ == "__main__":
    codigos = tabela_cclasstrib(sys.argv[1])
    conexao = ncm_aplicavel(sys.argv[2], codigos)
    aliquotas_is(conexao)

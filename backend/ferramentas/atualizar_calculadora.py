"""
Baixa ou atualiza a Calculadora RTC oficial (distribuição "jar", roda direto no Java, sem WSL nem Docker).

Usa as APIs públicas da Receita:
  - .../api/calculadora/dados-abertos/versao               -> versão publicada (app + base de dados)
  - .../api/calculadora/download/url?platform=jar          -> link do calculadora-jar.zip
O zip traz api-regime-geral.jar e calculadora/db/calculadora-pro.db. Só baixa se a versão publicada mudou.

Uso (só biblioteca padrão):
  python -I ferramentas/atualizar_calculadora.py [pasta]     (padrão: %USERPROFILE%/Desktop/calculadora-rtc)
Depois: ferramentas\\iniciar-calculadora.bat [pasta]
Pare a calculadora antes de atualizar (o Windows bloqueia o jar em uso).
"""
import json
import shutil
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

API = "https://piloto-cbs.tributos.gov.br/servico/calculadora-consumo/api/calculadora"
ARQUIVOS = ("api-regime-geral.jar", "calculadora/db/calculadora-pro.db")


def obter_json(url):
    with urllib.request.urlopen(url, timeout=30) as r:
        return json.load(r)


def main(pasta):
    pasta.mkdir(parents=True, exist_ok=True)
    marcador = pasta / "versao.json"

    publicada = obter_json(f"{API}/dados-abertos/versao")
    print(f"publicada: app {publicada['versaoApp']}, base {publicada['versaoDb']} ({publicada['dataVersaoDb']})")
    if marcador.exists() and all((pasta / a).exists() for a in ARQUIVOS):
        local = json.loads(marcador.read_text(encoding="utf-8"))
        if (local.get("versaoApp"), local.get("versaoDb")) == (publicada["versaoApp"], publicada["versaoDb"]):
            print(f"já atualizada em {pasta}")
            return

    url = obter_json(f"{API}/download/url?platform=jar")["downloadUrl"]
    print(f"baixando {url} ...")
    with tempfile.TemporaryDirectory() as tmp:
        zip_local = Path(tmp) / "calculadora-jar.zip"
        with urllib.request.urlopen(url, timeout=600) as r, open(zip_local, "wb") as out:
            esperado = int(r.headers.get("Content-Length", 0))
            shutil.copyfileobj(r, out, 1 << 20)
        if esperado and zip_local.stat().st_size != esperado:
            sys.exit(f"download incompleto: {zip_local.stat().st_size} de {esperado} bytes; tente de novo")
        with zipfile.ZipFile(zip_local) as z:
            if z.testzip() is not None:
                sys.exit("zip corrompido; tente de novo")
            for nome in ARQUIVOS:
                destino = pasta / nome
                destino.parent.mkdir(parents=True, exist_ok=True)
                with z.open(nome) as src, open(destino, "wb") as dst:
                    shutil.copyfileobj(src, dst, 1 << 20)
                print(f"ok: {destino}")

    marcador.write_text(json.dumps(publicada, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"calculadora {publicada['versaoApp']} pronta em {pasta}")


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else Path.home() / "Desktop" / "calculadora-rtc")

# Ensaio da apresentação contra a API no ar (profile demo). Roda o roteiro N vezes e mostra o tempo de cada passo.
#
#   1. calculadora:  ferramentas\iniciar-calculadora.bat              (opcional: sem ela, cálculo simplificado)
#   2. API:          mvnw spring-boot:run -Dspring-boot.run.profiles=demo   (com GEMINI_API_KEY para a IA real)
#   3. ensaio:       powershell -ExecutionPolicy Bypass -File ferramentas\ensaio-demo.ps1 [-Vezes 3]
#
# Termina com código 0 se todos os ensaios passarem. No fim, a API fica no estado inicial (reiniciada).

param(
    [string]$Api = "http://localhost:8090",
    [int]$Vezes = 3
)

$ErrorActionPreference = "Stop"
$pasta = Join-Path $PSScriptRoot "..\notas-demo-ao-vivo"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

function Chamar([string]$metodo, [string]$caminho, [string]$corpo = $null) {
    $req = @{ Method = $metodo; Uri = "$Api$caminho"; UseBasicParsing = $true; TimeoutSec = 120 }
    if ($corpo) { $req.Body = [Text.Encoding]::UTF8.GetBytes($corpo); $req.ContentType = "application/json" }
    $r = Invoke-WebRequest @req
    $texto = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
    if ($r.Headers["Content-Type"] -like "*json*") { return $texto | ConvertFrom-Json }
    return $texto
}

function Enviar([long]$cliente, [string]$arquivo) {
    $saida = curl.exe -s -w "`n%{http_code}" -F "arquivos=@$(Join-Path $pasta $arquivo)" "$Api/api/clientes/$cliente/notas"
    $linhas = $saida -split "`n"
    if ($linhas[-1] -ne "201") { throw "upload de $arquivo respondeu HTTP $($linhas[-1]): $($linhas[0..($linhas.Count-2)] -join '')" }
    return (($linhas[0..($linhas.Count - 2)] -join "") | ConvertFrom-Json).importadas[0].id
}

function Passo([string]$nome, [scriptblock]$bloco) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $r = & $bloco
    Write-Host ("  ok  {0,-58} {1,6} ms" -f $nome, $sw.ElapsedMilliseconds)
    return $r
}

function Conferir([bool]$condicao, [string]$mensagem) {
    if (-not $condicao) { throw $mensagem }
}

$st = Chamar GET "/api/demo/status"
Write-Host "Checklist: calculadora no ar=$($st.calculadoraNoAr) (modo $($st.modoCalculo)) | IA configurada=$($st.iaConfigurada) | respostas gravadas da IA=$($st.respostasGravadasIa)"
if (-not $st.calculadoraNoAr) { Write-Host "  atenção: sem a calculadora oficial, o cálculo usa o método simplificado (mesmos valores)" }
if (-not $st.iaConfigurada) { Write-Host "  atenção: sem GEMINI_API_KEY, a classificação usa as respostas gravadas da IA" }

$falhas = 0
$resultados = @()
for ($n = 1; $n -le $Vezes; $n++) {
    Write-Host "`nEnsaio $n de $Vezes"
    try {
        Passo "reiniciar (seed)" { $r = Chamar POST "/api/demo/reiniciar"; Conferir ($r.notasImportadas -eq 18) "seed com $($r.notasImportadas) notas" } | Out-Null
        Passo "tela inicial (3 clientes com indicadores)" { $c = Chamar GET "/api/clientes"; Conferir ($c.Count -eq 3) "clientes: $($c.Count)" } | Out-Null

        $novos = Passo "upload: produtos novos (nf1004)" { Enviar 1 "1-distribuidora_nf1004.xml" }
        $cl = Passo "classificar com IA + recalcular (nf1004)" { Chamar POST "/api/notas/$novos/classificar" }
        Conferir ($cl.classificados -eq 8) "nf1004: $($cl.classificados) de 8 classificados; avisos: $($cl.avisos -join ' | ')"
        Write-Host ("        origem: {0} | avisos: {1}" -f ($cl.porOrigem | ConvertTo-Json -Compress), ($(if ($cl.avisos) { $cl.avisos -join " | " } else { "nenhum" })))

        $hack = Passo "upload: nota de teste do hackathon" { Enviar 1 "1-distribuidora_nfe_teste_hackathon.xml" }
        $ch = Passo "classificar + recalcular (hackathon)" { Chamar POST "/api/notas/$hack/classificar" }
        Conferir ($ch.classificados -eq 8) "hackathon: $($ch.classificados) de 8 classificados"

        $rev = Passo "revisão do cliente" { Chamar GET "/api/clientes/1/revisao" }
        $nota = Chamar GET "/api/notas/$novos"
        $carne = $nota.itens[0].id
        Passo "aceitar a classificação da carne" { Chamar PUT "/api/itens/$carne/classificacao" '{"aceitar": true}' } | Out-Null
        $painel = Passo "painel do cliente" { Chamar GET "/api/clientes/1/dashboard" }
        Conferir ($painel.indicadores.pendentesRevisao -eq $rev.total - 1) "pendentes no painel ($($painel.indicadores.pendentesRevisao)) <> revisão - 1 ($($rev.total - 1))"
        Passo "relatório CSV" { $csv = Chamar GET "/api/clientes/1/relatorio.csv"; Conferir ($csv.Length -gt 1000) "CSV vazio" } | Out-Null

        $i = $painel.indicadores
        Write-Host ("        painel: hoje R$ {0} -> 2027 R$ {1} ({2}%) | pendentes {3} | maior impacto: {4} ({5})" -f `
            $i.liquidoHoje, $i.liquido2027, $i.variacaoPct, $i.pendentesRevisao, $painel.topItens[0].descricao, $painel.topItens[0].diferenca)
        $resultados += "$($i.liquidoHoje)|$($i.liquido2027)|$($i.pendentesRevisao)"
    } catch {
        $falhas++
        Write-Host "  FALHOU: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Chamar POST "/api/demo/reiniciar" | Out-Null
$iguais = ($resultados | Select-Object -Unique).Count -le 1
Write-Host ""
if ($falhas -eq 0 -and $iguais) {
    Write-Host "OK: $Vezes ensaios sem erro e com os mesmos números. API reiniciada para a apresentação." -ForegroundColor Green
    exit 0
}
if (-not $iguais) { Write-Host "Os ensaios deram números diferentes: $($resultados -join ' / ')" -ForegroundColor Yellow }
Write-Host "FALHAS: $falhas de $Vezes ensaios." -ForegroundColor Red
exit 1

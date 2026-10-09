# ==============================================================================
# Script de Inicialização Segura do Backend TribIA (PowerShell)
# Carrega variáveis de ambiente de .env sem expor valores e inicia o Spring Boot.
# Compatível com Windows PowerShell 5.1 e PowerShell Core 7+.
# ==============================================================================

[CmdletBinding()]
param (
    [string]$EnvFile = ".env",
    [switch]$SkipRun,
    # Roda os testes do backend em vez de subir o servidor. Sem filtro = suíte completa.
    # Ex.: -Testes "JevHttpTest,AnaliseFiscalControllerTest". Os testes nunca usam as chaves do .env.
    [switch]$Testes,
    [string]$Filtro = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# 1. Garantir JAVA_HOME (Java 21). Ordem: JAVA_HOME já definido, JDKs do usuário (%USERPROFILE%\.jdks, sem
#    precisar de administrador), instalações padrão. Nunca uma pasta temporária: ela some entre sessões.
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $CandidatosJava = @(
        "$env:USERPROFILE\.jdks\temurin-21*",
        "$env:USERPROFILE\.jdks\*21*",
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Java\jdk-21*",
        "C:\Program Files\Microsoft\jdk-21*"
    )
    foreach ($candidato in $CandidatosJava) {
        $resolvido = Resolve-Path $candidato -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($resolvido -and (Test-Path "$($resolvido.Path)\bin\java.exe")) {
            $env:JAVA_HOME = $resolvido.Path
            break
        }
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    Write-Error ("JAVA_HOME (Java 21) não encontrado. Instale o JDK 21 (ex.: winget install EclipseAdoptium.Temurin.21.JDK) " +
        "ou extraia um JDK 21 em $env:USERPROFILE\.jdks\ e rode de novo.")
    exit 1
}

# Testes: não carregam o .env (os testes forçam chaves vazias e nunca chamam IA real)
if ($Testes) {
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    Write-Host "[TribIA] Java: $env:JAVA_HOME" -ForegroundColor Cyan
    Set-Location -Path (Join-Path $ScriptDir "backend")
    $env:GEMINI_API_KEY = ""; $env:JEV_API_KEY = ""; $env:TYPESAFE_API_KEY = ""
    if ($Filtro) { & .\mvnw.cmd test "-Dtest=$Filtro" } else { & .\mvnw.cmd test }
    exit $LASTEXITCODE
}

$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Write-Host "[TribIA] Java configurado: $env:JAVA_HOME" -ForegroundColor Cyan

# 2. Carregar variáveis do .env na raiz (se existir)
$CaminhoEnv = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $ScriptDir $EnvFile }
if (Test-Path $CaminhoEnv) {
    Write-Host "[TribIA] Carregando variáveis de ambiente de: $EnvFile" -ForegroundColor Cyan
    $linhas = Get-Content -Path $CaminhoEnv -Encoding UTF8
    $contagem = 0
    foreach ($linha in $linhas) {
        $l = $linha.Trim()
        if ([string]::IsNullOrWhiteSpace($l) -or $l.StartsWith("#") -or $l.StartsWith("!")) {
            continue
        }
        $pos = $l.IndexOf("=")
        if ($pos -gt 0) {
            $chave = $l.Substring(0, $pos).Trim()
            $valor = $l.Substring($pos + 1).Trim()
            # Remove aspas se houver
            if (($valor.StartsWith('"') -and $valor.EndsWith('"')) -or ($valor.StartsWith("'") -and $valor.EndsWith("'"))) {
                $valor = $valor.Substring(1, $valor.Length - 2)
            }
            [System.Environment]::SetEnvironmentVariable($chave, $valor, [System.EnvironmentVariableTarget]::Process)
            $contagem++
        }
    }
    Write-Host "[TribIA] $contagem variáveis carregadas com sucesso no processo (nenhuma chave exposta)." -ForegroundColor Green
} else {
    Write-Host "[TribIA] Aviso: Arquivo $EnvFile não encontrado. Prosseguindo com variáveis padrão." -ForegroundColor Yellow
}

# 3. Status resumido das IAs (seguro: indica apenas presença/ausência, nunca o valor)
$geminiConfigurado = if ([string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY)) { "Não configurada" } else { "Configurada" }
$jevModo = if ([string]::IsNullOrWhiteSpace($env:TRIBIA_JEV_MODO)) { "DESLIGADO" } else { $env:TRIBIA_JEV_MODO }
$jevConfigurada = if ([string]::IsNullOrWhiteSpace($env:JEV_API_KEY)) { "Não configurada" } else { "Configurada" }

$corGemini = if ($geminiConfigurado -eq "Configurada") { "Green" } else { "Yellow" }
$corModo = if ($jevModo -eq "HTTP") { "Cyan" } elseif ($jevModo -eq "SIMULADO") { "Yellow" } else { "Gray" }
$corJevKey = if ($jevConfigurada -eq "Configurada") { "Green" } else { "Yellow" }

Write-Host "--------------------------------------------------------" -ForegroundColor DarkGray
Write-Host " Status de Integração (Auditoria Segura):" -ForegroundColor White
Write-Host "   - Gemini API Key : $geminiConfigurado" -ForegroundColor $corGemini
Write-Host "   - JEV AI Modo    : $jevModo" -ForegroundColor $corModo
Write-Host "   - JEV AI Key     : $jevConfigurada" -ForegroundColor $corJevKey
Write-Host "--------------------------------------------------------" -ForegroundColor DarkGray
if ($jevModo -eq "HTTP" -and $jevConfigurada -eq "Configurada") {
    Write-Host "[TribIA] ATENÇÃO: JEV em modo HTTP. Cada análise fiscal faz uma chamada COBRADA à TypeSafe." -ForegroundColor Yellow
}

if ($SkipRun) {
    Write-Host "[TribIA] Inicialização do servidor pulada (--SkipRun)." -ForegroundColor Cyan
    return
}

# 4. Iniciar backend com Maven Wrapper
$BackendDir = Join-Path $ScriptDir "backend"
Write-Host "[TribIA] Iniciando Spring Boot em http://localhost:8090 ..." -ForegroundColor Cyan
Set-Location -Path $BackendDir
& .\mvnw.cmd spring-boot:run

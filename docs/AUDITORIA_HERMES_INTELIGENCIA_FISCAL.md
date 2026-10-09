# Guia de Auditoria para o Hermes — Inteligência Fiscal

Este documento foi preparado para que o agente **Hermes** possa inspecionar e auditar o funcionamento da **Inteligência Fiscal (Gemini + JEV AI)** nesta máquina local de forma autônoma, segura e sem exigir a leitura ou exposição de chaves privadas.


> **Atualização 08/10/2026 (noite):** a auditoria anterior não conseguiu rodar o Maven porque o JDK estava numa pasta
> temporária. Agora: `iniciar-backend.ps1 -Testes` (JDK em `%USERPROFILE%\.jdks\temurin-21.0.12.1`). Resposta item a item:
> `docs/contexto-projeto/RESPOSTA-AUDITORIA-HERMES-2026-10-08.md`.

---

## 1. Inicialização dos Serviços

### A. Backend (Spring Boot — Porta 8090)
Execute na raiz do repositório via PowerShell:
```powershell
powershell -ExecutionPolicy Bypass -File .\iniciar-backend.ps1
```
* O script detecta automaticamente o JDK 21 LTS na máquina.
* Carrega as variáveis do `.env` no processo.
* Exibe uma auditoria visual segura (mostra apenas se as chaves estão "Configurada" ou "Não configurada", sem imprimir valores).
* Inicia o servidor Tomcat em `http://localhost:8090`.

*Para apenas auditar as variáveis sem subir o servidor:*
```powershell
powershell -ExecutionPolicy Bypass -File .\iniciar-backend.ps1 -SkipRun
```

### B. Frontend (React / Vite — Porta 5173)
Em outro terminal:
```powershell
cd frontend
npm run dev
```
O frontend escuta em `http://localhost:5173` e encaminha chamadas `/api/*` transparentemente para o backend na porta 8090.

---

## 2. Auditoria Segura de Configuração (Sem Expor Chaves)

### A. Via API Administrativa (Recomendado para Hermes)
1. Obtenha o token CSRF:
   `GET http://localhost:8090/api/auth/csrf` (extraia o cookie `XSRF-TOKEN`).
2. Faça login como administrador:
   `POST http://localhost:8090/api/auth/login`
   Headers: `X-XSRF-TOKEN: <token>`, `Content-Type: application/json`
   Corpo: `{"email": "admin@tribia.local", "senha": "<senha do administrador local, definida no .env>"}`
3. Consulte o estado da JEV AI (sem chamadas externas e sem custo):
   `GET http://localhost:8090/api/admin/jev/status`
   **Exemplo de resposta segura:**
   ```json
   {
     "modo": "DESLIGADO",
     "url": "https://api.typesafe.ai",
     "modelo": "jev-1.13.0",
     "chaveConfigurada": false,
     "ativaNasAnalises": false,
     "observacao": "JEV AI desligada: nenhuma chamada é feita."
   }
   ```
   * Quando a chave estiver no `.env`, `"chaveConfigurada"` será `true`.
   * Quando `TRIBIA_JEV_MODO=HTTP` for definido, `"ativaNasAnalises"` será `true`.

### B. Via Interface Web
1. Acesse `http://localhost:5173` e autentique com `admin@tribia.local` e a senha do administrador local (definida no `.env`, nunca registrada em documento).
2. Acesse a rota `/dashboard/configuracoes`.
3. Inspecione o card **JEV AI**: ele informa a situação ("Desligada" / "Ativa" / "Simulada") e se a chave no servidor está "Configurada".

---

## 3. Execução de Testes Simulados e Gratuitos

Estes testes utilizam dublês e mocks em memória; **nunca fazem chamadas reais e não consomem cotas**:

### Suíte de Testes da JEV AI e Inteligência Fiscal
Na **raiz** do repositório (o script encontra o JDK 21 em `%USERPROFILE%\.jdks\`, não carrega o `.env` e força as
chaves vazias nos testes):
```powershell
# suíte completa do backend
powershell -ExecutionPolicy Bypass -File .\iniciar-backend.ps1 -Testes
# só a Inteligência Fiscal, a JEV e o Gemini
powershell -ExecutionPolicy Bypass -File .\iniciar-backend.ps1 -Testes -Filtro "JevHttpTest,JevConfigTest,TesteConexaoJevTest,JevControllerTest,ValidadorNcmJevTest,PesquisaNcmIaSegurancaTest,AnaliseFiscalControllerTest*,GeminiClientTest,LeitorAnexosTest,TabelaNcmVigenteTest,VerificacaoTabelaNcmTest"
```
> Não use um `JAVA_HOME` apontando para pasta temporária (`AppData\Local\Temp\...`): ela some entre sessões. Esse
> foi o motivo da falha do Maven na auditoria anterior. JDK estável: `%USERPROFILE%\.jdks\temurin-21.0.12.1`.

* `JevHttpTest` (14 testes): valida cliente HTTP contra respostas simuladas da TypeSafe, retries em 429/529, timeouts, tratamento de erros e neutralização do texto enviado.
* `JevControllerTest` (4 testes): proteção de rotas (somente ADMIN, CSRF obrigatório) e situação da tabela NCM.
* `TesteConexaoJevTest` (6 testes): verificação sintética e bloqueios de custo.
* `AnaliseFiscalControllerTest` (24 testes): endpoints de análise fiscal da empresa.

---

## 4. Como Executar um Teste Real Autorizado (Cobrado por Token)

> **Atenção:** Só execute este passo quando houver autorização expressa do responsável, pois consome tokens na API da TypeSafe e do Gemini.

### Opção A — Pelo Painel do Administrador (Frontend)
1. Com `JEV_API_KEY` informada no `.env` e o backend iniciado:
2. Acesse `Configurações` -> Card **JEV AI**.
3. Clique em **Testar conexão**. O sistema exibirá um diálogo de confirmação de custo.
4. Ao confirmar, o backend chama a TypeSafe com mercadoria sintética (sabonete vs celular) e retorna modelos disponíveis, tokens consumidos e tempo de resposta.

### Opção B — Por Linha de Comando (JUnit Contrato)
```powershell
.\mvnw.cmd test "-Dtest=JevContratoTest" "-Djev.contrato=true"
```

### Para ativar a JEV nas análises em segundo plano:
Altere no `.env` da raiz:
```properties
TRIBIA_JEV_MODO=HTTP
```
Reinicie o backend. O card passará para **Ativa**.

---

## 5. Inspeção Visual e Auditoria no Frontend

1. Com backend e frontend rodando, faça login com o usuário administrador ou de empresa.
2. Acesse uma empresa (ex.: `/dashboard/empresas/1`).
3. Vá para a aba **Inteligência Fiscal** (`/dashboard/empresas/1/inteligencia-fiscal`).
4. **Verificações a auditar:**
   - Formulário de submissão de mercadoria com descrição e anexos (PDF, DOCX, XLSX).
   - Fila de análises e indicadores (Total, Concluídas, Em Revisão).
   - Detalhe da análise: lista de NCMs candidatas sugeridas pelo Gemini com justificativas e grau de confiança.
   - Verificação "Avaliação da JEV AI" na validação e notas na tabela "Classificações avaliadas" (quando ativa:
     valor na escala 0 a 1; divergência com o Gemini manda para revisão). Quando desligada, a análise registra nas
     limitações que a pontuação não está disponível.
   - Card **Revisão humana** (aceitar ou trocar a NCM, com justificativa) e card **Tabela NCM** em Configurações.
   - Exportação de relatório em PDF.

---

## 6. Política de Segurança dos Logs

O Hermes pode auditar os logs da aplicação com tranquilidade:
* **Sanitização de credenciais:** As classes `LlmProperties` e `JevProperties` implementam métodos `toString()` que mascaram as chaves como `***` ou `(vazia)`.
* **Sem vazamento de dados de negócio na JEV:** A classe `JevHttp` apenas registra códigos de status HTTP e tentativas. Textos completos de mercadorias ou notas não são impressos no log.
* **Console Spring Boot:** Em caso de credencial inválida, o log reporta `A API recusou a chave (HTTP 401/403)` sem imprimir o valor configurado.

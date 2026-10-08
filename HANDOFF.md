# HANDOFF — TribIA: Contexto Completo do Projeto

> **Gerado em:** 08/10/2026  
> **Finalidade:** Passagem de contexto para outra IA/sessão continuar o desenvolvimento.  
> Leia este arquivo antes de qualquer outra coisa.

---

## 1. Visão Geral do Produto

**TribIA** é uma aplicação web para escritórios de contabilidade e empresas gerenciarem o impacto da **Reforma Tributária brasileira (LC 214/2024)** em seus produtos.

**Fluxo principal:**
1. O usuário faz upload de XMLs de Notas Fiscais Eletrônicas (NF-e)
2. A IA (Google Gemini) classifica cada item nas regras tributárias de 2027 (CST + cClassTrib)
3. O sistema calcula o imposto atual (PIS/Cofins) e o de 2027 (CBS/IBS/IS)
4. O painel exibe a comparação e o relatório pode ser exportado como CSV

**Repositório:** `https://github.com/leozinlsb/trib-ai`  
**Branch principal:** `main`

---

## 2. Stack Tecnológica

### Backend (`/backend`)
| Componente | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3 |
| Segurança | Spring Security (sessão em cookie HttpOnly + CSRF) |
| Banco de dados | H2 (arquivo em `backend/data/tribia`) |
| IA | Google Gemini API (`gemini-3.5-flash`) |
| Build | Maven Wrapper (`mvnw.cmd`) |
| API Docs | Springdoc / Swagger UI |

### Frontend (`/frontend`)
| Componente | Tecnologia |
|---|---|
| Framework | React 19 + TypeScript |
| Build tool | Vite |
| Estilos | CSS puro (sem Tailwind) — tokens em `src/styles/` |
| Comunicação | `fetch` nativo com suporte a CSRF (header `X-XSRF-TOKEN`) |

---

## 3. Histórico Relevante desta Sessão de Desenvolvimento

### O que foi feito (em ordem cronológica):

1. **O backend já estava desenvolvido (`main`)** com as etapas 1 a 8:
   - Upload e parsing de XMLs de NF-e
   - Classificação por IA (Gemini)
   - Cálculo PIS/Cofins atual vs CBS/IBS/IS 2027
   - Revisão manual de classificações
   - Painel de apuração + exportação CSV
   - Profile `demo` com respostas gravadas da IA

2. **O frontend chegou via Pull Request** (branch `dev/prataliyann-hue`) e foi **mergeado no `main`** nesta sessão.

3. **Conflitos de merge resolvidos manualmente** nos arquivos:
   - `Cliente.java` — manteve lógica local + adicionou campo `ativo` do PR
   - `ClienteDto.java` — idem
   - `ClienteController.java` — manteve lógica local + adicionou endpoints de usuários/acessos do PR
   - `NotaController.java` — idem
   - `RelatorioController.java` — manteve lógica local (o PR tinha versão mais simples)
   - `application.properties` — manteve configuração local + adicionou propriedades de segurança do PR

4. **Spring Security foi adicionado pelo PR** à dependência do projeto. O backend agora exige autenticação em todas as rotas `/api/**`.

5. **Testes de compilação corrigidos** após o merge.

6. **Commit do merge** realizado e push para `origin/main` feito com sucesso.

---

## 4. Estado Atual (08/10/2026)

### Funcionando
- Backend compilando e iniciando sem erros
- Frontend com dependências instaladas (`npm install` executado)
- Integração: Vite (`/api`) repassa chamadas para `localhost:8090`
- Spring Security ativo: login obrigatório em `/api/**`
- H2 Database em arquivo: dados persistem entre reinícios do backend

### PROBLEMA CRÍTICO: Testes do Backend com 401/403

**~40 testes do backend falham** com status `401 Unauthorized` ou `403 Forbidden`.

**Causa:** O PR introduziu Spring Security, mas os testes existentes no `main` não têm autenticação mockada. Os testes chamam endpoints protegidos sem estar autenticados.

**Solução necessária:** Adicionar `@WithMockUser` ou configurar `SecurityMockMvcRequestPostProcessors` nas classes de teste relevantes:

```java
// Opção 1: anotar a classe ou método
@WithMockUser(username = "admin@tribia.local", roles = {"ADMIN"})

// Opção 2: em MockMvc
mockMvc.perform(get("/api/clientes")
    .with(user("admin@tribia.local").roles("ADMIN")))
```

**Classes de teste possivelmente afetadas:**
- `ClienteControllerTest.java`
- `NotaControllerTest.java`
- `RelatorioControllerTest.java`
- `RevisaoControllerTest.java`
- `DashboardControllerTest.java`

### Funcionalidade Parcial: Inteligência Fiscal

O frontend tem telas de **Inteligência Fiscal** completas, mas **os endpoints do backend não existem ainda**. As telas mostram "Análise fiscal ainda não disponível". O contrato proposto está em:
- `frontend/docs/inteligencia-fiscal-api.md`
- `frontend/src/api/inteligenciaFiscal.ts`

---

## 5. Como Inicializar o Ambiente

### Backend (novo terminal PowerShell)
```powershell
cd C:\Users\Leoba\Desktop\trib-ai\backend
$env:TRIBIA_ADMIN_SENHA="admin123"
$env:GEMINI_API_KEY="sua-chave-aqui"
.\mvnw.cmd spring-boot:run
```

### Frontend (novo terminal PowerShell)
```powershell
cd C:\Users\Leoba\Desktop\trib-ai\frontend
npm run dev
```

### URLs de Acesso
- Frontend: http://localhost:5173
- Backend API: http://localhost:8090
- Swagger: http://localhost:8090/swagger-ui.html
- H2 Console: http://localhost:8090/h2-console (JDBC: `jdbc:h2:file:./data/tribia`, user: `sa`, senha: vazia)

### Login Inicial
- **Email:** `admin@tribia.local`
- **Senha:** valor de `$env:TRIBIA_ADMIN_SENHA` (no exemplo: `admin123`)

---

## 6. Próximas Tarefas (em ordem de prioridade)

1. **[ALTA] Corrigir os ~40 testes do backend** — Adicionar `@WithMockUser` em todas as classes de teste de controllers. A dependência `spring-security-test` já está no `pom.xml`.
2. **[MÉDIA] Implementar endpoints de Inteligência Fiscal** — Frontend pronto aguardando. Contrato em `frontend/docs/inteligencia-fiscal-api.md`.
3. **[MÉDIA] Testar fluxo completo** — Login → criar empresa → upload XML → classificação → painel → CSV.
4. **[BAIXA] Revogar chave Gemini exposta** — Ver `PENDENCIAS.md` item O1.
5. **[BAIXA]** Criar `backend/application-local.properties` com senha fixa para não repetir a variável de ambiente.

---

## 7. Notas de Domínio

- **CST** = Código de Situação Tributária (tipo de tributação do item)
- **cClassTrib** = Código de Classificação Tributária (regime do item em 2027)
- **CBS** = Contribuição sobre Bens e Serviços (substitui PIS/Cofins em 2027)
- **IBS** = Imposto sobre Bens e Serviços (substitui ICMS/ISS em 2027)
- **IS** = Imposto Seletivo (tributação extra sobre produtos prejudiciais)
- **Seed** = dados de demonstração carregados na inicialização (3 empresas com notas reais)
- **Profile `demo`** = ativa respostas gravadas da IA para apresentações offline

---

*Fim do HANDOFF. Em caso de dúvida sobre decisões técnicas, consulte o `git log` e o `PENDENCIAS.md`.*

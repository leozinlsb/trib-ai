# TribIA

A empresa sobe os XMLs das notas fiscais, a IA classifica cada item nas regras da reforma tributária
(CST + cClassTrib) e o sistema compara o imposto líquido de hoje (PIS/Cofins) com o de 2027 (CBS/IBS/IS).

Comparativos são estimativas, não apuração fiscal definitiva. Classificação/cálculo existem
no backend; integração visual ainda parcial (B5). Consulte [plano mestre](PLANO_MESTRE_TRIBIA.md)
e [validação da Etapa 1](docs/contexto-projeto/CONCLUSAO-ETAPA-1-2026-10-08.md) para evidências,
ressalvas fiscais, revogação da chave antiga e revisão histórica antes de uso real.

## Frontend

React 19 + TypeScript + Vite, em [`frontend/`](frontend/). Com o backend rodando:

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173 (o Vite repassa /api para http://localhost:8090)
npm test         # testes do motor de alertas (Node 22.6+)
# E2E no navegador (backend isolado + Playwright fora do repo): ver o cabeçalho de scripts/etapa4-e2e.mjs
```

Detalhes, endpoints usados e limitações em [`frontend/README.md`](frontend/README.md).

## Backend

Java 21 + Spring Boot 3, em [`backend/`](backend/). Não precisa instalar o Maven: use o wrapper.

```bash
cd backend
./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
./mvnw test
```

- API: http://localhost:8090 (a calculadora offline da Receita ocupa 8080, 8081, 8082 e 80)
- Swagger: http://localhost:8090/swagger-ui.html
- Console H2: http://localhost:8090/h2-console, somente ADMIN quando habilitado
  (JDBC padrão `jdbc:h2:file:./data/tribia`, usuário `sa`). Desabilite console/demo em produção.

### Acesso (login e empresas)

Rotas de negócio `/api` exigem login (cookie HttpOnly + CSRF); login e bootstrap CSRF são
públicos. Há dois perfis:

- **Administrador** (escritório): gerencia as empresas e acessa todas. É criado na inicialização como
  `admin@tribia.local`. Defina a senha pela variável de ambiente `TRIBIA_ADMIN_SENHA` antes de subir o backend;
  sem ela, uma senha aleatória é gerada e aparece **uma vez** no log.
- **Usuário de empresa**: vê somente a própria empresa. Os acessos são criados pelo administrador em
  *Empresas → (empresa) → Configurações → Acessos da empresa*. Não há cadastro público.

```bash
# Windows (PowerShell)
$env:TRIBIA_ADMIN_SENHA="uma-senha-forte"; .\mvnw.cmd spring-boot:run
```

Ou, para não repetir a cada execução, crie `backend/application-local.properties` (fora do Git, já está no
`.gitignore`) com `tribia.admin.senha=...` (e, se quiser, `tribia.admin.email=...`). O backend lê esse arquivo
ao iniciar pela pasta `backend/`.

O isolamento entre empresas é feito no servidor (`AcessoService`): a empresa do usuário vem da sessão, pedir
outra empresa devolve 404 e operações administrativas devolvem 403. "Remover" uma empresa é desativá-la
(exclusão lógica): notas e acessos são mantidos e ela pode ser reativada.

> O banco padrão é H2 **em arquivo**, em `backend/data/tribia` quando iniciado pela pasta backend.
> Empresas, usuários e notas persistem entre reinícios; mudar a senha de inicialização não
> altera um ADMIN existente. Não apague o banco para testar: use ambiente isolado em memória.
> Guardas/cache privado passaram nos caminhos avaliados; histórico do cache antigo não foi auditado em dados reais.

### Calculadora oficial (Calculadora RTC da Receita)

O TribIA chama a calculadora offline em `http://localhost:8080/api` (`tribia.calculadora.url`). Usamos a
distribuição oficial **jar** da Receita (`calculadora-jar.zip`), que roda direto no Java, sem WSL nem Docker:

```bash
# 1. baixa ou atualiza (só baixa se a Receita publicou versão nova; pare a calculadora antes)
python -I ferramentas/atualizar_calculadora.py          # pasta padrão: %USERPROFILE%\Desktop\calculadora-rtc

# 2. a cada uso (deixe a janela aberta; sobe em ~10 s)
ferramentas\iniciar-calculadora.bat
```

O script consulta as APIs públicas `dados-abertos/versao` e `download/url?platform=jar` do portal
piloto-cbs.tributos.gov.br e guarda a versão instalada em `versao.json`.

Modo de cálculo (`tribia.calculo.modo`): `AUTO` (padrão: oficial e, se ela falhar, o cálculo simplificado com
aviso), `OFICIAL` ou `SIMPLIFICADA`. O simplificado é uma estimativa; três contratos reais
RTC offline passaram em 08/10/2026, inclusive SimplificadaVsOficialContratoTest. Isso não
certifica todas as regras tributárias ou a base enviada à calculadora.

Observações:
- A partir de 2027 a calculadora exige as alíquotas nominais; o TribIA envia as de `tribia.aliquotas.ano2027.*`,
  por isso o resultado vem marcado como **simulado**.
- Se a calculadora não conhecer um NCM (ex.: `34022000`, extinto em 2022), o item é recalculado sem NCM e
  o resultado traz um aviso.
- O teste `CalculadoraOficialContratoTest` roda contra a calculadora real quando ela está no ar; senão, é pulado.

### IA (Gemini)

A classificação dos itens que o XML e o cache não resolvem é feita pelo Gemini (`tribia.llm.*`). A chave **nunca**
fica em arquivo versionado: coloque-a no `.env` da raiz do repositório (ignorado pelo Git), que o backend lê ao
subir, ou numa variável de ambiente (que tem prioridade sobre o `.env`).

```
# .env (raiz do repositório)
GEMINI_API_KEY=sua-chave
```

- Modelos em ordem de preferência (`tribia.llm.modelos`): se o primeiro estiver sobrecarregado ou sem cota,
  o segundo é tentado sozinho. O `gemini-2.5-*` não está mais disponível para chaves novas.
- A IA só escolhe entre as opções da tabela oficial de cClassTrib. Benefícios de anexo (ex.: cesta básica) só são
  aceitos se o NCM constar da lista oficial do código; senão a resposta é recusada e reenviada uma vez.
- Sem chave/IA fora do ar, classificar responde 200: XML/cache preservados; regra só sugere
  associação única permitida (REGRA, confiança 0,40, não aceita). Sem evidência/ambíguo fica
  pendente, fora do cálculo. Não presume integral. IA/revisão usam cache privado por empresa;
  compartilhado somente catálogo SEED. Legado sem dono IA/MANUAL não reutilizado.
- Os testes nunca usam a chave do `.env`. `GeminiContratoTest` usa a IA de verdade (gasta cota) e só roda com
  `GEMINI_API_KEY` definida como variável de ambiente.

### Hospedagem (profile `prod`)

O `backend/Dockerfile` sobe a API com `SPRING_PROFILES_ACTIVE=prod`: sem console H2 nem Swagger, rotas
`/api/demo` desligadas (ligue com `TRIBIA_DEMO_HABILITADO=true` só num ambiente de demonstração), cookie de sessão
`Secure` e login bloqueado por 15 min após 5 falhas no mesmo e-mail. Variáveis: `TRIBIA_ADMIN_SENHA` (obrigatória,
12+ caracteres: sem ela a API não sobe) e `GEMINI_API_KEY` (opcional). O front na Vercel repassa `/api` ao backend
(`frontend/vercel.json`), então tudo fica na mesma origem e não é preciso configurar CORS.

### Apresentação (profile `demo`)

Roteiro completo para quem apresenta: [`docs/ROTEIRO_DEMO.md`](docs/ROTEIRO_DEMO.md).

```powershell
ferramentas\iniciar-calculadora.bat                                  # janela 1 (opcional)
$env:TRIBIA_ADMIN_SENHA="..."; mvnw spring-boot:run "-Dspring-boot.run.profiles=demo"   # janela 2
powershell -ExecutionPolicy Bypass -File ferramentas\ensaio-demo.ps1 -Vezes 3      # janela 3 (mesma senha)
```

O ensaio entra como ADMIN (sessão + CSRF) e roda o roteiro pela API: reiniciar → upload → classificar → revisão →
painel → CSV. As rotas demo exigem ADMIN e habilitação explícita; reiniciar apaga uploads/revisões/cache.
Nunca execute esse reset ou o ensaio contra dados reais: para ensaiar sem tocar `data/`, suba a API com
`--spring.datasource.url=jdbc:h2:mem:ensaio`.

- `GET /api/demo/status`: checklist (calculadora no ar, IA configurada, respostas gravadas, volume de dados).
- `POST /api/demo/reiniciar`: volta ao estado inicial (só o seed) entre ensaios.
- Plano B da IA: se ela falhar, usa as respostas que o Gemini real deu antes para os produtos de
  `notas-demo-ao-vivo/` (`src/main/resources/demo/respostas-ia.json`), com aviso na resposta.
  Para regravar: `$env:GEMINI_API_KEY="..."; mvnw test -Dtest=GerarRespostasIaDemoTest -Dseed.gerar=true`.
- Plano B do cálculo: AUTO usa simplificado com aviso/flag de simulação; não prometer equivalência universal.
- Roteiro sugerido: tela inicial → upload de `1-distribuidora_nf1004.xml` (8 produtos novos: a IA classifica
  em ~12 s) → upload da nota do hackathon (7 do cache, 1 pela IA) → revisão → painel → CSV.

### Dados de demonstração

Na inicialização, o backend importa as notas de `src/main/resources/seed/{cnpj}/` (3 saídas e 3 entradas
por cliente, de agosto a outubro de 2026). Para desligar: `tribia.seed.enabled=false`.

As notas para o **upload ao vivo** ficam fora do seed, em [`backend/notas-demo-ao-vivo/`](backend/notas-demo-ao-vivo/)
(o número no início do nome é o id do cliente).

Os XMLs são gerados a partir de `src/test/java/br/com/tribia/seed/CatalogoSeed.java`. Depois de editar o catálogo:

```bash
./mvnw test -Dtest=GerarArquivosSeedTest -Dseed.gerar=true
```

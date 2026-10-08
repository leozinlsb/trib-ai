# TribIA

A empresa sobe os XMLs das notas fiscais, a IA classifica cada item nas regras da reforma tributária
(CST + cClassTrib) e o sistema compara o imposto líquido de hoje (PIS/Cofins) com o de 2027 (CBS/IBS/IS).

## Frontend

React 19 + TypeScript + Vite, em [`frontend/`](frontend/). Com o backend rodando:

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173 (o Vite repassa /api para http://localhost:8090)
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
- Console H2: http://localhost:8090/h2-console (JDBC URL `jdbc:h2:mem:tribia`, usuário `sa`)

### Acesso (login e empresas)

Toda rota `/api` exige login (sessão em cookie HttpOnly + proteção CSRF). Há dois perfis:

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

> O banco é H2 **em memória**: empresas, usuários e notas enviadas somem quando o backend reinicia
> (os 3 clientes e as notas de demonstração são recriados).

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

Observações:
- A partir de 2027 a calculadora exige as alíquotas nominais; o TribIA envia as de `tribia.aliquotas.ano2027.*`,
  por isso o resultado vem marcado como **simulado**.
- Se a calculadora não conhecer um NCM (ex.: `34022000`, extinto em 2022), o item é recalculado sem NCM e
  o resultado traz um aviso.
- O teste `CalculadoraOficialContratoTest` roda contra a calculadora real quando ela está no ar; senão, é pulado.

### Dados de demonstração

Na inicialização, o backend importa as notas de `src/main/resources/seed/{cnpj}/` (3 saídas e 3 entradas
por cliente, de agosto a outubro de 2026). Para desligar: `tribia.seed.enabled=false`.

As notas para o **upload ao vivo** ficam fora do seed, em [`backend/notas-demo-ao-vivo/`](backend/notas-demo-ao-vivo/)
(o número no início do nome é o id do cliente).

Os XMLs são gerados a partir de `src/test/java/br/com/tribia/seed/CatalogoSeed.java`. Depois de editar o catálogo:

```bash
./mvnw test -Dtest=GerarArquivosSeedTest -Dseed.gerar=true
```

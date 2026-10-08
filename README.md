# TribIA

A empresa sobe os XMLs das notas fiscais, a IA classifica cada item nas regras da reforma tributária
(CST + cClassTrib) e o sistema compara o imposto líquido de hoje (PIS/Cofins) com o de 2027 (CBS/IBS/IS).

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
aviso), `OFICIAL` ou `SIMPLIFICADA`. O simplificado usa as mesmas fórmulas e tabelas oficiais e é conferido contra
a calculadora real pelo `SimplificadaVsOficialContratoTest`.

Observações:
- A partir de 2027 a calculadora exige as alíquotas nominais; o TribIA envia as de `tribia.aliquotas.ano2027.*`,
  por isso o resultado vem marcado como **simulado**.
- Se a calculadora não conhecer um NCM (ex.: `34022000`, extinto em 2022), o item é recalculado sem NCM e
  o resultado traz um aviso.
- O teste `CalculadoraOficialContratoTest` roda contra a calculadora real quando ela está no ar; senão, é pulado.

### IA (Gemini)

A classificação dos itens que o XML e o cache não resolvem é feita pelo Gemini (`tribia.llm.*`). A chave **nunca**
fica em arquivo do projeto: defina a variável de ambiente antes de subir a API.

```powershell
setx GEMINI_API_KEY "sua-chave"      # uma vez; abra um novo terminal (e reinicie o VS Code) depois
```

- Modelos em ordem de preferência (`tribia.llm.modelos`): se o primeiro estiver sobrecarregado ou sem cota,
  o segundo é tentado sozinho. O `gemini-2.5-*` não está mais disponível para chaves novas.
- A IA só escolhe entre as opções da tabela oficial de cClassTrib. Benefícios de anexo (ex.: cesta básica) só são
  aceitos se o NCM constar da lista oficial do código; senão a resposta é recusada e reenviada uma vez.
- Sem chave ou com a IA fora do ar, `POST /api/notas/{id}/classificar` responde 200 com os itens pendentes e um aviso.
- `GeminiContratoTest` usa a IA de verdade (gasta cota) e só roda com `GEMINI_API_KEY` definida.

### Dados de demonstração

Na inicialização, o backend importa as notas de `src/main/resources/seed/{cnpj}/` (3 saídas e 3 entradas
por cliente, de agosto a outubro de 2026). Para desligar: `tribia.seed.enabled=false`.

As notas para o **upload ao vivo** ficam fora do seed, em [`backend/notas-demo-ao-vivo/`](backend/notas-demo-ao-vivo/)
(o número no início do nome é o id do cliente).

Os XMLs são gerados a partir de `src/test/java/br/com/tribia/seed/CatalogoSeed.java`. Depois de editar o catálogo:

```bash
./mvnw test -Dtest=GerarArquivosSeedTest -Dseed.gerar=true
```

# Roteiro da demonstração — TribIA

Para quem apresenta. Números conferidos em 08/10/2026 (ensaio com a calculadora oficial e a IA real), com a base
de 2027 sem ICMS/PIS/Cofins (decisão S5). Dados 100% fictícios.

## Antes de subir ao palco (10 min)

1. **Calculadora oficial:** `backend\ferramentas\iniciar-calculadora.bat` (janela 1). Sem ela o sistema usa o
   cálculo simplificado, que dá os mesmos números.
2. **API em modo demo** (janela 2), com a chave do Gemini no `.env` da raiz:
   ```powershell
   cd backend
   $env:TRIBIA_ADMIN_SENHA = "<senha da apresentação>"
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1"
   ```
   O banco em memória não toca `backend/data/`: cada subida começa limpa.
3. **Front** (janela 3): `cd frontend; npm run dev` → http://localhost:5173.
   Na versão hospedada (Render) não há janela 3: abra a URL pública (e acorde a API uns 5 minutos antes).
4. **Ensaio** (janela 4, mesma senha): `powershell -ExecutionPolicy Bypass -File backend\ferramentas\ensaio-demo.ps1 -Vezes 1`.
   Tem que terminar em "OK". Ele deixa os dados no estado inicial.
5. Deixe aberto o Explorer em `backend\notas-demo-ao-vivo\` para arrastar os XMLs.

## Roteiro (5–7 min)

| # | Tela | O que mostrar | O que dizer |
|---|------|---------------|-------------|
| 1 | Login → Visão geral | 3 empresas, 18 notas, 100% classificadas | "Um escritório com três clientes de perfis diferentes." |
| 2 | Distribuidora → Início | Hoje **R$ 33,22** → 2027 **R$ 118,08** (+255%) | "Paga mais: o refrigerante deixa de ser monofásico na revenda e o óleo de soja sai da alíquota zero para a redução de 60%." |
| 3 | Farmácia → Início | **R$ 374,66** → **R$ 252,40** (−32,6%) | "No Presumido ela não tem crédito hoje; em 2027 as compras passam a gerar crédito." |
| 4 | Casa Limpa (Loja) → Início | **R$ 552,57** → **R$ 396,55** (−28,2%) | "Crédito amplo e base sem ICMS: carga menor." |
| 5 | Distribuidora → Documentos → Enviar notas | Arrastar `1-distribuidora_nf1004.xml` | "Nota com 8 produtos que o sistema nunca viu." |
| 6 | (espera ~12 s) | Toast de classificação | "A IA escolhe só entre códigos da tabela oficial; benefício de anexo só se o NCM estiver na lista." |
| 7 | Detalhe da nota 1004 | Carne/queijo/farinha cesta básica (zero), banana hortifrúti, pão redução 60%, azeite integral, cerveja com IS | "O azeite não ganhou benefício de insumo agropecuário: isso depende de quem compra." |
| 8 | Revisão | Itens da IA aguardando aceite → Aceitar a carne | "Nada vira verdade fiscal sem um humano aceitar." |
| 9 | Alertas | Notas sem grupo IBS/CBS, itens pendentes | "Conferimos o código de cada nota contra a tabela oficial." |
| 10 | Análises e Relatórios | Relatório da empresa + CSV | "Sai pronto para o contador." |

Depois do upload, a Distribuidora fica em **R$ 264,58 → R$ 378,44** (+43%); o item de maior impacto é o
refrigerante (R$ 112,42).

## Se algo falhar

- **IA lenta ou fora do ar:** o profile demo usa as respostas que o Gemini real deu antes para essas mesmas
  notas (aviso "Modo demonstração" no resultado). Siga o roteiro normalmente.
- **Calculadora fora do ar:** o cálculo cai no simplificado com aviso; os números são os mesmos.
- **Estado bagunçado:** rode o ensaio de novo (passo 4) ou reinicie a API: o banco em memória recomeça do seed.
- **Upload recusado (409):** a nota já foi enviada; reinicie (passo anterior).

## Ressalvas que devem ser ditas

- É simulação: a alíquota da CBS de 2027 (9,43%) é estimativa; não é apuração definitiva.
- O comparativo é PIS/Cofins hoje × CBS/IBS/IS em 2027; o ICMS não muda em 2027 e fica fora.
- Base de 2027 sem ICMS/PIS/Cofins (LC 214, art. 12, § 2º) e crédito de compra divergente pelo menor valor (R2):
  decisões registradas, a confirmar com especialista.
- Classificações automáticas são sugestões; a revisão humana é parte do produto.

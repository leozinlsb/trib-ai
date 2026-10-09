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

## Inteligência Fiscal: da mercadoria ao PDF (4–5 min)

Precisa do Gemini (`GEMINI_API_KEY` no `.env`). A JEV só pontua se `JEV_API_KEY` + `TRIBIA_JEV_MODO=HTTP` estiverem
configurados (ver `docs/JEV-AI-INTEGRACAO.md`); sem ela, a análise funciona e diz que a pontuação não está disponível.

| # | Tela | O que fazer | O que dizer |
|---|------|-------------|-------------|
| 11 | Configurações (admin) → card JEV AI | Mostrar "Ativa" e "Chave no servidor: Configurada" (a chave nunca aparece) | "A chave fica só no servidor." |
| 12 | Distribuidora → Início | Card "Inteligência Fiscal" com os números reais | "Acompanhamento de todas as análises da empresa." |
| 13 | Inteligência Fiscal → Nova análise | Nome "Sabonete de glicerina 90 g", descrição de uso, NCM atual 3401.11.90; anexar uma ficha técnica em PDF ou .txt | "Documentos técnicos entram como dados, nunca como instruções para a IA." |
| 14 | Acompanhamento | Etapas: interpretando → pesquisando NCM → avaliando (JEV) → validando | "Processamento em segundo plano; sobrevive a reinício do servidor." |
| 15 | Resultado | NCM 3401.11.90 com o **texto oficial** da NCM vigente (Siscomex, Res. Gecex 926/2026); verificações; tabela "Classificações avaliadas" com a nota da JEV ("0,93 (escala 0 a 1)") | "A nota da JEV mede compatibilidade do texto, não é chance de acerto. Se a JEV discordar da IA, a análise vai para revisão." |
| 16 | Revisão humana (no resultado) | Aceitar a sugestão ou escolher outra NCM com justificativa | "Nada vira classificação sem uma pessoa; fica registrado quem e quando." |
| 17 | Baixar relatório | PDF com aviso, verificações, notas da JEV, revisão e fontes com versão | "Pronto para o contador conferir." |

Plano B: sem Gemini, a análise termina com "A IA não está configurada" (não inventa resultado). Mostre então uma análise
feita antes do evento.

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
- Os valores de 2027 são **projeção pendente de validação fiscal**: além do ICMS, a base exclui o PIS/Cofins da nota de
  2026 (hipótese S5, a confirmar com especialista). A tela e o PDF avisam.
- Inteligência Fiscal: a NCM sugerida é apoio; a pontuação da JEV mede compatibilidade do texto, não acerto fiscal;
  "validado pelas verificações disponíveis" não é aprovação da Receita Federal. Imagens anexadas não são lidas (sem OCR).

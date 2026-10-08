# FLUXO_FISCAL.md

Descrição da jornada ideal de classificação fiscal no TribIA, com fundamentos e referências.

## 1. Fundamentos legais

A classificação fiscal de mercadorias no Brasil segue o Sistema Harmonizado (SH) e a Nomenclatura Comum do Mercosul (NCM), conforme:

- **Lei nº 12.973/2014** – aprova a Tabela de Incidência do Imposto sobre Produtos Industrializados (IPI) e estabelece a NCM como base para tributação federal.
- **Decreto nº 11.029/2022** – aprova a Tabela de Incidência dos tributos federais sobre produtos industrializados (IPI, II, IOF) e a NCM/SH.
- **Resolução Camex nº 20/2021** – dispõe sobre a NCM/SH e suas atualizações.
- **Regras Gerais de Interpretação (RGIs)** da NCM – oito regras que orientam a classificação quando a descrição da mercadoria não é suficiente.
- **Nota Explicativa da NCM** – comentários que auxiliam na interpretação de cada posição.
- **Decisões de empate da Câmara de Comércio Exterior (CAMEX)** e **soluções de consulta** da Receita Federal – interpretações oficiais em casos específicos.
- **Lei nº 12.741/2012** (Lei do Pedágio) e **Lei nº 13.988/2020** – tratam do crédito/tributo de CBS/IBS no contexto da Reforma Tributária (em transição).

O TribIA não deve substituir a responsabilidade do classificador profissional, mas auxiliar na coleta de informações, sugestão de NCM fundamentada e geração de relatório de apoio.

## 2. Etapas do processo de classificação no TribIA

### 2.1. Entrada de dados pelo usuário
- **Nome da mercadoria** – identificação comercial.
- **Descrição técnica** – composição, matérias‑primas, processo de fabricação, finalidade, características físicas/químicas.
- **Composição (opcional)** – percentual de cada matéria‑prima ou componente.
- **Finalidade (opcional)** – uso previsto (ex.: embalagem, consumo industrial, etc.).
- **Características (opcional)** – formato, tamanho, peso, embalagem, etc.
- **NCM atual (opcional)** – caso a empresa já utilize um código, para comparação.
- **Anexos** – documentos técnicos (PDF, planilhas, imagens, textos) que contenham informações relevantes para a classificação (ficha técnica, CATS, laudos, etc.).

*Base legal:* a classificação deve partir do conhecimento integral do produto (RGI 1) e de suas notas explicativas; quanto mais detalhada a descrição, mais precisa a análise.

### 2.2. Envio de documentos técnicos
O usuário pode anexar até 10 arquivos (PDF, PNG, JPG, DOC, XLSX, TXT, etc.), cada um de até 10 MB. Esses documentos são armazenados e disponibilizados ao serviço de IA para extração de informações.

*Base legal:* documentos oficiais (ficha técnica, laudos) são considerados fontes confiáveis para complementar a descrição (RGIs 2‑6 e notas explicativas).

### 2.3. Solicitação de classificação fiscal (inteligência fiscal)
Ao iniciar a análise, o frontend envia os dados e anexos ao backend via endpoint `POST /api/clientes/{clienteId}/analises-fiscais`. O backend então:

1. **Pré‑processamento** – valida presença de campos obrigatórios (nome, descrição ≥ 20 caracteres, NCM atual com 8 dígitos se informado).
2. **Extração de texto dos anexos** – usando OCR ou parsing simples para obter informações relevantes.
3. **Consulta ao cache global** – busca por combinação NCM + descrição normalizada em tabela de classificações já validadas (se houver, retorna sugestão com alta confiança).
4. **Análise por IA (Gemini)** – envia um prompt estruturado contendo:
   - Descrição da mercadoria.
   - Informações extraídas dos anexos.
   - Pergunta específica: “Qual o código NCM mais adequado para esta mercadoria, conforme a Lei nº 12.973/2014, as RGIs e as notas explicativas? Forneça justificativa com referência ao artigo, alínea ou nota explicativa aplicável.”
   - O modelo retorna: NCM sugerido, descrição oficial da posição, justificativa textual, nível de confiança (0‑1) e fontes usadas (ex.: nota explicativa 85.01, RGI 3, etc.).
5. **Fallback para regras oficiais** – caso a IA falhe ou retorne confiança baixa (< 0,6), o sistema aplica a regra oficial da Tabela NCM (busca por posição lógica a partir dos primeiros 4 dígitos do NCM informado ou da descrição) e gera sugestão com confiança baixa, indicando necessidade de revisão humana.
6. **Geração de alternativa(s)** – além da NCM principal, o sistema pode gerar até duas alternativas com pontuação JEV (valor entre 0 e 1) que mede a compatibilidade com as regras fiscais, não sendo probabilidade de acerto.
7. **Validação fiscal automática** – verifica:
   - Vigência do NCM sugerido (consulta à base de dados da Receita Federal, se disponível).
   - Existência de restrições (ex.: NCM sujeito a Licença Importação/Exportação, tratamento tributário especial).
   - Conformidade com a alíquota de IPI, II, IOF conforme a TIPI (se aplicável).
   - Possibilidade de crédito de CBS/IBS (se a mercadoria for insumo para produção de bens sujeitos ao novo tributo).
8. **Elaboração do relatório** – compila:
   - Dados de entrada do usuário.
   - Resumo dos anexos processados.
   - NCM sugerida, descrição oficial, justificativa com referências legais (artigos, incisos, notas explicativas, decisões de empate).
   - Eventual NCM atual da empresa e comparação.
   - Lista de alternativas com pontuação JEV e avaliação.
   - Resultado da validação fiscal (situação: VALIDADO_VERIFICACOES, PENDENTE_REVISAO, INFORMACOES_INSUFICIENTES, INCONSISTENCIA).
   - Lista de fontes consultadas (tabelas oficiais, notas explicativas, decisões de CAMEX, soluções de consulta).
   - Limitações da análise (ex.: depende da qualidade dos anexos, não substitui parecer técnico jurídico).
9. **Armazenamento** – grava a análise em tabela própria, vinculada ao cliente e ao usuário solicitante, com status (`AGUARDANDO`, `INTERPRETANDO`, `PESQUISANDO_NCM`, `AVALIANDO`, `VALIDANDO`, `GERANDO_RELATORIO`, `CONCLUIDA`, `FALHA`, `INFORMACOES_INSUFICIENTES`, `AGUARDANDO_REVISAO`).

### 2.4. Acompanhamento do processamento
O frontend consulta periodicamente o endpoint `GET /api/analises-fiscais/{id}` para obter o status atual e exibir ao usuário (traga, spinner, mensagens de etapa). Enquanto o status estiver em `EM_ANDAMENTO` (lista de etapas intermediárias), a tela indica que a análise está em progresso.

### 2.5. Conclusão e entrega do resultado
Quando o status chegar a `CONCLUIDA`, o frontend exibe:

- NCM sugerida em destaque.
- Descrição oficial da posição NCM.
- Justificativa completa com citações legais.
- Validação fiscal (situação e detalhes).
- Eventuais alertas (ex.: NCM sujeito a restrição, necessidade de documento especial).
- Alternativas com pontuação JEV.
- Lista de fontes.
- Botão para download do relatório individual (PDF/HTML) contendo todas as informações acima.

Se o status for `INFORMACOES_INSUFICIENTES` ou `AGUARDANDO_REVISAO`, o sistema indica quais informações faltam (ex.: composição não informada, necessidade de laudo técnico) e permite que o usuário retome a análise enviando novos dados ou documentos.

### 2.6. Geração do relatório individual
O relatório é gerado em formato PDF (ou HTML para visualização) contendo:

- Cabeçalho com identificação da análise (número, cliente, data).
- Seção “Mercadoria analisada” com nome, descrição, composição, finalidade, características.
- Seção “Documentos analisados” com lista de anexos e resumo de conteúdo extraído.
- Seção “Classificação fiscal sugerida” com NCM, descrição oficial, justificativa estruturada (tópicos: características relevantes, regras aplicadas, notas explicativas consultadas, decisões de empate).
- Seção “Validação fiscal automática” com resultados das verificações (vigência, restrições, tributos, créditos CBS/IBS).
- Seção “Alternativas consideradas” (se houver).
- Seção “Fundamentação legal” com lista de normas, artigos, incisos, notas explicativas, decisões de empate usadas.
- Seção “Limitações e avisos” – disclaimer de que o relatório não substitui parecer técnico jurídico nem homologação pela Receita Federal.
- Rodapé com número de protocolo e data/hora de geração.

### 2.7. Arquivamento e reutilização
Toda análise concluída é armazenada e pode ser:

- Reaberta pelo mesmo usuário para atualização com novos documentos.
- Utilizada como base para classificação de notas fiscais posteriores (cache global): ao classificar uma nota, o sistema verifica se o NCM da análise fiscal está em cache e, se validado, aplica diretamente com alta confiança.
- Exportada pelo administrador para auditoria ou compartilhamento com contabilidade.

## 3. Responsabilidades e limites

- O TribIA **não emite decisão vinculante** da Receita Federal; apenas fornece subsídio técnico.
- O usuário classificador permanece responsável por validar a NCM sugerida perante a legislação aplicável e, se necessário, solicitar solução de consulta à Receita Federal.
- O sistema deve manter atualizada sua base de dados da NCM (versão vigente) e das notas explicativas, importando oficialmente as publicações da Receita Federal sempre que houver atualização.
- Qualquer sugestão de IA deve ser revisada por profissional habilitado antes de ser utilizada em operações fiscais (emissão de NF-e, apuração de tributos).

## 4. Referências consultadas (exemplos)

- Lei nº 12.973/2014.
- Decreto nº 11.029/2022.
- Resolução Camex nº 20/2021.
- Regras Gerais de Interpretação (RGI) 1‑8 da NCM/SH.
- Notas Explicativas da NCM (versão 2022/2023).
- Manual de Orientação Classificação Fiscal (MOC) – Receita Federal (quando disponível).
- Soluções de consulta COSIT e dockets da CAMEX (exemplo: COSIT nº 123/2020).
- Tabela de Incidência do IPI (TIPI) para verificação de alíquotas.
- Legislação sobre CBS/IBS (Lei complementar 214/2015 e posteriores) para validação de crédito futuro.

*Obs.: Esta lista é ilustrativa; a implementação deve usar as fontes oficiais vigentes no momento da execução.*

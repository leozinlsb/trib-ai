-- Clientes de demonstração (adendo, seção 1). Todos os CNPJs são fictícios, com dígitos verificadores válidos.
-- MERGE ... KEY(cnpj): roda a cada inicialização sem duplicar (o banco agora é em arquivo).
-- A ordem define os ids na primeira carga: 1 = Distribuidora, 2 = Farmácia, 3 = Loja.
-- fabricante = false: os três revendem; no Imposto Seletivo (monofásico) quem paga é o fabricante.

-- 1. Emitente da nota de teste (nfe_teste_hackathon.xml).
MERGE INTO cliente (cnpj, razao_social, nome_fantasia, regime, setor, uf, municipio, codigo_municipio, fabricante)
KEY (cnpj)
VALUES ('10433218000193', 'Distribuidora Fictícia de Alimentos Ltda', 'Distribuidora Fictícia',
        'LUCRO_REAL', 'Distribuição de alimentos', 'SP', 'São Paulo', '3550308', FALSE);

-- 2. Hoje paga PIS/Cofins cumulativo, sem crédito. Em 2027 passa a ter crédito.
MERGE INTO cliente (cnpj, razao_social, nome_fantasia, regime, setor, uf, municipio, codigo_municipio, fabricante)
KEY (cnpj)
VALUES ('45723174000110', 'Farmácia Fictícia Saúde Ltda', 'Farma Fictícia',
        'LUCRO_PRESUMIDO', 'Varejo de medicamentos', 'SP', 'Campinas', '3509502', FALSE);

-- 3. Tributação cheia.
MERGE INTO cliente (cnpj, razao_social, nome_fantasia, regime, setor, uf, municipio, codigo_municipio, fabricante)
KEY (cnpj)
VALUES ('31592846000191', 'Utilidades e Limpeza Fictícia Ltda', 'Casa Limpa Fictícia',
        'LUCRO_REAL', 'Varejo de utilidades e limpeza', 'SP', 'São Paulo', '3550308', FALSE);

-- Clientes de demonstração (adendo, seção 1). Todos os CNPJs são fictícios, com dígitos verificadores válidos.
-- A ordem de inserção define os ids: 1 = Distribuidora, 2 = Farmácia, 3 = Loja.

-- 1. Emitente da nota de teste (nfe_teste_hackathon.xml). Muita cesta básica: tende a pagar menos em 2027.
INSERT INTO cliente (cnpj, razao_social, nome_fantasia, regime, setor, uf, municipio, codigo_municipio)
VALUES ('10433218000193', 'Distribuidora Fictícia de Alimentos Ltda', 'Distribuidora Fictícia',
        'LUCRO_REAL', 'Distribuição de alimentos', 'SP', 'São Paulo', '3550308');

-- 2. Hoje paga PIS/Cofins cumulativo, sem crédito. Em 2027 passa a ter crédito.
INSERT INTO cliente (cnpj, razao_social, nome_fantasia, regime, setor, uf, municipio, codigo_municipio)
VALUES ('45723174000110', 'Farmácia Fictícia Saúde Ltda', 'Farma Fictícia',
        'LUCRO_PRESUMIDO', 'Varejo de medicamentos', 'SP', 'Campinas', '3509502');

-- 3. Tributação cheia. Mostra um cliente em que a carga sobe.
INSERT INTO cliente (cnpj, razao_social, nome_fantasia, regime, setor, uf, municipio, codigo_municipio)
VALUES ('31592846000191', 'Utilidades e Limpeza Fictícia Ltda', 'Casa Limpa Fictícia',
        'LUCRO_REAL', 'Varejo de utilidades e limpeza', 'SP', 'São Paulo', '3550308');

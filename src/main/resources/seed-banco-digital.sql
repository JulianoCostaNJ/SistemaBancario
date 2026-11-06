-- POO Bank: dados de demonstracao do banco digital (clientes Mariana Costa e Lucas Almeida).
-- Aplicado por FabricaConexao.popularDadosBancoDigital() DEPOIS que app.PopuladorBancoDigital criou as
-- contas e as transacoes pelo ServicoBancario. Este script NAO grava saldo nem transacoes: so os dados
-- da extensao, que apontam para as contas pelo documento do titular e para as transacoes pelo horario.
-- E idempotente: ids fixos e INSERT OR IGNORE. Roda em uma unica transacao e falha inteiro se faltarem contas.
-- Todos os valores sao referenciais e seguem o prototipo de telas. Os horarios das transacoes seguem o
-- formato yyyy-MM-dd HH:mm:ss.SSSSSS usado por TransacaoRepositorySqlite.
-- Credencial de demonstracao: senha poobank@2026 (PBKDF2WithHmacSHA256, 120000 iteracoes, chave de 32 bytes
-- em hexadecimal). A senha em texto nunca e gravada. Nao usar em ambiente real.
-- Nao usar ponto e virgula dentro de comentarios nem de textos.

INSERT OR IGNORE INTO categorias (id, nome, icone, cor) VALUES
    (1, 'Saúde', 'heart-pulse', '#1677FF'),
    (2, 'Alimentação', 'utensils', '#047857'),
    (3, 'Lazer', 'ticket', '#CA7A00'),
    (4, 'Transporte', 'car', '#00C97F'),
    (5, 'Compras', 'shopping-bag', '#7C3AED'),
    (6, 'Depósito', 'arrow-down-left', '#16A34A'),
    (7, 'Saque', 'banknote', '#475569'),
    (8, 'Transferência', 'arrow-left-right', '#0EA5E9'),
    (9, 'Fatura do cartão', 'credit-card', '#64748B');

INSERT OR IGNORE INTO dados_conta (conta_numero, agencia, numero_exibicao, digito)
SELECT c.numero, '0001', '10482', '7' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '321.654.987-00';

INSERT OR IGNORE INTO dados_conta (conta_numero, agencia, numero_exibicao, digito)
SELECT c.numero, '0001', '20831', '4' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '456.789.123-00';

INSERT OR IGNORE INTO credenciais (conta_numero, senha_hash, salt, iteracoes, criado_em)
SELECT c.numero, 'd0282523435e16fa5e4220aa30d511ffcc30c69d602e1824ce92659454e7a42b', 'a1b2c3d4e5f60718293a4b5c6d7e8f90', 120000, '2026-07-01 09:00:00.000000' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '321.654.987-00';

INSERT OR IGNORE INTO credenciais (conta_numero, senha_hash, salt, iteracoes, criado_em)
SELECT c.numero, 'e5d9080001cfa5dd2ac49cc2d81a6b8c618b7367b147099fab429da4c132226e', '0f1e2d3c4b5a69788796a5b4c3d2e1f0', 120000, '2026-07-01 09:00:00.000000' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '456.789.123-00';

INSERT OR IGNORE INTO chaves_pix (id, conta_numero, tipo, chave, criada_em)
SELECT 1, c.numero, 'CPF', '32165498700', '2026-07-01 09:30:00.000000' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '321.654.987-00';

INSERT OR IGNORE INTO chaves_pix (id, conta_numero, tipo, chave, criada_em)
SELECT 2, c.numero, 'EMAIL', 'mariana.costa@poobank.com.br', '2026-07-01 09:30:00.000000' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '321.654.987-00';

INSERT OR IGNORE INTO chaves_pix (id, conta_numero, tipo, chave, criada_em)
SELECT 3, c.numero, 'TELEFONE', '+5541999990001', '2026-07-01 09:30:00.000000' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '321.654.987-00';

INSERT OR IGNORE INTO chaves_pix (id, conta_numero, tipo, chave, criada_em)
SELECT 4, c.numero, 'ALEATORIA', 'b3c1f2a4-7d5e-4c8a-9f10-2a6e5d4c3b21', '2026-07-01 09:30:00.000000' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '321.654.987-00';

INSERT OR IGNORE INTO chaves_pix (id, conta_numero, tipo, chave, criada_em)
SELECT 5, c.numero, 'CPF', '45678912300', '2026-07-01 09:30:00.000000' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '456.789.123-00';

INSERT OR IGNORE INTO chaves_pix (id, conta_numero, tipo, chave, criada_em)
SELECT 6, c.numero, 'EMAIL', 'lucas.almeida@poobank.com.br', '2026-07-01 09:30:00.000000' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '456.789.123-00';

INSERT OR IGNORE INTO cartoes (id, conta_numero, bandeira, produto, apelido, final_cartao, nome_impresso, validade, limite_total, dia_fechamento, dia_vencimento, principal, status)
SELECT 1, c.numero, 'VISA', 'Platinum', 'Cartão principal', '4821', 'MARIANA COSTA', '12/26', '5000.00', 12, 19, 1, 'ATIVO' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '321.654.987-00';

INSERT OR IGNORE INTO cartoes (id, conta_numero, bandeira, produto, apelido, final_cartao, nome_impresso, validade, limite_total, dia_fechamento, dia_vencimento, principal, status)
SELECT 2, c.numero, 'MASTERCARD', 'Internacional', 'Cartão internacional', '1187', 'MARIANA COSTA', '08/27', '3000.00', 12, 19, 0, 'ATIVO' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '321.654.987-00';

INSERT OR IGNORE INTO faturas (id, cartao_id, competencia, fechamento, vencimento, status) VALUES
    (1, 1, '2026-07', '2026-08-12', '2026-08-19', 'PAGA'),
    (2, 1, '2026-08', '2026-09-12', '2026-09-19', 'PAGA'),
    (3, 1, '2026-09', '2026-10-12', '2026-10-19', 'PAGA'),
    (4, 1, '2026-10', '2026-11-12', '2026-11-19', 'ABERTA'),
    (5, 1, '2026-11', '2026-12-12', '2026-12-19', 'FUTURA'),
    (6, 1, '2026-12', '2027-01-12', '2027-01-19', 'FUTURA');

INSERT OR IGNORE INTO lancamentos_cartao (id, cartao_id, fatura_id, categoria_id, estabelecimento, descricao, valor, parcela_atual, total_parcelas, data_compra) VALUES
    (1, 1, 1, 2, 'Supermercado Bom Preço', 'Compras do mês', '520.00', 1, 1, '2026-07-18'),
    (2, 1, 1, 4, 'Aplicativo de transporte', 'Corridas', '370.00', 1, 1, '2026-07-25'),
    (3, 1, 2, 5, 'Loja Central', 'Vestuário', '700.00', 1, 1, '2026-08-15'),
    (4, 1, 2, 1, 'Clínica Vida', 'Consulta', '420.00', 1, 1, '2026-08-22'),
    (5, 1, 3, 2, 'Restaurante Sabor', 'Jantar', '600.00', 1, 1, '2026-09-14'),
    (6, 1, 3, 3, 'Cinema Centro', 'Ingressos', '380.00', 1, 1, '2026-09-26'),
    (7, 1, 4, 5, 'Tech Store', 'Smartphone 128GB', '655.00', 1, 3, '2026-10-14'),
    (8, 1, 4, 3, 'Streaming Plus', 'Assinatura mensal', '35.00', 1, 1, '2026-10-20'),
    (9, 1, 4, 3, 'Cinema e Jantar', 'Saída com amigos', '110.00', 1, 1, '2026-11-02'),
    (10, 1, 4, 1, 'Farmácia Vida', 'Medicamentos', '165.00', 1, 1, '2026-11-03'),
    (11, 1, 4, 2, 'Mercado Bom Preço', 'Compras do mês', '137.50', 1, 1, '2026-11-04'),
    (12, 1, 4, 4, 'Aplicativo de transporte', 'Corridas', '137.50', 1, 1, '2026-11-05'),
    (13, 1, 5, 5, 'Tech Store', 'Smartphone 128GB', '655.00', 2, 3, '2026-10-14'),
    (14, 1, 6, 5, 'Tech Store', 'Smartphone 128GB', '655.00', 3, 3, '2026-10-14');

INSERT OR IGNORE INTO transacao_detalhes (transacao_id, categoria_id, descricao, contraparte)
SELECT t.id, 9, 'Pagamento de fatura - Julho 2026', 'Visa Platinum 4821' FROM transacoes t WHERE t.tipo = 'SAQUE' AND t.horario = '2026-08-11 10:05:00.000000';

INSERT OR IGNORE INTO transacao_detalhes (transacao_id, categoria_id, descricao, contraparte)
SELECT t.id, 9, 'Pagamento de fatura - Agosto 2026', 'Visa Platinum 4821' FROM transacoes t WHERE t.tipo = 'SAQUE' AND t.horario = '2026-09-12 09:40:00.000000';

INSERT OR IGNORE INTO transacao_detalhes (transacao_id, categoria_id, descricao, contraparte)
SELECT t.id, 9, 'Pagamento de fatura - Setembro 2026', 'Visa Platinum 4821' FROM transacoes t WHERE t.tipo = 'SAQUE' AND t.horario = '2026-10-14 16:20:00.000000';

INSERT OR IGNORE INTO transacao_detalhes (transacao_id, categoria_id, descricao, contraparte)
SELECT t.id, 6, 'Depósito na conta', NULL FROM transacoes t WHERE t.tipo = 'DEPOSITO' AND t.horario = '2026-11-03 09:15:00.000000';

INSERT OR IGNORE INTO transacao_detalhes (transacao_id, categoria_id, descricao, contraparte)
SELECT t.id, 8, 'Transferência enviada', 'Lucas Almeida' FROM transacoes t WHERE t.tipo = 'TRANSFERENCIA' AND t.horario = '2026-11-04 14:30:00.000000';

INSERT OR IGNORE INTO transacao_detalhes (transacao_id, categoria_id, descricao, contraparte)
SELECT t.id, 7, 'Saque simulado', NULL FROM transacoes t WHERE t.tipo = 'SAQUE' AND t.horario = '2026-11-05 10:20:00.000000';

INSERT OR IGNORE INTO pagamentos_fatura (id, fatura_id, transacao_id, valor, pago_em)
SELECT 1, 1, t.id, '890.00', '2026-08-11 10:05:00.000000' FROM transacoes t WHERE t.tipo = 'SAQUE' AND t.horario = '2026-08-11 10:05:00.000000';

INSERT OR IGNORE INTO pagamentos_fatura (id, fatura_id, transacao_id, valor, pago_em)
SELECT 2, 2, t.id, '1120.00', '2026-09-12 09:40:00.000000' FROM transacoes t WHERE t.tipo = 'SAQUE' AND t.horario = '2026-09-12 09:40:00.000000';

INSERT OR IGNORE INTO pagamentos_fatura (id, fatura_id, transacao_id, valor, pago_em)
SELECT 3, 3, t.id, '980.00', '2026-10-14 16:20:00.000000' FROM transacoes t WHERE t.tipo = 'SAQUE' AND t.horario = '2026-10-14 16:20:00.000000';

INSERT OR IGNORE INTO metas (id, conta_numero, nome, valor_alvo, valor_atual, prazo)
SELECT 1, c.numero, 'Férias', '3000.00', '2190.00', '2027-01-15' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '321.654.987-00';

INSERT OR IGNORE INTO metas (id, conta_numero, nome, valor_alvo, valor_atual, prazo)
SELECT 2, c.numero, 'Emergência', '5000.00', '2800.00', '2027-06-30' FROM contas c JOIN clientes cl ON cl.id = c.cliente_id WHERE cl.documento = '321.654.987-00';

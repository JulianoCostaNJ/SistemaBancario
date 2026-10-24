-- POO Bank: esquema do banco SQLite.
-- Valores monetarios (saldo e valor) sao gravados como TEXT, com a representacao decimal exata
-- do BigDecimal (toPlainString). Nunca usar REAL, que perde precisao.
-- O horario e gravado como TEXT no formato fixo yyyy-MM-dd HH:mm:ss.SSSSSS, para que a ordem
-- alfabetica seja igual a ordem cronologica do extrato.
-- Os comandos sao separados por ponto e virgula e executados um a um. Nao usar ponto e virgula
-- dentro de comentarios.

CREATE TABLE IF NOT EXISTS clientes (
    id        INTEGER PRIMARY KEY,
    nome      TEXT NOT NULL,
    documento TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS contas (
    numero     INTEGER PRIMARY KEY,
    cliente_id INTEGER NOT NULL REFERENCES clientes (id),
    saldo      TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS transacoes (
    id            INTEGER PRIMARY KEY,
    conta_origem  INTEGER REFERENCES contas (numero),
    conta_destino INTEGER REFERENCES contas (numero),
    tipo          TEXT NOT NULL CHECK (tipo IN ('DEPOSITO', 'SAQUE', 'TRANSFERENCIA')),
    valor         TEXT NOT NULL,
    horario       TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_transacoes_conta_origem ON transacoes (conta_origem);

CREATE INDEX IF NOT EXISTS idx_transacoes_conta_destino ON transacoes (conta_destino);

-- POO Bank: extensao do esquema para o banco digital (acesso, categorias, Pix, cartoes, faturas, metas).
-- Aplicado DEPOIS de schema.sql, que continua sendo o nucleo exigido pela especificacao.
-- Nada aqui altera clientes, contas ou transacoes: todas as tabelas novas apontam para elas por chave
-- estrangeira, e todos os comandos sao CREATE IF NOT EXISTS, entao o script pode rodar mais de uma vez.
-- Valores monetarios sao TEXT com a representacao decimal exata do BigDecimal, sempre com 2 casas.
-- Datas e horarios sao TEXT: horario no formato yyyy-MM-dd HH:mm:ss.SSSSSS e datas em yyyy-MM-dd.
-- O saldo da conta continua sendo alterado somente por Conta. Pix e pagamento de fatura nao mexem em
-- saldo: eles apontam para uma linha de transacoes (DEPOSITO, SAQUE ou TRANSFERENCIA) ja concluida.
-- O limite disponivel do cartao NAO e gravado: e calculado a partir do limite total e dos lancamentos.
-- Os comandos sao separados por ponto e virgula e executados um a um. Nao usar ponto e virgula
-- dentro de comentarios nem de textos.

-- ---------------------------------------------------------------- acesso

CREATE TABLE IF NOT EXISTS credenciais (
    conta_numero INTEGER PRIMARY KEY REFERENCES contas (numero),
    senha_hash   TEXT    NOT NULL,
    salt         TEXT    NOT NULL,
    iteracoes    INTEGER NOT NULL CHECK (iteracoes > 0),
    criado_em    TEXT    NOT NULL
);

-- ---------------------------------------------------------------- identificacao exibida da conta

CREATE TABLE IF NOT EXISTS dados_conta (
    conta_numero    INTEGER PRIMARY KEY REFERENCES contas (numero),
    agencia         TEXT NOT NULL CHECK (length(agencia) = 4 AND agencia NOT GLOB '*[^0-9]*'),
    numero_exibicao TEXT NOT NULL CHECK (length(numero_exibicao) >= 1 AND numero_exibicao NOT GLOB '*[^0-9]*'),
    digito          TEXT NOT NULL CHECK (length(digito) = 1 AND digito NOT GLOB '*[^0-9]*'),
    UNIQUE (agencia, numero_exibicao, digito)
);

-- ---------------------------------------------------------------- extrato inteligente

CREATE TABLE IF NOT EXISTS categorias (
    id    INTEGER PRIMARY KEY,
    nome  TEXT NOT NULL UNIQUE,
    icone TEXT NOT NULL,
    cor   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS transacao_detalhes (
    transacao_id INTEGER PRIMARY KEY REFERENCES transacoes (id),
    categoria_id INTEGER REFERENCES categorias (id),
    descricao    TEXT NOT NULL,
    contraparte  TEXT
);

CREATE INDEX IF NOT EXISTS idx_transacao_detalhes_categoria ON transacao_detalhes (categoria_id);

-- ---------------------------------------------------------------- Pix

CREATE TABLE IF NOT EXISTS chaves_pix (
    id           INTEGER PRIMARY KEY,
    conta_numero INTEGER NOT NULL REFERENCES contas (numero),
    tipo         TEXT    NOT NULL CHECK (tipo IN ('CPF', 'EMAIL', 'TELEFONE', 'ALEATORIA')),
    chave        TEXT    NOT NULL UNIQUE,
    criada_em    TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chaves_pix_conta ON chaves_pix (conta_numero);

CREATE TABLE IF NOT EXISTS pix_operacoes (
    id             INTEGER PRIMARY KEY,
    transacao_id   INTEGER NOT NULL UNIQUE REFERENCES transacoes (id),
    chave_pix_id   INTEGER REFERENCES chaves_pix (id),
    sentido        TEXT    NOT NULL CHECK (sentido IN ('ENVIADO', 'RECEBIDO')),
    id_fim_a_fim   TEXT    NOT NULL UNIQUE
);

-- ---------------------------------------------------------------- cartoes, faturas e compras

CREATE TABLE IF NOT EXISTS cartoes (
    id              INTEGER PRIMARY KEY,
    conta_numero    INTEGER NOT NULL REFERENCES contas (numero),
    bandeira        TEXT    NOT NULL CHECK (bandeira IN ('VISA', 'MASTERCARD', 'ELO')),
    produto         TEXT    NOT NULL,
    apelido         TEXT    NOT NULL,
    final_cartao    TEXT    NOT NULL CHECK (length(final_cartao) = 4 AND final_cartao NOT GLOB '*[^0-9]*'),
    nome_impresso   TEXT    NOT NULL,
    validade        TEXT    NOT NULL CHECK (validade GLOB '[0-9][0-9]/[0-9][0-9]'),
    limite_total    TEXT    NOT NULL CHECK (limite_total NOT GLOB '*[^0-9.]*' AND limite_total GLOB '[0-9]*.[0-9][0-9]'),
    dia_fechamento  INTEGER NOT NULL CHECK (dia_fechamento BETWEEN 1 AND 28),
    dia_vencimento  INTEGER NOT NULL CHECK (dia_vencimento BETWEEN 1 AND 28),
    principal       INTEGER NOT NULL CHECK (principal IN (0, 1)),
    status          TEXT    NOT NULL CHECK (status IN ('ATIVO', 'BLOQUEADO', 'CANCELADO'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_cartoes_principal ON cartoes (conta_numero) WHERE principal = 1;

CREATE INDEX IF NOT EXISTS idx_cartoes_conta ON cartoes (conta_numero);

CREATE TABLE IF NOT EXISTS faturas (
    id          INTEGER PRIMARY KEY,
    cartao_id   INTEGER NOT NULL REFERENCES cartoes (id),
    competencia TEXT    NOT NULL CHECK (competencia GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]'),
    fechamento  TEXT    NOT NULL,
    vencimento  TEXT    NOT NULL,
    status      TEXT    NOT NULL CHECK (status IN ('FUTURA', 'ABERTA', 'FECHADA', 'PAGA', 'VENCIDA')),
    UNIQUE (cartao_id, competencia)
);

CREATE TABLE IF NOT EXISTS lancamentos_cartao (
    id              INTEGER PRIMARY KEY,
    cartao_id       INTEGER NOT NULL REFERENCES cartoes (id),
    fatura_id       INTEGER NOT NULL REFERENCES faturas (id),
    categoria_id    INTEGER REFERENCES categorias (id),
    estabelecimento TEXT    NOT NULL,
    descricao       TEXT,
    valor           TEXT    NOT NULL CHECK (valor NOT GLOB '*[^0-9.]*' AND valor GLOB '[0-9]*.[0-9][0-9]'),
    parcela_atual   INTEGER NOT NULL DEFAULT 1 CHECK (parcela_atual >= 1),
    total_parcelas  INTEGER NOT NULL DEFAULT 1 CHECK (total_parcelas >= 1),
    data_compra     TEXT    NOT NULL,
    CHECK (parcela_atual <= total_parcelas)
);

CREATE INDEX IF NOT EXISTS idx_lancamentos_fatura ON lancamentos_cartao (fatura_id);

CREATE INDEX IF NOT EXISTS idx_lancamentos_categoria ON lancamentos_cartao (categoria_id);

CREATE TABLE IF NOT EXISTS pagamentos_fatura (
    id           INTEGER PRIMARY KEY,
    fatura_id    INTEGER NOT NULL REFERENCES faturas (id),
    transacao_id INTEGER NOT NULL UNIQUE REFERENCES transacoes (id),
    valor        TEXT    NOT NULL CHECK (valor NOT GLOB '*[^0-9.]*' AND valor GLOB '[0-9]*.[0-9][0-9]'),
    pago_em      TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pagamentos_fatura ON pagamentos_fatura (fatura_id);

-- ---------------------------------------------------------------- metas

CREATE TABLE IF NOT EXISTS metas (
    id           INTEGER PRIMARY KEY,
    conta_numero INTEGER NOT NULL REFERENCES contas (numero),
    nome         TEXT    NOT NULL,
    valor_alvo   TEXT    NOT NULL CHECK (valor_alvo NOT GLOB '*[^0-9.]*' AND valor_alvo GLOB '[0-9]*.[0-9][0-9]'),
    valor_atual  TEXT    NOT NULL CHECK (valor_atual NOT GLOB '*[^0-9.]*' AND valor_atual GLOB '[0-9]*.[0-9][0-9]'),
    prazo        TEXT
);

CREATE INDEX IF NOT EXISTS idx_metas_conta ON metas (conta_numero);

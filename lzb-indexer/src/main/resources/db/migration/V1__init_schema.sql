-- lzb-indexer initial schema (PostgreSQL 16)

CREATE TABLE token_transfers (
    id           BIGSERIAL PRIMARY KEY,
    tx_hash      VARCHAR(66)  NOT NULL,
    block_number BIGINT       NOT NULL,
    log_index    INT          NOT NULL,
    from_address VARCHAR(42)  NOT NULL,
    to_address   VARCHAR(42)  NOT NULL,
    amount       NUMERIC      NOT NULL,
    chain_name   VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP,
    CONSTRAINT uk_transfer UNIQUE (tx_hash, log_index, chain_name)
);
CREATE INDEX idx_transfer_chain_block ON token_transfers (chain_name, block_number);
CREATE INDEX idx_transfer_chain_from ON token_transfers (chain_name, from_address);
CREATE INDEX idx_transfer_chain_to ON token_transfers (chain_name, to_address);

CREATE TABLE swap_events (
    id           BIGSERIAL PRIMARY KEY,
    tx_hash      VARCHAR(66)  NOT NULL,
    block_number BIGINT       NOT NULL,
    log_index    INT          NOT NULL,
    sender       VARCHAR(42)  NOT NULL,
    receiver     VARCHAR(42)  NOT NULL,
    amount0_in   NUMERIC      NOT NULL,
    amount1_in   NUMERIC      NOT NULL,
    amount0_out  NUMERIC      NOT NULL,
    amount1_out  NUMERIC      NOT NULL,
    chain_name   VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP,
    CONSTRAINT uk_swap UNIQUE (tx_hash, log_index, chain_name)
);
CREATE INDEX idx_swap_chain_block ON swap_events (chain_name, block_number);
CREATE INDEX idx_swap_chain_sender ON swap_events (chain_name, sender);
CREATE INDEX idx_swap_chain_receiver ON swap_events (chain_name, receiver);

CREATE TABLE gmx_position_history (
    id               BIGSERIAL PRIMARY KEY,
    event_type       VARCHAR(20)  NOT NULL,
    tx_hash          VARCHAR(66)  NOT NULL,
    block_number     BIGINT       NOT NULL,
    log_index        INT          NOT NULL,
    position_key     VARCHAR(66),
    account          VARCHAR(42)  NOT NULL,
    collateral_token VARCHAR(42)  NOT NULL,
    index_token      VARCHAR(42)  NOT NULL,
    collateral_delta NUMERIC      NOT NULL,
    size_delta       NUMERIC      NOT NULL,
    is_long          BOOLEAN      NOT NULL,
    price            NUMERIC      NOT NULL,
    fee              NUMERIC,
    chain_name       VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP,
    CONSTRAINT uk_gmx_history UNIQUE (tx_hash, log_index, chain_name)
);
CREATE INDEX idx_gmx_ph_account ON gmx_position_history (chain_name, account, block_number);
CREATE INDEX idx_gmx_ph_key ON gmx_position_history (chain_name, position_key);

CREATE TABLE gmx_positions (
    id                BIGSERIAL PRIMARY KEY,
    position_key      VARCHAR(66)  NOT NULL,
    account           VARCHAR(42)  NOT NULL,
    collateral_token  VARCHAR(42)  NOT NULL,
    index_token       VARCHAR(42)  NOT NULL,
    is_long           BOOLEAN      NOT NULL,
    size              NUMERIC      NOT NULL,
    collateral        NUMERIC      NOT NULL,
    average_price     NUMERIC      NOT NULL,
    total_fee         NUMERIC,
    entry_block       BIGINT       NOT NULL,
    entry_tx          VARCHAR(66)  NOT NULL,
    last_update_block BIGINT       NOT NULL,
    last_update_tx    VARCHAR(66)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    chain_name        VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT uk_gmx_position UNIQUE (chain_name, position_key)
);
CREATE INDEX idx_gmx_pos_account ON gmx_positions (chain_name, account, status);
CREATE INDEX idx_gmx_pos_market ON gmx_positions (chain_name, index_token, status);

CREATE TABLE scanned_blocks (
    block_number BIGINT       NOT NULL,
    block_hash   VARCHAR(66)  NOT NULL,
    chain_name   VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP,
    PRIMARY KEY (block_number, chain_name)
);

CREATE TABLE sync_checkpoints (
    id                    BIGSERIAL PRIMARY KEY,
    contract_address      VARCHAR(42)  NOT NULL,
    last_scanned_block    BIGINT       NOT NULL,
    last_scanned_tx_index INT,
    is_reorg_protected    BOOLEAN,
    chain_name            VARCHAR(255) NOT NULL,
    updated_at            TIMESTAMP,
    CONSTRAINT uk_checkpoint_contract UNIQUE (contract_address)
);

CREATE TABLE sync_errors (
    id            BIGSERIAL PRIMARY KEY,
    chain_name    VARCHAR(255) NOT NULL,
    block_number  BIGINT       NOT NULL,
    error_type    VARCHAR(20)  NOT NULL,
    error_message TEXT         NOT NULL,
    created_at    TIMESTAMP
);
CREATE INDEX idx_sync_errors_block ON sync_errors (chain_name, block_number);

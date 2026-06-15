CREATE TABLE IF NOT EXISTS token_transfers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tx_hash         VARCHAR(66)   NOT NULL,
    block_number    BIGINT        NOT NULL,
    log_index       INT           NOT NULL,
    from_address    VARCHAR(42)   NOT NULL,
    to_address      VARCHAR(42)   NOT NULL,
    value           NUMERIC       NOT NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_transfer_unique ON token_transfers(tx_hash, log_index);
CREATE INDEX IF NOT EXISTS idx_transfer_from ON token_transfers(from_address);
CREATE INDEX IF NOT EXISTS idx_transfer_to ON token_transfers(to_address);
CREATE INDEX IF NOT EXISTS idx_transfer_block ON token_transfers(block_number);

CREATE TABLE IF NOT EXISTS sync_checkpoints (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_address        VARCHAR(42)   NOT NULL,
    last_scanned_block      BIGINT        NOT NULL,
    last_scanned_tx_index   INT           DEFAULT 0,
    is_reorg_protected      BOOLEAN       DEFAULT FALSE,
    updated_at              TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_checkpoint_contract ON sync_checkpoints(contract_address);

CREATE TABLE IF NOT EXISTS sync_errors (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    block_number    BIGINT        NOT NULL,
    error_message   TEXT          NOT NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sync_errors_block ON sync_errors(block_number);
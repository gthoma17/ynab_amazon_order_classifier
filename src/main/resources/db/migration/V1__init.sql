CREATE TABLE IF NOT EXISTS app_config (
    key        VARCHAR NOT NULL PRIMARY KEY,
    value      TEXT    NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS category_rules (
    id                 INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    ynab_category_id   VARCHAR   NOT NULL,
    ynab_category_name VARCHAR   NOT NULL,
    user_description   TEXT      NOT NULL,
    updated_at         TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS amazon_orders (
    id                  INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    email_message_id    VARCHAR   NOT NULL UNIQUE,
    order_date          TIMESTAMP NOT NULL,
    total_amount        DECIMAL   NOT NULL,
    items_json          TEXT      NOT NULL,
    status              VARCHAR   NOT NULL,
    ynab_transaction_id VARCHAR,
    ynab_category_id    VARCHAR,
    created_at          TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_logs (
    id       INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    source   VARCHAR   NOT NULL,
    last_run TIMESTAMP NOT NULL,
    status   VARCHAR   NOT NULL,
    message  TEXT
);

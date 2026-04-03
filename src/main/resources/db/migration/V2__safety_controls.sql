-- Safety controls: order cap, schedule config, start-from date, installation timestamp

INSERT OR IGNORE INTO app_config (key, value, updated_at)
VALUES ('INSTALLED_AT', strftime('%Y-%m-%d', 'now'), datetime('now'));

INSERT OR IGNORE INTO app_config (key, value, updated_at)
VALUES ('START_FROM_DATE', strftime('%Y-%m-%d', 'now'), datetime('now'));

INSERT OR IGNORE INTO app_config (key, value, updated_at)
VALUES ('ORDER_CAP', '0', datetime('now'));

INSERT OR IGNORE INTO app_config (key, value, updated_at)
VALUES ('SCHEDULE_CONFIG', '{"type":"EVERY_N_HOURS","hourInterval":5}', datetime('now'));

-- Dry-run results table: one row per order that would have been updated
CREATE TABLE IF NOT EXISTS dry_run_results (
    id                     INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    order_id               INTEGER,
    order_date             TIMESTAMP NOT NULL,
    total_amount           DECIMAL   NOT NULL,
    items_json             TEXT      NOT NULL,
    ynab_transaction_id    VARCHAR,
    proposed_category_id   VARCHAR,
    proposed_category_name VARCHAR,
    error_message          TEXT,
    run_at                 TIMESTAMP NOT NULL
);

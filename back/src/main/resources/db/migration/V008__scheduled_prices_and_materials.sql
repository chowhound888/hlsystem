ALTER TABLE price_adjustment ADD COLUMN effective_at DATETIME(3) NULL,
 ADD COLUMN confirmation_mode VARCHAR(16) NOT NULL DEFAULT 'MANUAL';
INSERT IGNORE INTO sys_config VALUES ('auto_price_job_user','7');
CREATE TABLE price_valuation_detail (
 id BIGINT PRIMARY KEY, adjustment_id BIGINT NOT NULL, stock_id BIGINT NOT NULL,
 quantity_snapshot DECIMAL(18,6) NOT NULL, difference DECIMAL(18,2) NOT NULL,
 UNIQUE(adjustment_id,stock_id), FOREIGN KEY(adjustment_id) REFERENCES price_adjustment(id), FOREIGN KEY(stock_id) REFERENCES inv_stock(id)
);

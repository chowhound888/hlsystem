ALTER TABLE acc_voucher ADD COLUMN created_by BIGINT NULL, ADD COLUMN posted_by BIGINT NULL;
ALTER TABLE prep_production_order ADD COLUMN abnormal_loss_cost DECIMAL(18,2) NOT NULL DEFAULT 0;
INSERT IGNORE INTO sys_config VALUES ('accounting_posting_mode','AUTO');
CREATE TABLE prep_material_return (
 id BIGINT PRIMARY KEY, production_order_id BIGINT NOT NULL, allocation_id BIGINT NOT NULL,
 document_id BIGINT NOT NULL, base_qty DECIMAL(18,6) NOT NULL, cost_amount DECIMAL(18,2) NOT NULL,
 reason VARCHAR(500) NOT NULL, created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(production_order_id) REFERENCES prep_production_order(id), FOREIGN KEY(allocation_id) REFERENCES inv_allocation(id), FOREIGN KEY(document_id) REFERENCES inv_document(id)
);

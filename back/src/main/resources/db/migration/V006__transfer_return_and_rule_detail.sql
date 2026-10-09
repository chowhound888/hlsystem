ALTER TABLE inv_allocation ADD COLUMN received_cost DECIMAL(18,2) NOT NULL DEFAULT 0;
UPDATE inv_allocation SET received_cost=cost_amount WHERE received_qty=base_qty;
CREATE TABLE inv_transfer_receipt (
 id BIGINT PRIMARY KEY, document_id BIGINT NOT NULL, operator_id BIGINT NOT NULL,
 received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(document_id) REFERENCES inv_document(id)
);
CREATE TABLE inv_transfer_receipt_item (
 id BIGINT PRIMARY KEY, receipt_id BIGINT NOT NULL, allocation_id BIGINT NOT NULL,
 base_qty DECIMAL(18,6) NOT NULL, cost_amount DECIMAL(18,2) NOT NULL,
 FOREIGN KEY(receipt_id) REFERENCES inv_transfer_receipt(id), FOREIGN KEY(allocation_id) REFERENCES inv_allocation(id)
);
CREATE TABLE ip_return_order (
 id BIGINT PRIMARY KEY, execution_id BIGINT NOT NULL UNIQUE, document_id BIGINT NOT NULL,
 amount DECIMAL(18,2) NOT NULL, reason VARCHAR(500) NOT NULL, created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(execution_id) REFERENCES ip_order_execution(id), FOREIGN KEY(document_id) REFERENCES inv_document(id)
);
ALTER TABLE ru_rule ADD COLUMN min_age INT NULL, ADD COLUMN max_age INT NULL,
 ADD COLUMN dose_basis VARCHAR(32) NOT NULL DEFAULT 'DAILY', ADD COLUMN per_kg BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN route VARCHAR(32) NULL;
CREATE TABLE ip_ward_usage (
 id BIGINT PRIMARY KEY, location_id BIGINT NOT NULL, item_id BIGINT NOT NULL, encounter_id BIGINT NULL,
 document_id BIGINT NOT NULL, base_qty DECIMAL(18,6) NOT NULL, amount DECIMAL(18,2) NOT NULL,
 purpose VARCHAR(500) NOT NULL, operator_id BIGINT NOT NULL, used_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(location_id) REFERENCES sys_location(id), FOREIGN KEY(item_id) REFERENCES md_item(id), FOREIGN KEY(encounter_id) REFERENCES cl_encounter(id), FOREIGN KEY(document_id) REFERENCES inv_document(id)
);

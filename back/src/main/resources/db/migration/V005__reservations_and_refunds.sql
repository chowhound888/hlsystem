ALTER TABLE inv_allocation ADD COLUMN returned_cost DECIMAL(18,2) NOT NULL DEFAULT 0;
CREATE TABLE inv_stock_reservation (
 id BIGINT PRIMARY KEY, prescription_item_id BIGINT NOT NULL, stock_id BIGINT NOT NULL,
 base_qty DECIMAL(18,6) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
 expires_at DATETIME(3) NOT NULL, UNIQUE(prescription_item_id,stock_id),
 FOREIGN KEY(prescription_item_id) REFERENCES op_prescription_item(id), FOREIGN KEY(stock_id) REFERENCES inv_stock(id), CHECK(base_qty>0)
);
CREATE TABLE sys_print_template (
 id BIGINT PRIMARY KEY, document_type VARCHAR(32) NOT NULL UNIQUE, title VARCHAR(200) NOT NULL,
 fields_json JSON NOT NULL, paper_orientation VARCHAR(16) NOT NULL DEFAULT 'portrait', revision INT NOT NULL DEFAULT 1
);
CREATE TABLE pur_plan (
 id BIGINT PRIMARY KEY, number VARCHAR(64) NOT NULL UNIQUE, location_id BIGINT NOT NULL,
 supplier_id BIGINT NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED', created_by BIGINT NOT NULL,
 approved_by BIGINT NULL, receipt_document_id BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(location_id) REFERENCES sys_location(id), FOREIGN KEY(supplier_id) REFERENCES md_supplier(id), FOREIGN KEY(receipt_document_id) REFERENCES inv_document(id)
);
CREATE TABLE pur_plan_item (
 id BIGINT PRIMARY KEY, plan_id BIGINT NOT NULL, item_id BIGINT NOT NULL, base_qty DECIMAL(18,6) NOT NULL,
 FOREIGN KEY(plan_id) REFERENCES pur_plan(id), FOREIGN KEY(item_id) REFERENCES md_item(id), CHECK(base_qty>0)
);

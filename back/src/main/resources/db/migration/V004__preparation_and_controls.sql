ALTER TABLE sys_idempotency ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING';
CREATE TABLE inv_control_register (
 id BIGINT PRIMARY KEY, document_item_id BIGINT NOT NULL UNIQUE, patient_id BIGINT NULL,
 recipient VARCHAR(300) NOT NULL, reviewer_id BIGINT NOT NULL, executor_id BIGINT NOT NULL,
 registered_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(document_item_id) REFERENCES inv_document_item(id),
 FOREIGN KEY(patient_id) REFERENCES cl_patient(id), CHECK(reviewer_id<>executor_id)
);
CREATE TABLE prep_formula (
 id BIGINT PRIMARY KEY, code VARCHAR(64) NOT NULL UNIQUE, name VARCHAR(200) NOT NULL, output_item_id BIGINT NOT NULL,
 revision INT NOT NULL DEFAULT 1, standard_output_qty DECIMAL(18,6) NOT NULL,
 standard_hours DECIMAL(18,6) NOT NULL DEFAULT 0, standard_energy_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
 created_by BIGINT NOT NULL, FOREIGN KEY(output_item_id) REFERENCES md_item(id), CHECK(standard_output_qty>0)
);
CREATE TABLE prep_formula_item (
 id BIGINT PRIMARY KEY, formula_id BIGINT NOT NULL, input_item_id BIGINT NOT NULL, standard_base_qty DECIMAL(18,6) NOT NULL,
 FOREIGN KEY(formula_id) REFERENCES prep_formula(id), FOREIGN KEY(input_item_id) REFERENCES md_item(id), CHECK(standard_base_qty>0)
);
CREATE TABLE prep_production_order (
 id BIGINT PRIMARY KEY, number VARCHAR(64) NOT NULL UNIQUE, formula_id BIGINT NOT NULL, formula_snapshot JSON NOT NULL,
 location_id BIGINT NOT NULL, planned_qty DECIMAL(18,6) NOT NULL, good_qty DECIMAL(18,6),
 batch_no VARCHAR(100) NOT NULL, expires_on DATE NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
 created_by BIGINT NOT NULL, approved_by BIGINT NULL, quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 material_cost DECIMAL(18,2) NOT NULL DEFAULT 0, labor_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
 energy_cost DECIMAL(18,2) NOT NULL DEFAULT 0, other_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
 unit_cost DECIMAL(18,6), issue_document_id BIGINT NULL, output_document_id BIGINT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(formula_id) REFERENCES prep_formula(id),
 FOREIGN KEY(location_id) REFERENCES sys_location(id), FOREIGN KEY(issue_document_id) REFERENCES inv_document(id), FOREIGN KEY(output_document_id) REFERENCES inv_document(id)
);
CREATE TABLE prep_process_record (
 id BIGINT PRIMARY KEY, production_order_id BIGINT NOT NULL, step_no INT NOT NULL, name VARCHAR(200) NOT NULL,
 actual_hours DECIMAL(18,6) NOT NULL, output_qty DECIMAL(18,6) NOT NULL, energy_cost DECIMAL(18,2) NOT NULL,
 operator_id BIGINT NOT NULL, recorded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(production_order_id) REFERENCES prep_production_order(id), UNIQUE(production_order_id,step_no)
);
CREATE TABLE prep_quality_test (
 id BIGINT PRIMARY KEY, production_order_id BIGINT NULL, stock_id BIGINT NULL, type VARCHAR(32) NOT NULL,
 standard_description VARCHAR(1000) NOT NULL, measured_value VARCHAR(1000) NOT NULL, conclusion VARCHAR(32) NOT NULL,
 tested_by BIGINT NOT NULL, reviewed_by BIGINT NULL, status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
 tested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(production_order_id) REFERENCES prep_production_order(id), FOREIGN KEY(stock_id) REFERENCES inv_stock(id)
);
CREATE TABLE prep_sales_order (
 id BIGINT PRIMARY KEY, number VARCHAR(64) NOT NULL UNIQUE, location_id BIGINT NOT NULL, item_id BIGINT NOT NULL,
 customer_name VARCHAR(200) NOT NULL, quantity DECIMAL(18,6) NOT NULL, unit_price DECIMAL(18,6) NOT NULL,
 amount DECIMAL(18,2) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', created_by BIGINT NOT NULL,
 inventory_document_id BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(location_id) REFERENCES sys_location(id), FOREIGN KEY(item_id) REFERENCES md_item(id), FOREIGN KEY(inventory_document_id) REFERENCES inv_document(id)
);

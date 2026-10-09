CREATE TABLE cl_patient (
 id BIGINT PRIMARY KEY, patient_no VARCHAR(64) NOT NULL UNIQUE, name VARCHAR(100) NOT NULL,
 sex VARCHAR(16) NOT NULL, birth_date DATE NOT NULL, phone VARCHAR(30), weight_kg DECIMAL(8,2),
 allergy_notes VARCHAR(500), pregnancy_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE TABLE cl_patient_allergy (
 id BIGINT PRIMARY KEY, patient_id BIGINT NOT NULL, item_id BIGINT NULL, ingredient_id BIGINT NULL,
 reaction VARCHAR(500) NOT NULL, severity VARCHAR(32) NOT NULL DEFAULT 'BLOCK',
 FOREIGN KEY(patient_id) REFERENCES cl_patient(id), FOREIGN KEY(item_id) REFERENCES md_item(id), FOREIGN KEY(ingredient_id) REFERENCES md_ingredient(id)
);
CREATE TABLE cl_encounter (
 id BIGINT PRIMARY KEY, number VARCHAR(64) NOT NULL UNIQUE, patient_id BIGINT NOT NULL,
 type VARCHAR(32) NOT NULL, department_id BIGINT NOT NULL, doctor_id BIGINT NOT NULL,
 ward_location_id BIGINT NULL, bed_no VARCHAR(30), status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
 admitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), discharged_at DATETIME(3),
 FOREIGN KEY(patient_id) REFERENCES cl_patient(id), FOREIGN KEY(department_id) REFERENCES sys_department(id),
 FOREIGN KEY(doctor_id) REFERENCES sys_user(id), FOREIGN KEY(ward_location_id) REFERENCES sys_location(id)
);
CREATE TABLE cl_encounter_diagnosis (
 encounter_id BIGINT NOT NULL, diagnosis_id BIGINT NOT NULL, PRIMARY KEY(encounter_id,diagnosis_id),
 FOREIGN KEY(encounter_id) REFERENCES cl_encounter(id), FOREIGN KEY(diagnosis_id) REFERENCES md_diagnosis(id)
);
CREATE TABLE cl_charge (
 id BIGINT PRIMARY KEY, encounter_id BIGINT NOT NULL, source_type VARCHAR(32) NOT NULL, source_id BIGINT NOT NULL,
 amount DECIMAL(18,2) NOT NULL, paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0, refunded_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
 status VARCHAR(32) NOT NULL DEFAULT 'UNPAID', UNIQUE(source_type,source_id), FOREIGN KEY(encounter_id) REFERENCES cl_encounter(id), CHECK(amount>=0)
);
CREATE TABLE cl_payment (
 id BIGINT PRIMARY KEY, charge_id BIGINT NULL, encounter_id BIGINT NOT NULL, kind VARCHAR(32) NOT NULL,
 amount DECIMAL(18,2) NOT NULL, operator_id BIGINT NOT NULL, reason VARCHAR(500),
 paid_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(charge_id) REFERENCES cl_charge(id), FOREIGN KEY(encounter_id) REFERENCES cl_encounter(id), CHECK(amount>0)
);
CREATE TABLE cl_deposit_account (
 id BIGINT PRIMARY KEY, encounter_id BIGINT NOT NULL UNIQUE, balance DECIMAL(18,2) NOT NULL DEFAULT 0,
 deposited_amount DECIMAL(18,2) NOT NULL DEFAULT 0, charged_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
 FOREIGN KEY(encounter_id) REFERENCES cl_encounter(id)
);
CREATE TABLE cl_deposit_ledger (
 id BIGINT PRIMARY KEY, account_id BIGINT NOT NULL, event_key VARCHAR(180) NOT NULL UNIQUE,
 delta DECIMAL(18,2) NOT NULL, balance_after DECIMAL(18,2) NOT NULL, reason VARCHAR(500), operator_id BIGINT NOT NULL,
 occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(account_id) REFERENCES cl_deposit_account(id)
);
CREATE TABLE op_prescription (
 id BIGINT PRIMARY KEY, number VARCHAR(64) NOT NULL UNIQUE, encounter_id BIGINT NOT NULL, doctor_id BIGINT NOT NULL,
 location_id BIGINT NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
 check_status VARCHAR(32) NOT NULL DEFAULT 'UNCHECKED', reviewed_by BIGINT NULL, verified_by BIGINT NULL, dispensed_by BIGINT NULL,
 inventory_document_id BIGINT NULL, version INT NOT NULL DEFAULT 0, warning_reason VARCHAR(500),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(encounter_id) REFERENCES cl_encounter(id),
 FOREIGN KEY(doctor_id) REFERENCES sys_user(id), FOREIGN KEY(location_id) REFERENCES sys_location(id), FOREIGN KEY(inventory_document_id) REFERENCES inv_document(id), INDEX ix_prescription(location_id,status,created_at)
);
CREATE TABLE op_prescription_item (
 id BIGINT PRIMARY KEY, prescription_id BIGINT NOT NULL, item_id BIGINT NOT NULL, unit_id BIGINT NOT NULL,
 quantity DECIMAL(18,6) NOT NULL, factor_snapshot DECIMAL(18,6) NOT NULL, base_qty DECIMAL(18,6) NOT NULL,
 dose DECIMAL(18,6) NOT NULL, dose_unit VARCHAR(32) NOT NULL DEFAULT 'mg', frequency_per_day INT NOT NULL,
 days INT NOT NULL DEFAULT 1, route VARCHAR(32) NOT NULL DEFAULT '口服', unit_price DECIMAL(18,6) NOT NULL DEFAULT 0,
 amount DECIMAL(18,2) NOT NULL DEFAULT 0, returned_base_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
 inventory_item_id BIGINT NULL, FOREIGN KEY(prescription_id) REFERENCES op_prescription(id), FOREIGN KEY(item_id) REFERENCES md_item(id),
 FOREIGN KEY(unit_id) REFERENCES md_unit(id), FOREIGN KEY(inventory_item_id) REFERENCES inv_document_item(id), CHECK(base_qty>0), CHECK(dose>0), CHECK(frequency_per_day>0)
);
CREATE TABLE ph_return_order (
 id BIGINT PRIMARY KEY, prescription_id BIGINT NOT NULL, prescription_item_id BIGINT NOT NULL,
 base_qty DECIMAL(18,6) NOT NULL, amount DECIMAL(18,2) NOT NULL, quality_disposition VARCHAR(32) NOT NULL,
 reason VARCHAR(500) NOT NULL, inventory_document_id BIGINT NOT NULL, refund_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(prescription_id) REFERENCES op_prescription(id), FOREIGN KEY(prescription_item_id) REFERENCES op_prescription_item(id), FOREIGN KEY(inventory_document_id) REFERENCES inv_document(id)
);
CREATE TABLE ip_medical_order (
 id BIGINT PRIMARY KEY, number VARCHAR(64) NOT NULL UNIQUE, encounter_id BIGINT NOT NULL, item_id BIGINT NOT NULL,
 location_id BIGINT NOT NULL, doctor_id BIGINT NOT NULL, order_type VARCHAR(32) NOT NULL,
 start_date DATE NOT NULL, end_date DATE NULL, interval_days INT NOT NULL DEFAULT 1, times_json JSON NOT NULL,
 base_qty DECIMAL(18,6) NOT NULL, dose DECIMAL(18,6) NOT NULL, dose_unit VARCHAR(32) NOT NULL DEFAULT 'mg', route VARCHAR(32) NOT NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', stopped_at DATETIME(3), created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(encounter_id) REFERENCES cl_encounter(id), FOREIGN KEY(item_id) REFERENCES md_item(id), FOREIGN KEY(location_id) REFERENCES sys_location(id), CHECK(interval_days>0 AND base_qty>0)
);
CREATE TABLE ip_order_execution (
 id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL, scheduled_at DATETIME(3) NOT NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'PENDING', amount DECIMAL(18,2) NOT NULL DEFAULT 0,
 unit_price DECIMAL(18,6), confirmed_by BIGINT NULL, verified_by BIGINT NULL, dispensed_by BIGINT NULL,
 warning_reason VARCHAR(500), inventory_document_id BIGINT NULL, UNIQUE(order_id,scheduled_at),
 FOREIGN KEY(order_id) REFERENCES ip_medical_order(id), FOREIGN KEY(inventory_document_id) REFERENCES inv_document(id)
);
CREATE TABLE ip_ward_quota (
 id BIGINT PRIMARY KEY, location_id BIGINT NOT NULL, item_id BIGINT NOT NULL, quota_qty DECIMAL(18,6) NOT NULL,
 UNIQUE(location_id,item_id), FOREIGN KEY(location_id) REFERENCES sys_location(id), FOREIGN KEY(item_id) REFERENCES md_item(id)
);
CREATE TABLE ru_rule (
 id BIGINT PRIMARY KEY, code VARCHAR(64) NOT NULL UNIQUE, type VARCHAR(32) NOT NULL, name VARCHAR(200) NOT NULL,
 item_id BIGINT NOT NULL, peer_item_id BIGINT NULL, diagnosis_id BIGINT NULL,
 min_dose DECIMAL(18,6), max_dose DECIMAL(18,6), dose_unit VARCHAR(32) NOT NULL DEFAULT 'mg',
 severity VARCHAR(32) NOT NULL, message VARCHAR(1000) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 revision INT NOT NULL DEFAULT 1, source_note VARCHAR(500) NOT NULL DEFAULT '课程演示',
 FOREIGN KEY(item_id) REFERENCES md_item(id), FOREIGN KEY(peer_item_id) REFERENCES md_item(id), FOREIGN KEY(diagnosis_id) REFERENCES md_diagnosis(id)
);
CREATE TABLE ru_check (
 id BIGINT PRIMARY KEY, source_type VARCHAR(32) NOT NULL, source_id BIGINT NOT NULL, result VARCHAR(32) NOT NULL,
 snapshot_json JSON NOT NULL, checked_by BIGINT NOT NULL, checked_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX ix_check(source_type,source_id)
);
CREATE TABLE ru_knowledge (
 id BIGINT PRIMARY KEY, item_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL, content TEXT NOT NULL,
 source_note VARCHAR(500) NOT NULL DEFAULT '课程演示', revision INT NOT NULL DEFAULT 1, FOREIGN KEY(item_id) REFERENCES md_item(id)
);
INSERT INTO md_ingredient VALUES (1,'AMOXICILLIN','阿莫西林'),(2,'PARACETAMOL','对乙酰氨基酚');

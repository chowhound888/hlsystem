CREATE TABLE inv_batch (
 id BIGINT PRIMARY KEY, item_id BIGINT NOT NULL, manufacturer_id BIGINT NOT NULL, batch_no VARCHAR(100) NOT NULL,
 manufactured_on DATE NOT NULL, expires_on DATE NOT NULL, quality_status VARCHAR(32) NOT NULL DEFAULT 'PASSED',
 special_source VARCHAR(32) NOT NULL DEFAULT 'PURCHASE', UNIQUE(item_id,manufacturer_id,batch_no),
 FOREIGN KEY(item_id) REFERENCES md_item(id), FOREIGN KEY(manufacturer_id) REFERENCES md_manufacturer(id),
 CHECK(expires_on>=manufactured_on), INDEX ix_expiry(item_id,quality_status,expires_on)
);
CREATE TABLE inv_document (
 id BIGINT PRIMARY KEY, number VARCHAR(64) NOT NULL UNIQUE, type VARCHAR(32) NOT NULL, location_id BIGINT NOT NULL,
 target_location_id BIGINT NULL, supplier_id BIGINT NULL, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
 business_date DATE NOT NULL, created_by BIGINT NOT NULL, approved_by BIGINT NULL, executed_by BIGINT NULL,
 reason VARCHAR(500), special_source VARCHAR(32) NOT NULL DEFAULT 'PURCHASE', version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), executed_at DATETIME(3),
 FOREIGN KEY(location_id) REFERENCES sys_location(id), FOREIGN KEY(target_location_id) REFERENCES sys_location(id),
 FOREIGN KEY(supplier_id) REFERENCES md_supplier(id), FOREIGN KEY(created_by) REFERENCES sys_user(id),
 INDEX ix_documents(location_id,status,business_date)
);
CREATE TABLE inv_document_item (
 id BIGINT PRIMARY KEY, document_id BIGINT NOT NULL, item_id BIGINT NOT NULL, unit_id BIGINT NOT NULL,
 quantity DECIMAL(18,6) NOT NULL, factor_snapshot DECIMAL(18,6) NOT NULL, base_qty DECIMAL(18,6) NOT NULL,
 batch_no VARCHAR(100), manufactured_on DATE, expires_on DATE, manufacturer_id BIGINT NULL,
 unit_cost DECIMAL(18,6) NOT NULL DEFAULT 0, cost_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
 original_item_id BIGINT NULL,
 FOREIGN KEY(document_id) REFERENCES inv_document(id), FOREIGN KEY(item_id) REFERENCES md_item(id), FOREIGN KEY(unit_id) REFERENCES md_unit(id),
 CHECK(quantity>0), CHECK(base_qty>0), CHECK(unit_cost>=0)
);
CREATE TABLE inv_cost_layer (
 id BIGINT PRIMARY KEY, batch_id BIGINT NOT NULL, receipt_item_id BIGINT NOT NULL UNIQUE,
 base_unit_cost DECIMAL(18,6) NOT NULL, original_qty DECIMAL(18,6) NOT NULL, original_cost DECIMAL(18,2) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(batch_id) REFERENCES inv_batch(id), FOREIGN KEY(receipt_item_id) REFERENCES inv_document_item(id)
);
CREATE TABLE inv_stock (
 id BIGINT PRIMARY KEY, location_id BIGINT NOT NULL, cost_layer_id BIGINT NOT NULL, stock_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
 on_hand_qty DECIMAL(18,6) NOT NULL DEFAULT 0, reserved_qty DECIMAL(18,6) NOT NULL DEFAULT 0, remaining_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
 frozen BOOLEAN NOT NULL DEFAULT FALSE, version INT NOT NULL DEFAULT 0, UNIQUE(location_id,cost_layer_id,stock_status),
 FOREIGN KEY(location_id) REFERENCES sys_location(id), FOREIGN KEY(cost_layer_id) REFERENCES inv_cost_layer(id),
 CHECK(on_hand_qty>=0), CHECK(reserved_qty>=0 AND reserved_qty<=on_hand_qty), CHECK(remaining_cost>=0)
);
CREATE TABLE inv_ledger (
 id BIGINT PRIMARY KEY, stock_id BIGINT NOT NULL, document_item_id BIGINT NOT NULL, event_key VARCHAR(180) NOT NULL UNIQUE,
 business_date DATE NOT NULL, qty_delta DECIMAL(18,6) NOT NULL, cost_delta DECIMAL(18,2) NOT NULL,
 balance_qty DECIMAL(18,6) NOT NULL, balance_cost DECIMAL(18,2) NOT NULL, operator_id BIGINT NOT NULL,
 occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(stock_id) REFERENCES inv_stock(id),
 FOREIGN KEY(document_item_id) REFERENCES inv_document_item(id), INDEX ix_ledger(stock_id,business_date,id)
);
CREATE TABLE inv_allocation (
 id BIGINT PRIMARY KEY, document_item_id BIGINT NOT NULL, source_stock_id BIGINT NOT NULL,
 cost_layer_id BIGINT NOT NULL, base_qty DECIMAL(18,6) NOT NULL, cost_amount DECIMAL(18,2) NOT NULL,
 received_qty DECIMAL(18,6) NOT NULL DEFAULT 0, returned_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
 FOREIGN KEY(document_item_id) REFERENCES inv_document_item(id), FOREIGN KEY(source_stock_id) REFERENCES inv_stock(id),
 FOREIGN KEY(cost_layer_id) REFERENCES inv_cost_layer(id), CHECK(received_qty>=0 AND received_qty<=base_qty)
);
CREATE TABLE inv_requisition (
 id BIGINT PRIMARY KEY, number VARCHAR(64) NOT NULL UNIQUE, location_id BIGINT NOT NULL, supply_location_id BIGINT NOT NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED', created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(location_id) REFERENCES sys_location(id), FOREIGN KEY(supply_location_id) REFERENCES sys_location(id)
);
CREATE TABLE inv_requisition_item (
 id BIGINT PRIMARY KEY, requisition_id BIGINT NOT NULL, item_id BIGINT NOT NULL, base_qty DECIMAL(18,6) NOT NULL,
 FOREIGN KEY(requisition_id) REFERENCES inv_requisition(id), FOREIGN KEY(item_id) REFERENCES md_item(id)
);
CREATE TABLE inv_stocktake (
 id BIGINT PRIMARY KEY, number VARCHAR(64) NOT NULL UNIQUE, location_id BIGINT NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'COUNTING',
 created_by BIGINT NOT NULL, approved_by BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(location_id) REFERENCES sys_location(id)
);
CREATE TABLE inv_stocktake_item (
 id BIGINT PRIMARY KEY, stocktake_id BIGINT NOT NULL, stock_id BIGINT NOT NULL, snapshot_qty DECIMAL(18,6) NOT NULL,
 counted_qty DECIMAL(18,6), reason VARCHAR(500), UNIQUE(stocktake_id,stock_id), FOREIGN KEY(stocktake_id) REFERENCES inv_stocktake(id), FOREIGN KEY(stock_id) REFERENCES inv_stock(id)
);
CREATE TABLE inv_closing (
 id BIGINT PRIMARY KEY, location_id BIGINT NOT NULL, period_type VARCHAR(16) NOT NULL, period_start DATE NOT NULL,
 period_end DATE NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'CLOSED', snapshot_json JSON NOT NULL,
 closed_by BIGINT NOT NULL, closed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE(location_id,period_type,period_start,period_end), FOREIGN KEY(location_id) REFERENCES sys_location(id)
);
CREATE TABLE acc_period (period_date DATE PRIMARY KEY, status VARCHAR(16) NOT NULL DEFAULT 'OPEN');
CREATE TABLE acc_account (id BIGINT PRIMARY KEY, code VARCHAR(64) NOT NULL UNIQUE, name VARCHAR(100) NOT NULL);
CREATE TABLE acc_voucher (
 id BIGINT PRIMARY KEY, number VARCHAR(64) NOT NULL UNIQUE, event_key VARCHAR(180) NOT NULL UNIQUE,
 source_type VARCHAR(32) NOT NULL, source_id BIGINT NOT NULL, business_date DATE NOT NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'POSTED', description VARCHAR(500) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX ix_voucher(business_date,status)
);
CREATE TABLE acc_voucher_entry (
 id BIGINT PRIMARY KEY, voucher_id BIGINT NOT NULL, account_id BIGINT NOT NULL, location_id BIGINT NULL,
 debit DECIMAL(18,2) NOT NULL DEFAULT 0, credit DECIMAL(18,2) NOT NULL DEFAULT 0,
 FOREIGN KEY(voucher_id) REFERENCES acc_voucher(id), FOREIGN KEY(account_id) REFERENCES acc_account(id),
 CHECK(debit>=0 AND credit>=0 AND (debit=0 OR credit=0))
);
CREATE TABLE acc_payable (
 id BIGINT PRIMARY KEY, supplier_id BIGINT NOT NULL, document_id BIGINT NOT NULL UNIQUE,
 amount DECIMAL(18,2) NOT NULL, paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
 FOREIGN KEY(supplier_id) REFERENCES md_supplier(id), FOREIGN KEY(document_id) REFERENCES inv_document(id), CHECK(paid_amount>=0)
);
CREATE TABLE acc_supplier_payment (
 id BIGINT PRIMARY KEY, payable_id BIGINT NOT NULL, amount DECIMAL(18,2) NOT NULL, operator_id BIGINT NOT NULL,
 paid_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(payable_id) REFERENCES acc_payable(id), CHECK(amount>0)
);
CREATE TABLE price_adjustment (
 id BIGINT PRIMARY KEY, number VARCHAR(64) NOT NULL UNIQUE, item_id BIGINT NOT NULL, old_price DECIMAL(18,6) NOT NULL,
 new_price DECIMAL(18,6) NOT NULL, reason VARCHAR(500) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
 created_by BIGINT NOT NULL, approved_by BIGINT NULL, valuation_delta DECIMAL(18,2),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(item_id) REFERENCES md_item(id), CHECK(new_price>=0)
);
CREATE TABLE price_history (
 id BIGINT PRIMARY KEY, item_id BIGINT NOT NULL, adjustment_id BIGINT NOT NULL UNIQUE, old_price DECIMAL(18,6) NOT NULL,
 new_price DECIMAL(18,6) NOT NULL, effective_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(item_id) REFERENCES md_item(id), FOREIGN KEY(adjustment_id) REFERENCES price_adjustment(id)
);
INSERT INTO acc_account VALUES (1,'INVENTORY','药品库存'),(2,'PAYABLE','应付账款'),(3,'COST','药品消耗成本'),(4,'LOSS','报损损失'),(5,'CASH','资金'),(6,'REVENUE','药品收入'),(7,'PREPAID','患者预收款'),(8,'RECEIVABLE','应收款'),(9,'WIP','在制品'),(10,'VALUATION','零售估值调整');

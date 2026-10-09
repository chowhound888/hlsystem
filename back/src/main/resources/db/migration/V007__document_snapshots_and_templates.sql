ALTER TABLE inv_document_item ADD COLUMN name_snapshot VARCHAR(200), ADD COLUMN specification_snapshot VARCHAR(200), ADD COLUMN unit_snapshot VARCHAR(100);
ALTER TABLE op_prescription_item ADD COLUMN name_snapshot VARCHAR(200), ADD COLUMN specification_snapshot VARCHAR(200), ADD COLUMN unit_snapshot VARCHAR(100);
UPDATE inv_document_item x JOIN md_item i ON i.id=x.item_id JOIN md_unit u ON u.id=x.unit_id SET x.name_snapshot=i.name,x.specification_snapshot=i.specification,x.unit_snapshot=u.name;
UPDATE op_prescription_item x JOIN md_item i ON i.id=x.item_id JOIN md_unit u ON u.id=x.unit_id SET x.name_snapshot=i.name,x.specification_snapshot=i.specification,x.unit_snapshot=u.name;
ALTER TABLE inv_closing ADD COLUMN revision INT NOT NULL DEFAULT 1;
ALTER TABLE inv_closing ADD INDEX ix_closing_location(location_id);
ALTER TABLE inv_closing DROP INDEX location_id;
ALTER TABLE inv_closing ADD UNIQUE KEY uq_closing(location_id,period_type,period_start,period_end,revision);
INSERT INTO sys_print_template(id,document_type,title,fields_json) VALUES
(1,'INVENTORY','药品业务单据','["name","specification","unit","quantity","batch_no","cost_amount"]'),
(2,'PRESCRIPTION','门诊药品处方单','["name","specification","unit","quantity","directions","amount"]');
UPDATE inv_allocation SET returned_cost=ROUND(cost_amount*returned_qty/base_qty,2) WHERE returned_qty>0 AND returned_cost=0;

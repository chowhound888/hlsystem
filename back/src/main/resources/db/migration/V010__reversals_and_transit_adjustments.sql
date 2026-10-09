ALTER TABLE inv_document ADD COLUMN reversal_of_id BIGINT NULL;
ALTER TABLE inv_allocation ADD COLUMN lost_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
 ADD COLUMN lost_cost DECIMAL(18,2) NOT NULL DEFAULT 0;
CREATE TABLE inv_transit_adjustment (
 id BIGINT PRIMARY KEY, allocation_id BIGINT NOT NULL, type VARCHAR(32) NOT NULL,
 quantity DECIMAL(18,6) NOT NULL, reason VARCHAR(500) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
 created_by BIGINT NOT NULL, approved_by BIGINT NULL, document_id BIGINT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(allocation_id) REFERENCES inv_allocation(id), FOREIGN KEY(document_id) REFERENCES inv_document(id)
);

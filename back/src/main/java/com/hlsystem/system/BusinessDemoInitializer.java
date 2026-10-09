package com.hlsystem.system;

import com.hlsystem.inventory.InventoryService;
import com.hlsystem.security.DatabaseUserService;
import java.time.LocalDate;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class BusinessDemoInitializer implements ApplicationRunner {
  private final JdbcTemplate db;
  private final DatabaseUserService users;
  private final InventoryService inventory;

  public BusinessDemoInitializer(
      JdbcTemplate db, DatabaseUserService users, InventoryService inventory) {
    this.db = db;
    this.users = users;
    this.inventory = inventory;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (db.queryForObject(
            "SELECT COUNT(*) FROM sys_config WHERE config_key='business_demo_initialized'",
            Integer.class)
        > 0) return;
    db.update(
        "INSERT IGNORE INTO"
            + " md_drug_ingredient(id,drug_id,ingredient_id,strength_amount,strength_unit) VALUES"
            + " (1,1,1,250,'mg'),(2,2,2,500,'mg')");
    db.update(
        "INSERT IGNORE INTO cl_patient(id,patient_no,name,sex,birth_date,weight_kg,allergy_notes)"
            + " VALUES"
            + " (1,'PT-DEMO-001','张晓明','MALE','1992-06-15',65,'无已知药物过敏史'),(2,'PT-DEMO-002','李安宁','FEMALE','1985-11-08',58,'青霉素过敏'),(3,'PT-DEMO-003','王建平','MALE','1970-03-20',72,'无已知药物过敏史')");
    db.update(
        "INSERT IGNORE INTO"
            + " cl_encounter(id,number,patient_id,type,department_id,doctor_id,ward_location_id,bed_no)"
            + " VALUES"
            + " (1,'EN-DEMO-001',1,'OUTPATIENT',6,10,NULL,NULL),(2,'EN-DEMO-002',2,'OUTPATIENT',6,10,NULL,NULL),(3,'EN-DEMO-003',3,'INPATIENT',6,10,8,'08')");
    db.update("INSERT IGNORE INTO cl_encounter_diagnosis VALUES (1,1),(2,2),(3,3)");
    db.update("INSERT IGNORE INTO cl_deposit_account(id,encounter_id,balance) VALUES (1,3,0)");
    db.update(
        "INSERT IGNORE INTO cl_patient_allergy(id,patient_id,item_id,reaction) VALUES"
            + " (1,2,1,'青霉素类药物过敏')");
    db.update(
        "INSERT IGNORE INTO"
            + " ru_rule(id,code,type,name,item_id,min_dose,max_dose,dose_unit,severity,message)"
            + " VALUES"
            + " (1,'DEMO-DOSE-001','DOSE','阿莫西林示例日剂量',1,500,3000,'mg','WARNING','日剂量超出演示范围，需药师核实')");
    db.update(
        "INSERT IGNORE INTO ru_knowledge(id,item_id,title,content) VALUES"
            + " (1,1,'阿莫西林用药提示（演示）','按处方及药师核对结果使用。青霉素过敏者需重点检查。此条仅用于课程流程演示。'),(2,2,'对乙酰氨基酚用药提示（演示）','注意组合药中的重复成分及叠加剂量。本资料不作为临床依据。')");
    db.update(
        "INSERT IGNORE INTO"
            + " md_item(id,code,name,specification,item_type,base_unit_id,quantity_scale) VALUES"
            + " (101,'MAT001','制剂原料A','教学原料','RAW_MATERIAL',5,3),(102,'MAT002','制剂包装瓶','100ml','PACKAGING',4,0)");
    db.update(
        "INSERT IGNORE INTO md_item_unit(id,item_id,unit_id,factor_to_base) VALUES"
            + " (1010,101,5,1),(1020,102,4,1)");
    var context = SecurityContextHolder.createEmptyContext();
    SecurityContextHolder.setContext(context);
    try {
      for (long location : List.of(1L, 3L, 5L, 7L))
        for (int batch = 0; batch < 2; batch++) {
          signIn("admin");
          var lines = new ArrayList<Map<String, Object>>();
          for (var item :
              db.queryForList(
                  "SELECT i.id,i.base_unit_id,d.retail_price FROM md_item i LEFT JOIN md_drug d ON"
                      + " d.item_id=i.id WHERE (?<>7 AND i.item_type='DRUG') OR (?=7 AND i.id IN"
                      + " (101,102)) ORDER BY i.id",
                  location,
                  location)) {
            long itemId = ((Number) item.get("id")).longValue();
            String cost =
                item.get("retail_price") == null
                    ? "0.25"
                    : new java.math.BigDecimal(item.get("retail_price").toString())
                        .multiply(new java.math.BigDecimal("0.6"))
                        .toPlainString();
            lines.add(
                Map.of(
                    "itemId",
                    itemId,
                    "unitId",
                    item.get("base_unit_id"),
                    "quantity",
                    batch == 0 ? "300" : "60",
                    "batchNo",
                    "DEMO-" + itemId + "-" + batch,
                    "manufacturedOn",
                    LocalDate.now().minusMonths(3).toString(),
                    "expiresOn",
                    LocalDate.now().plusDays(batch == 0 ? 365 : 30).toString(),
                    "unitCost",
                    cost,
                    "manufacturerId",
                    1));
          }
          if (db.queryForObject(
                  "SELECT COUNT(*) FROM inv_document WHERE location_id=? AND reason=?",
                  Integer.class,
                  location,
                  "DEMO-INITIAL-" + batch)
              > 0) continue;
          long id =
              inventory.create(
                  Map.of(
                      "type",
                      "RECEIPT",
                      "locationId",
                      location,
                      "supplierId",
                      1,
                      "reason",
                      "DEMO-INITIAL-" + batch,
                      "items",
                      lines));
          inventory.action(id, "submit", 0, "");
          signIn(
              location == 1
                  ? "warehouse2"
                  : location == 3 ? "pharmacist" : location == 5 ? "inpatient" : "quality");
          inventory.action(id, "approve", 1, "");
          signIn("admin");
          inventory.action(id, "execute", 2, "");
        }
      db.update(
          "INSERT IGNORE INTO"
              + " prep_formula(id,code,name,output_item_id,standard_output_qty,standard_hours,standard_energy_cost,created_by)"
              + " VALUES (1,'F-DEMO-001','清凉合剂教学配方',6,100,2,20,8)");
      db.update("INSERT IGNORE INTO prep_formula_item VALUES (1,1,101,100),(2,1,102,100)");
      db.update("INSERT INTO sys_config VALUES ('business_demo_initialized','true')");
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void signIn(String username) {
    var u = users.loadUserByUsername(username);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
  }
}

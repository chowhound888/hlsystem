package com.hlsystem.system;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@org.springframework.core.annotation.Order(10)
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoInitializer implements ApplicationRunner {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  private final String password;

  public DemoInitializer(
      JdbcTemplate db, PasswordEncoder encoder, @Value("${app.demo.password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (db.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class) > 0) return;
    if (password == null || password.length() < 6)
      throw new IllegalStateException("Demo password must have at least 6 characters");
    String[] departments = {"信息管理科", "药库", "门诊药房", "住院药房", "制剂室", "内科", "外科病区"};
    for (int i = 0; i < departments.length; i++)
      db.update(
          "INSERT INTO sys_department(id,code,name,type) VALUES (?,?,?,?)",
          i + 1,
          "D" + (i + 1),
          departments[i],
          i == 6 ? "WARD" : "CLINICAL");
    String[][] locations = {
      {"1", "2", "主药库", "WAREHOUSE"},
      {"2", "2", "备用药库", "WAREHOUSE"},
      {"3", "3", "门诊一药房", "OUTPATIENT"},
      {"4", "3", "门诊二药房", "OUTPATIENT"},
      {"5", "4", "住院一药房", "INPATIENT"},
      {"6", "4", "住院二药房", "INPATIENT"},
      {"7", "5", "制剂库", "PREPARATION"},
      {"8", "7", "外科病区基数药", "WARD"},
      {"9", "2", "调拨在途", "TRANSIT"}
    };
    for (var l : locations)
      db.update(
          "INSERT INTO sys_location(id,department_id,code,name,type) VALUES (?,?,?,?,?)",
          Long.valueOf(l[0]),
          Long.valueOf(l[1]),
          "L" + l[0],
          l[2],
          l[3]);
    String[][] roles = {
      {"ADMIN", "管理员"},
      {"WAREHOUSE", "药库管理员"},
      {"OUTPATIENT", "门诊药师"},
      {"INPATIENT", "住院药师"},
      {"ACCOUNTANT", "药品会计"},
      {"PREPARATION", "制剂人员"},
      {"DOCTOR", "医生"}
    };
    for (int i = 0; i < roles.length; i++)
      db.update("INSERT INTO sys_role VALUES (?,?,?)", i + 1, roles[i][0], roles[i][1]);
    List<String> permissions =
        List.of(
            "system:read",
            "system:write",
            "master:read",
            "master:write",
            "inventory:read",
            "inventory:write",
            "inventory:approve",
            "inventory:execute",
            "clinical:read",
            "clinical:write",
            "clinical:payment",
            "outpatient:read",
            "outpatient:review",
            "outpatient:dispense",
            "inpatient:read",
            "inpatient:write",
            "accounting:read",
            "accounting:write",
            "pricing:write",
            "preparation:read",
            "preparation:write",
            "rational:read",
            "rational:write",
            "report:read");
    Map<Integer, List<String>> grants =
        Map.of(
            1,
            permissions,
            2,
            List.of(
                "master:read",
                "inventory:read",
                "inventory:write",
                "inventory:approve",
                "inventory:execute",
                "report:read"),
            3,
            List.of(
                "master:read",
                "inventory:read",
                "inventory:write",
                "inventory:approve",
                "inventory:execute",
                "clinical:read",
                "outpatient:read",
                "outpatient:review",
                "outpatient:dispense",
                "rational:read",
                "report:read"),
            4,
            List.of(
                "master:read",
                "inventory:read",
                "inventory:write",
                "inventory:approve",
                "inventory:execute",
                "clinical:read",
                "inpatient:read",
                "inpatient:write",
                "rational:read",
                "report:read"),
            5,
            List.of(
                "master:read",
                "inventory:read",
                "accounting:read",
                "accounting:write",
                "pricing:write",
                "report:read"),
            6,
            List.of(
                "master:read",
                "inventory:read",
                "inventory:write",
                "inventory:approve",
                "inventory:execute",
                "preparation:read",
                "preparation:write",
                "report:read"),
            7,
            List.of("master:read", "clinical:read", "clinical:write", "rational:read"));
    for (int i = 0; i < permissions.size(); i++) {
      String p = permissions.get(i);
      db.update("INSERT INTO sys_permission VALUES (?,?,?)", i + 1, p, p);
      for (var role : grants.entrySet())
        if (role.getValue().contains(p))
          db.update("INSERT INTO sys_role_permission VALUES (?,?)", role.getKey(), i + 1);
    }
    String[][] users = {
      {"admin", "系统管理员", "1", "1"},
      {"warehouse", "药库制单员", "2", "2"},
      {"warehouse2", "药库审核员", "2", "2"},
      {"pharmacist", "门诊复核药师", "3", "3"},
      {"pharmacist2", "门诊发药药师", "3", "3"},
      {"inpatient", "住院药师", "4", "4"},
      {"accountant", "药品会计", "5", "1"},
      {"preparation", "制剂生产员", "6", "5"},
      {"quality", "制剂质检员", "6", "5"},
      {"doctor", "内科医生", "7", "6"}
    };
    String hash = encoder.encode(password);
    for (int i = 0; i < users.length; i++) {
      var u = users[i];
      long id = i + 1;
      db.update(
          "INSERT INTO sys_user(id,username,password_hash,display_name,department_id) VALUES"
              + " (?,?,?,?,?)",
          id,
          u[0],
          hash,
          u[1],
          Long.valueOf(u[3]));
      db.update("INSERT INTO sys_user_role VALUES (?,?)", id, Integer.valueOf(u[2]));
      List<Long> scopes =
          switch (u[2]) {
            case "1", "5" -> List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
            case "2" -> List.of(1L, 2L);
            case "3" -> List.of(3L, 4L);
            case "4" -> List.of(5L, 6L, 8L);
            case "6" -> List.of(7L);
            default -> List.of(3L, 5L);
          };
      for (long location : scopes)
        db.update("INSERT INTO sys_user_scope VALUES (?,?)", id, location);
    }
    String[][] units = {
      {"PIECE", "片"},
      {"BOX", "盒"},
      {"VIAL", "支"},
      {"BOTTLE", "瓶"},
      {"GRAM", "克"},
      {"BAG", "袋"},
      {"KG", "千克"}
    };
    for (int i = 0; i < units.length; i++)
      db.update(
          "INSERT INTO md_unit(id,code,name) VALUES (?,?,?)", i + 1, units[i][0], units[i][1]);
    db.update(
        "INSERT INTO md_manufacturer(id,code,name,contact) VALUES (1,'M001','仁和教学制药厂','教学厂家')");
    db.update(
        "INSERT INTO md_supplier(id,code,name,contact,license_no) VALUES"
            + " (1,'S001','教学医药供应公司','课程演示','DEMO-001')");
    String[][] drugs = {
      {"阿莫西林胶囊", "0.25g×24粒", "胶囊", "抗菌药", "24", "0.5"},
      {"对乙酰氨基酚片", "0.5g×20片", "片剂", "解热镇痛", "20", "0.3"},
      {"氯化钠注射液", "250ml", "输液", "输液", "1", "4.5"},
      {"盐酸吗啡注射液", "10mg/支", "针剂", "镇痛药", "1", "12"},
      {"维生素C片", "100mg×100片", "片剂", "维生素", "100", "0.08"},
      {"院内清凉合剂", "100ml/瓶", "合剂", "院内制剂", "1", "15"}
    };
    for (int i = 0; i < drugs.length; i++) {
      long id = i + 1;
      var d = drugs[i];
      long unit = (i == 2 || i == 5) ? 4L : (i == 3 ? 3L : 1L);
      db.update(
          "INSERT INTO md_item(id,code,name,specification,item_type,base_unit_id) VALUES"
              + " (?,?,?,?,?,?)",
          id,
          "DR" + String.format("%03d", id),
          d[0],
          d[1],
          "DRUG",
          unit);
      db.update(
          "INSERT INTO md_item_unit(id,item_id,unit_id,factor_to_base) VALUES (?,?,?,1)",
          id * 10,
          id,
          unit);
      if (Integer.parseInt(d[4]) > 1)
        db.update(
            "INSERT INTO md_item_unit(id,item_id,unit_id,factor_to_base) VALUES (?,?,2,?)",
            id * 10 + 1,
            id,
            d[4]);
      db.update(
          "INSERT INTO"
              + " md_drug(id,item_id,generic_name,manufacturer_id,dosage_form,category,control_level,retail_price,in_house,insurance_category)"
              + " VALUES (?,?,?,1,?,?,?,?,?,?)",
          id,
          id,
          d[0],
          d[2],
          d[3],
          i == 3 ? "NARCOTIC" : "NORMAL",
          d[5],
          i == 5,
          "A");
      for (long location : List.of(1L, 3L, 5L))
        db.update(
            "INSERT INTO md_location_item(id,location_id,item_id,min_qty,target_qty) VALUES"
                + " (?,?,?,?,?)",
            id * 100 + location,
            location,
            id,
            20,
            200);
    }
    db.update("INSERT INTO md_drug_alias VALUES (1,2,'扑热息痛')");
    db.update("INSERT INTO md_diagnosis VALUES (1,'J00','急性鼻咽炎'),(2,'R50','发热'),(3,'I10','高血压')");
  }
}

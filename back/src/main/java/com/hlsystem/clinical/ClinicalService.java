package com.hlsystem.clinical;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.accounting.AccountingService;
import com.hlsystem.common.*;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalService {
  private final JdbcTemplate db;
  private final AccessService access;
  private final AuditService audit;
  private final AccountingService accounting;

  public ClinicalService(
      JdbcTemplate db, AccessService access, AuditService audit, AccountingService accounting) {
    this.db = db;
    this.access = access;
    this.audit = audit;
    this.accounting = accounting;
  }

  public void encounterAccess(long id) {
    var rows = db.queryForList("SELECT * FROM cl_encounter WHERE id=?", id);
    if (rows.isEmpty()) throw new BusinessException("NOT_FOUND", "就诊记录不存在", HttpStatus.NOT_FOUND);
    var e = rows.getFirst();
    if (access.current().roles().contains("DOCTOR")
        && !access.current().roles().contains("ADMIN")
        && id(e.get("department_id")) != access.current().departmentId())
      throw new BusinessException("FORBIDDEN", "不能访问其他科室的患者", HttpStatus.FORBIDDEN);
  }

  @Transactional
  public void updatePatient(long patient, Map<String, Object> r) {
    access.require("clinical:write");
    if (text(r, "name").isBlank()) throw new BusinessException("VALIDATION_FAILED", "请填写患者姓名");
    LocalDate birth = LocalDate.parse(text(r, "birthDate"));
    if (birth.isAfter(LocalDate.now()))
      throw new BusinessException("VALIDATION_FAILED", "出生日期不能在未来");
    db.update(
        "UPDATE cl_patient SET name=?,birth_date=?,phone=?,weight_kg=?,allergy_notes=? WHERE id=?",
        text(r, "name"),
        birth,
        text(r, "phone"),
        r.get("weightKg"),
        text(r, "allergyNotes"),
        patient);
    audit.record("UPDATE", "PATIENT", patient, null);
  }

  @Transactional
  public long patient(Map<String, Object> r) {
    access.require("clinical:write");
    String name = text(r, "name"), sex = text(r, "sex");
    if (name.isBlank() || name.length() > 100 || !Set.of("MALE", "FEMALE", "OTHER").contains(sex))
      throw new BusinessException("VALIDATION_FAILED", "请填写患者姓名与性别");
    LocalDate birth = LocalDate.parse(text(r, "birthDate"));
    if (birth.isAfter(LocalDate.now()))
      throw new BusinessException("VALIDATION_FAILED", "出生日期不能在未来");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO cl_patient(id,patient_no,name,sex,birth_date,phone,weight_kg,allergy_notes)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        id,
        "PT" + id,
        name,
        sex,
        birth,
        text(r, "phone"),
        r.get("weightKg"),
        text(r, "allergyNotes"));
    audit.record("CREATE", "PATIENT", id, null);
    return id;
  }

  @Transactional
  public long encounter(Map<String, Object> r) {
    access.require("clinical:write");
    long patient = id(r.get("patientId")), department = id(r.get("departmentId"));
    String type = text(r, "type");
    if (!Set.of("OUTPATIENT", "INPATIENT").contains(type))
      throw new BusinessException("VALIDATION_FAILED", "就诊类型无效");
    if (access.current().roles().contains("DOCTOR")
        && !access.current().roles().contains("ADMIN")
        && department != access.current().departmentId())
      throw new BusinessException("FORBIDDEN", "只能在本人科室创建就诊", HttpStatus.FORBIDDEN);
    long encounter = IdWorker.getId();
    Long ward = r.get("wardLocationId") == null ? null : id(r.get("wardLocationId"));
    if (type.equals("INPATIENT") && ward == null)
      throw new BusinessException("VALIDATION_FAILED", "住院须选择病区");
    db.update(
        "INSERT INTO"
            + " cl_encounter(id,number,patient_id,type,department_id,doctor_id,ward_location_id,bed_no)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        encounter,
        "EN" + encounter,
        patient,
        type,
        department,
        access.current().id(),
        ward,
        text(r, "bedNo"));
    if (type.equals("INPATIENT"))
      db.update(
          "INSERT INTO cl_deposit_account(id,encounter_id) VALUES (?,?)",
          IdWorker.getId(),
          encounter);
    if (r.get("diagnosisIds") instanceof List<?> diagnoses)
      for (Object d : diagnoses)
        db.update("INSERT INTO cl_encounter_diagnosis VALUES (?,?)", encounter, id(d));
    audit.record("CREATE", "ENCOUNTER", encounter, null);
    return encounter;
  }

  @Transactional
  public void deposit(long encounter, BigDecimal amount) {
    access.require("clinical:payment");
    encounterAccess(encounter);
    positive(amount, "押金");
    accounting.openPeriod();
    var a =
        db.queryForMap(
            "SELECT * FROM cl_deposit_account WHERE encounter_id=? FOR UPDATE", encounter);
    BigDecimal balance = decimal(a.get("balance")).add(amount);
    long payment = IdWorker.getId();
    db.update(
        "INSERT INTO cl_payment(id,encounter_id,kind,amount,operator_id) VALUES"
            + " (?,?,'DEPOSIT',?,?)",
        payment,
        encounter,
        amount,
        access.current().id());
    db.update(
        "UPDATE cl_deposit_account SET balance=?,deposited_amount=deposited_amount+? WHERE id=?",
        balance,
        amount,
        a.get("id"));
    db.update(
        "INSERT INTO cl_deposit_ledger(id,account_id,event_key,delta,balance_after,operator_id)"
            + " VALUES (?,?,?,?,?,?)",
        IdWorker.getId(),
        a.get("id"),
        "DEPOSIT:" + payment,
        amount,
        balance,
        access.current().id());
    accounting.post("DEPOSIT:" + payment, "PAYMENT", payment, "住院押金", amount, 5, 7, null);
    audit.record("DEPOSIT", "ENCOUNTER", encounter, null);
  }
}

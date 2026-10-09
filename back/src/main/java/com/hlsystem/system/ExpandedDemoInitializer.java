package com.hlsystem.system;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.accounting.AccountingService;
import com.hlsystem.clinical.ClinicalService;
import com.hlsystem.inventory.InventoryService;
import com.hlsystem.inpatient.InpatientService;
import com.hlsystem.outpatient.OutpatientService;
import com.hlsystem.preparation.PreparationService;
import com.hlsystem.security.DatabaseUserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(30)
@ConditionalOnProperty(name = "app.demo.expanded-enabled", havingValue = "true")
public class ExpandedDemoInitializer implements ApplicationRunner {
  private static final String MARKER = "expanded_demo_v1";
  private static final String REASON = "教学场景扩展数据";
  private final JdbcTemplate db;
  private final DatabaseUserService users;
  private final InventoryService inventory;
  private final OutpatientService outpatient;
  private final ClinicalService clinical;
  private final InpatientService inpatient;
  private final PreparationService preparation;
  private final AccountingService accounting;

  public ExpandedDemoInitializer(JdbcTemplate db, DatabaseUserService users, InventoryService inventory,
      OutpatientService outpatient, ClinicalService clinical, InpatientService inpatient,
      PreparationService preparation, AccountingService accounting) {
    this.db=db;this.users=users;this.inventory=inventory;this.outpatient=outpatient;
    this.clinical=clinical;this.inpatient=inpatient;this.preparation=preparation;this.accounting=accounting;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    db.update("INSERT IGNORE INTO sys_config(config_key,config_value) VALUES (?,'RUNNING')",MARKER);
    String state=db.queryForObject("SELECT config_value FROM sys_config WHERE config_key=? FOR UPDATE",String.class,MARKER);
    if("COMPLETE".equals(state)){log.info("Expanded teaching dataset already exists; no records added.");return;}
    signIn("admin");
    try {
      db.execute((ConnectionCallback<Void>) connection -> {
        ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("db/demo/expanded-catalog.sql"),java.nio.charset.StandardCharsets.UTF_8));
        return null;
      });
      createInventory();
      createOutpatient();
      createInpatient();
      createProduction();
      createPendingDocuments();
      db.update("UPDATE cl_encounter SET admitted_at=? WHERE id BETWEEN 2001 AND 2030",LocalDate.now().minusDays(20).atTime(8,0));
      verifyBalances();
      db.update("UPDATE sys_config SET config_value='COMPLETE' WHERE config_key=?",MARKER);
      log.info("Expanded teaching dataset created and balances verified.");
    } finally { SecurityContextHolder.clearContext(); }
  }

  private void createInventory() {
    for(long location:List.of(1L,2L,3L,4L,5L,6L)) {
      for(int batch=0;batch<2;batch++) {
        List<Map<String,Object>> lines=new ArrayList<>();
        for(var item:db.queryForList("SELECT i.id,i.base_unit_id,d.manufacturer_id,d.retail_price FROM md_item i JOIN md_drug d ON d.item_id=i.id WHERE i.id BETWEEN 1001 AND 1020 ORDER BY i.id")) {
          long itemId=id(item.get("id"));int quantity=(batch==0?160:35)+(int)(itemId%7)*12;
          if(location==4||location==6)quantity/=2;
          if(itemId==1004&&location==3)quantity=batch==0?8:4;
          lines.add(receiptLine(item,quantity,batch));
        }
        long doc=receipt(location,2+batch,lines,REASON+"：常规批次到货 "+location+"-"+batch);
        dateDocument(doc,LocalDate.now().minusDays(batch==0?27:12));
      }
    }
    List<Map<String,Object>> expired=new ArrayList<>();
    for(var item:db.queryForList("SELECT i.id,i.base_unit_id,d.manufacturer_id,d.retail_price FROM md_item i JOIN md_drug d ON d.item_id=i.id WHERE i.id IN (1002,1004,1009)")){
      var line=new HashMap<>(receiptLine(item,12,0));line.put("batchNo","EX-DEMO-EXPIRED-"+item.get("id"));line.put("manufacturedOn",LocalDate.now().minusMonths(18).toString());line.put("expiresOn",LocalDate.now().minusDays(4).toString());expired.add(line);
    }
    receipt(1,2,expired,REASON+"：到期隔离观察批次");
    var materials=new ArrayList<Map<String,Object>>();
    for(var item:db.queryForList("SELECT id,base_unit_id FROM md_item WHERE id BETWEEN 1101 AND 1105 OR id IN (101,102)")) {
      materials.add(Map.of("itemId",item.get("id"),"unitId",item.get("base_unit_id"),"quantity","1200","batchNo","EX-RAW-"+item.get("id"),"manufacturedOn",LocalDate.now().minusMonths(2).toString(),"expiresOn",LocalDate.now().plusYears(2).toString(),"unitCost","0.25","manufacturerId",1));
    }
    receipt(7,3,materials,REASON+"：制剂原辅料采购");
    for(var stock:db.queryForList("SELECT s.id FROM inv_stock s JOIN inv_cost_layer c ON c.id=s.cost_layer_id JOIN inv_document_item x ON x.id=c.receipt_item_id JOIN inv_document d ON d.id=x.document_id WHERE s.location_id=7 AND s.stock_status='PENDING_QC' AND d.reason=?",REASON+"：制剂原辅料采购")) {
      signIn("preparation");long test=IdWorker.getId();
      db.update("INSERT INTO prep_quality_test(id,stock_id,type,standard_description,measured_value,conclusion,tested_by) VALUES (?,?,'RAW',?,?,'PASSED',8)",test,stock.get("id"),"教学原料验收：外观、包装和批次信息完整","检测项目均符合本次教学批次标准");
      signIn("quality");preparation.confirmRaw(test);
    }
  }

  private Map<String,Object> receiptLine(Map<String,Object> item,int quantity,int batch) {
    return Map.of("itemId",item.get("id"),"unitId",item.get("base_unit_id"),"quantity",String.valueOf(quantity),"batchNo","EX-2026-"+item.get("id")+"-"+batch,"manufacturedOn",LocalDate.now().minusMonths(batch==0?3:11).toString(),"expiresOn",LocalDate.now().plusDays(batch==0?400:45).toString(),"unitCost",decimal(item.get("retail_price")).multiply(new BigDecimal(batch==0?"0.62":"0.65")).toPlainString(),"manufacturerId",item.get("manufacturer_id"));
  }

  private long receipt(long location,long supplier,List<Map<String,Object>> lines,String reason) {
    signIn("admin");long doc=inventory.create(Map.of("type","RECEIPT","locationId",location,"supplierId",supplier,"reason",reason,"items",lines));
    inventory.action(doc,"submit",0,reason);
    signIn(location<=2?"warehouse2":location<=4?"pharmacist":location<=6?"inpatient":"quality");
    inventory.action(doc,"approve",1,"已核对品种、数量、批号及效期");
    signIn("admin");inventory.action(doc,"execute",2,"验收完成，确认入库");return doc;
  }

  private void createOutpatient() {
    long[] items={1003,1005,1007,1013,1015,1016};
    String[] doses={"300","5","20","10","10","350"};
    for(int index=0;index<42;index++) {
      long item=items[index%items.length],location=index%4==0?4:3,encounter=2001+index%30;
      var entry=db.queryForMap("SELECT base_unit_id FROM md_item WHERE id=?",item);
      signIn(index%2==0?"doctor2":"doctor3");var line=Map.<String,Object>of("itemId",item,"unitId",entry.get("base_unit_id"),"quantity",String.valueOf(6+index%5*2),"dose",doses[index%items.length],"doseUnit",item==1015?"ml":"mg","frequencyPerDay",item==1005?1:2,"days",3,"route","口服");
      long rx=outpatient.create(Map.of("encounterId",encounter,"locationId",location,"items",List.of(line)));
      if(index<40)outpatient.action(rx,"submit",0,REASON);
      if(index<36){signIn("pharmacist");outpatient.action(rx,"price",1,"已结合诊断核实本次教学处方用量");}
      if(index<32){signIn("admin");outpatient.action(rx,"pay",2,"门诊窗口已收款");}
      if(index<28){signIn("pharmacist");outpatient.action(rx,"verify",3,"已核对患者身份、药品和处方");}
      if(index<24){signIn("pharmacist2");outpatient.action(rx,"dispense",4,"患者本人领药，已交代用法");datePrescription(rx,LocalDate.now().minusDays(1+index%10));}
      if(index==4||index==16){signIn("pharmacist");var lineData=db.queryForMap("SELECT id FROM op_prescription_item WHERE prescription_id=?",rx);long ret=outpatient.returnDrug(rx,Map.of("itemId",lineData.get("id"),"quantity","2","reason","治疗方案调整，未使用药品退回","qualityDisposition","QUARANTINE"));signIn("admin");if(index==4)outpatient.refund(ret);}
    }
  }

  private void createInpatient() {
    List<Long> orders=new ArrayList<>();
    for(int index=0;index<10;index++) {
      long encounter=4001+index,location=index%3==0?6:5;
      signIn("admin");clinical.deposit(encounter,new BigDecimal(500+index*100));
      signIn(index%2==0?"doctor2":"doctor3");orders.add(inpatient.create(Map.ofEntries(Map.entry("encounterId",encounter),Map.entry("locationId",location),Map.entry("itemId",1005L),Map.entry("orderType",index%3==0?"TEMPORARY":"LONG_TERM"),Map.entry("startDate",LocalDate.now().toString()),Map.entry("endDate",LocalDate.now().plusDays(index%3==0?0:7).toString()),Map.entry("intervalDays",index%4==0?2:1),Map.entry("times",List.of("08:00")),Map.entry("quantity","1"),Map.entry("dose","5"),Map.entry("doseUnit","mg"),Map.entry("route","口服"))));
    }
    signIn("admin");inpatient.generate(5,LocalDate.now());inpatient.generate(6,LocalDate.now());
    for(int index=0;index<orders.size();index++){
      var instances=db.queryForList("SELECT id FROM ip_order_execution WHERE order_id=?",orders.get(index));if(instances.isEmpty())continue;long execution=id(instances.getFirst().get("id"));
      if(index<8){signIn("inpatient");inpatient.action(execution,"confirm","已核对医嘱、患者押金和用药信息");}
      if(index<6){inpatient.action(execution,"pick","按病区患者核对摆药");}
      if(index<4){signIn("admin");inpatient.action(execution,"dispense","病区护士领药");}
    }
  }

  private void createProduction() {
    for(int index=0;index<5;index++) {
      signIn("preparation");long production=preparation.create(Map.of("formulaId",index%2==0?1001L:1002L,"locationId",7L,"quantity","40","batchNo","EX-PREP-"+LocalDate.now()+"-"+index,"expiresOn",LocalDate.now().plusYears(1).toString()));
      if(index==4)continue;
      signIn("quality");preparation.action(production,"approve",Map.of("reason","生产计划核对通过"));
      if(index==3)continue;
      signIn("preparation");preparation.action(production,"issue",Map.of());
      for(int step=1;step<=3;step++)preparation.action(production,"process",Map.of("stepNo",step,"name",List.of("原辅料称量","配制与混合","灌装与包装").get(step-1),"hours",step==2?"1.5":"0.5","outputQuantity","40","energyCost",String.valueOf(step*5)));
      if(index==2)continue;
      signIn("quality");preparation.action(production,"test",Map.of("conclusion","PASSED","standard","外观、装量与卫生学教学检验标准","measured","外观均匀，装量符合标准，检验结果合格"));
      signIn("preparation");preparation.action(production,"settle",Map.of("goodQuantity","38","laborCost","120","otherCost","15","abnormalLossCost","2"));
      long sale=preparation.sale(Map.of("locationId",7L,"itemId",6L,"customerName",index==0?"教学门诊制剂窗口":"教学协作单位","quantity","8","unitPrice","15"));
      signIn("quality");preparation.executeSale(sale);
    }
  }

  private void createPendingDocuments() {
    signIn("admin");
    long transfer=inventory.create(Map.of("type","TRANSFER","locationId",1L,"targetLocationId",3L,"reason",REASON+"：门诊药房补货","items",List.of(Map.of("itemId",1005L,"unitId",1L,"quantity","30"))));
    inventory.action(transfer,"submit",0,REASON);signIn("warehouse2");inventory.action(transfer,"approve",1,"已确认药房库存低限");inventory.action(transfer,"execute",2,"出库装箱已核对");
    for(int index=0;index<4;index++){signIn("admin");long doc=inventory.create(Map.of("type",index%2==0?"ISSUE":"LOSS","locationId",1L,"reason",index%2==0?"教学场景：病区临时申领":"教学场景：包装破损待处理","items",List.of(Map.of("itemId",1001L+index,"unitId",1L,"quantity","3"))));if(index<3)inventory.action(doc,"submit",0,REASON);if(index<2){signIn("warehouse2");inventory.action(doc,"approve",1,"数量及原因已核对");}}
    signIn("admin");long plan=IdWorker.getId();db.update("INSERT INTO pur_plan(id,number,location_id,supplier_id,status,created_by) VALUES (?,?,1,2,'SUBMITTED',1)",plan,"PO-EX-"+plan);for(long item:List.of(1001L,1004L,1009L))db.update("INSERT INTO pur_plan_item VALUES (?,?,?,?)",IdWorker.getId(),plan,item,120);
    long requisition=IdWorker.getId();db.update("INSERT INTO inv_requisition(id,number,location_id,supply_location_id,created_by) VALUES (?,?,3,1,4)",requisition,"REQ-EX-"+requisition);db.update("INSERT INTO inv_requisition_item VALUES (?,?,1004,100)",IdWorker.getId(),requisition);
  }

  private void dateDocument(long doc,LocalDate date) {
    db.update("UPDATE inv_document SET business_date=?,created_at=?,executed_at=? WHERE id=?",date,date.atTime(9,15),date.atTime(10,0),doc);
    db.update("UPDATE inv_ledger l JOIN inv_document_item x ON x.id=l.document_item_id SET l.business_date=?,l.occurred_at=? WHERE x.document_id=?",date,date.atTime(10,0),doc);
    db.update("UPDATE acc_voucher SET business_date=?,created_at=? WHERE source_type='INVENTORY' AND source_id=?",date,date.atTime(10,0),doc);
    db.update("UPDATE sys_document_action SET occurred_at=? WHERE source_type='INVENTORY' AND source_id=?",date.atTime(9,30),doc);
  }

  private void datePrescription(long rx,LocalDate date) {
    var prescription=db.queryForMap("SELECT * FROM op_prescription WHERE id=?",rx);long doc=id(prescription.get("inventory_document_id"));dateDocument(doc,date);
    db.update("UPDATE op_prescription SET created_at=? WHERE id=?",date.atTime(10,15),rx);
    db.update("UPDATE inv_document SET created_at=?,executed_at=? WHERE id=?",date.atTime(10,15),date.atTime(11,0),doc);
    db.update("UPDATE acc_voucher SET business_date=?,created_at=? WHERE source_type='PRESCRIPTION' AND source_id=?",date,date.atTime(11,0),rx);
    db.update("UPDATE cl_payment p JOIN cl_charge c ON c.id=p.charge_id SET p.paid_at=? WHERE c.source_type='PRESCRIPTION' AND c.source_id=?",date.atTime(10,40),rx);
    db.update("UPDATE acc_voucher v JOIN cl_payment p ON p.id=v.source_id JOIN cl_charge c ON c.id=p.charge_id SET v.business_date=?,v.created_at=? WHERE v.source_type='PAYMENT' AND c.source_type='PRESCRIPTION' AND c.source_id=?",date,date.atTime(10,40),rx);
    db.update("UPDATE sys_document_action SET occurred_at=? WHERE source_type='PRESCRIPTION' AND source_id=?",date.atTime(10,30),rx);
  }

  private void verifyBalances(){
    BigDecimal difference=db.queryForObject("SELECT COALESCE(SUM(debit-credit),0) FROM acc_voucher_entry",BigDecimal.class);
    int negative=db.queryForObject("SELECT COUNT(*) FROM inv_stock WHERE on_hand_qty<0 OR reserved_qty>on_hand_qty OR remaining_cost<0",Integer.class);
    int mismatched=db.queryForObject("SELECT COUNT(*) FROM inv_stock s LEFT JOIN (SELECT stock_id,SUM(qty_delta) qty,SUM(cost_delta) cost FROM inv_ledger GROUP BY stock_id) l ON l.stock_id=s.id WHERE s.on_hand_qty<>COALESCE(l.qty,0) OR s.remaining_cost<>COALESCE(l.cost,0)",Integer.class);
    if(difference.signum()!=0||negative!=0||mismatched!=0)throw new IllegalStateException("Expanded data balance checks failed; dataset rolled back.");
  }

  private void signIn(String username){var user=users.loadUserByUsername(username);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));}
}

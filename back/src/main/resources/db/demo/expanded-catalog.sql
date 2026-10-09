INSERT IGNORE INTO md_manufacturer(id,code,name,contact,enabled) VALUES
(2,'M002','华东示范制药有限公司','教学资料','1'),
(3,'M003','北方医药实验厂','教学资料','1'),
(4,'M004','海滨生物制品厂','教学资料','1'),
(5,'M005','仁和中药制剂中心','教学资料','1');
INSERT IGNORE INTO sys_user(id,username,password_hash,display_name,department_id,enabled)
SELECT 1001,'doctor2',password_hash,'许嘉宁',6,TRUE FROM sys_user WHERE username='doctor';
INSERT IGNORE INTO sys_user(id,username,password_hash,display_name,department_id,enabled)
SELECT 1002,'doctor3',password_hash,'顾文清',6,TRUE FROM sys_user WHERE username='doctor';
INSERT IGNORE INTO sys_user_role(user_id,role_id) VALUES (1001,7),(1002,7);
INSERT IGNORE INTO sys_user_scope(user_id,location_id) VALUES (1001,3),(1001,4),(1001,5),(1001,6),(1002,3),(1002,4),(1002,5),(1002,6);
INSERT IGNORE INTO md_supplier(id,code,name,contact,license_no,enabled) VALUES
(2,'S002','华东医药配送中心','教学资料','DEMO-002',1),
(3,'S003','北方医疗供应链','教学资料','DEMO-003',1),
(4,'S004','中药饮片配送公司','教学资料','DEMO-004',1);
INSERT IGNORE INTO md_item(id,code,name,specification,item_type,base_unit_id,quantity_scale,enabled) VALUES
(1001,'DR101','头孢呋辛酯片','0.25g×12片','DRUG',1,0,1),
(1002,'DR102','阿奇霉素片','0.25g×6片','DRUG',1,0,1),
(1003,'DR103','布洛芬缓释胶囊','0.3g×20粒','DRUG',1,0,1),
(1004,'DR104','左氧氟沙星片','0.5g×10片','DRUG',1,0,1),
(1005,'DR105','氨氯地平片','5mg×28片','DRUG',1,0,1),
(1006,'DR106','缬沙坦胶囊','80mg×28粒','DRUG',1,0,1),
(1007,'DR107','奥美拉唑肠溶胶囊','20mg×14粒','DRUG',1,0,1),
(1008,'DR108','蒙脱石散','3g×10袋','DRUG',1,0,1),
(1009,'DR109','盐酸氨溴索注射液','15mg/2ml×10支','DRUG',3,0,1),
(1010,'DR110','葡萄糖注射液','5% 500ml×20瓶','DRUG',4,0,1),
(1011,'DR111','乳酸钠林格注射液','500ml×20袋','DRUG',6,0,1),
(1012,'DR112','地塞米松磷酸钠注射液','5mg/1ml×10支','DRUG',3,0,1),
(1013,'DR113','氯雷他定片','10mg×12片','DRUG',1,0,1),
(1014,'DR114','复方甘草片','100片','DRUG',1,0,1),
(1015,'DR115','藿香正气口服液','10ml×10支','DRUG',3,0,1),
(1016,'DR116','连花清瘟胶囊','0.35g×24粒','DRUG',1,0,1),
(1017,'DR117','逍遥丸','200丸','DRUG',1,0,1),
(1018,'DR118','黄芪饮片','500g','DRUG',5,3,1),
(1019,'DR119','党参饮片','500g','DRUG',5,3,1),
(1020,'DR120','复方氨基酸注射液','250ml×30瓶','DRUG',4,0,1);
INSERT IGNORE INTO md_item_unit(id,item_id,unit_id,factor_to_base) SELECT id*10,id,base_unit_id,1 FROM md_item WHERE id BETWEEN 1001 AND 1020;
INSERT IGNORE INTO md_item_unit(id,item_id,unit_id,factor_to_base) VALUES
(10011,1001,2,12),(10021,1002,2,6),(10031,1003,2,20),(10041,1004,2,10),
(10051,1005,2,28),(10061,1006,2,28),(10071,1007,2,14),(10081,1008,2,10),
(10091,1009,2,10),(10101,1010,2,20),(10111,1011,2,20),(10121,1012,2,10),
(10131,1013,2,12),(10141,1014,2,100),(10151,1015,2,10),(10161,1016,2,24),
(10171,1017,2,200),(10181,1018,2,500),(10191,1019,2,500),(10201,1020,2,30);
INSERT IGNORE INTO md_drug(id,item_id,generic_name,trade_name,manufacturer_id,dosage_form,category,insurance_code,insurance_category,prescription_required,control_level,imported,in_house,self_pay,valuable,retail_price,price_version) VALUES
(1001,1001,'头孢呋辛酯片','示范头孢',2,'片剂','抗菌药','YB101','A',1,'NORMAL',0,0,0,0,1.20,1),
(1002,1002,'阿奇霉素片','示范阿奇',2,'片剂','抗菌药','YB102','B',1,'NORMAL',0,0,0,0,2.60,1),
(1003,1003,'布洛芬缓释胶囊','示范布洛芬',3,'胶囊','解热镇痛','YB103','A',0,'NORMAL',0,0,0,0,0.90,1),
(1004,1004,'左氧氟沙星片','示范左氧',2,'片剂','抗菌药','YB104','B',1,'NORMAL',0,0,0,0,1.80,1),
(1005,1005,'氨氯地平片','示范络活喜',3,'片剂','心血管','YB105','A',1,'NORMAL',0,0,0,0,0.55,1),
(1006,1006,'缬沙坦胶囊','示范缬沙坦',3,'胶囊','心血管','YB106','B',1,'NORMAL',0,0,0,0,0.85,1),
(1007,1007,'奥美拉唑肠溶胶囊','示范奥美',2,'胶囊','消化系统','YB107','A',1,'NORMAL',0,0,0,0,0.70,1),
(1008,1008,'蒙脱石散','示范蒙脱石',3,'散剂','消化系统','YB108','A',0,'NORMAL',0,0,0,0,1.10,1),
(1009,1009,'盐酸氨溴索注射液','示范氨溴索',4,'针剂','呼吸系统','YB109','B',1,'NORMAL',0,0,0,0,4.50,1),
(1010,1010,'葡萄糖注射液','示范葡萄糖',4,'输液','输液','YB110','A',0,'NORMAL',0,0,0,0,3.20,1),
(1011,1011,'乳酸钠林格注射液','示范林格',4,'输液','输液','YB111','A',0,'NORMAL',0,0,0,0,4.10,1),
(1012,1012,'地塞米松磷酸钠注射液','示范地塞米松',4,'针剂','激素','YB112','B',1,'NORMAL',0,0,0,0,2.80,1),
(1013,1013,'氯雷他定片','示范氯雷他定',3,'片剂','抗过敏','YB113','B',0,'NORMAL',0,0,0,0,0.45,1),
(1014,1014,'复方甘草片','示范甘草片',3,'片剂','呼吸系统','YB114','B',1,'NORMAL',0,0,0,0,0.30,1),
(1015,1015,'藿香正气口服液','示范藿香',5,'口服液','中成药','YB115','B',0,'NORMAL',0,0,0,0,1.50,1),
(1016,1016,'连花清瘟胶囊','示范连花清瘟',5,'胶囊','中成药','YB116','B',0,'NORMAL',0,0,0,0,1.80,1),
(1017,1017,'逍遥丸','示范逍遥丸',5,'丸剂','中成药','YB117','B',0,'NORMAL',0,0,0,0,0.70,1),
(1018,1018,'黄芪饮片','黄芪',5,'中药饮片','中药饮片','YB118','B',0,'NORMAL',0,0,1,0,0.12,1),
(1019,1019,'党参饮片','党参',5,'中药饮片','中药饮片','YB119','B',0,'NORMAL',0,0,1,0,0.18,1),
(1020,1020,'复方氨基酸注射液','示范氨基酸',4,'输液','营养支持','YB120','B',1,'NORMAL',1,0,0,0,12.00,1);
INSERT IGNORE INTO md_drug_alias(id,drug_id,alias) VALUES
(1001,1001,'头孢呋辛'),(1002,1002,'阿奇霉素'),(1003,1003,'芬必得'),(1004,1005,'络活喜'),(1005,1007,'洛赛克'),(1006,1008,'思密达'),(1007,1013,'开瑞坦'),(1008,1015,'藿香正气水'),(1009,1016,'连花胶囊'),(1010,1018,'北芪');
INSERT IGNORE INTO md_location_item(id,location_id,item_id,min_qty,target_qty,expiry_warn_days) SELECT 200000+id*10+location_id,location_id,id,20,120,90 FROM md_item CROSS JOIN (SELECT 1 location_id UNION ALL SELECT 3 UNION ALL SELECT 5) l WHERE md_item.id BETWEEN 1001 AND 1020;
INSERT IGNORE INTO cl_patient(id,patient_no,name,sex,birth_date,phone,weight_kg,allergy_notes,pregnancy_status) VALUES
(1001,'PT-DEMO-101','陈雨欣','FEMALE','1998-04-12','13800001001',52,'无已知药物过敏史','NO'),
(1002,'PT-DEMO-102','赵文博','MALE','1968-09-03','13800001002',76,'磺胺类过敏','UNKNOWN'),
(1003,'PT-DEMO-103','周淑兰','FEMALE','1959-12-22','13800001003',61,'无已知药物过敏史','NO'),
(1004,'PT-DEMO-104','孙浩然','MALE','2004-07-18','13800001004',68,'无已知药物过敏史','NO'),
(1005,'PT-DEMO-105','吴秀梅','FEMALE','1975-02-11','13800001005',64,'青霉素过敏','UNKNOWN'),
(1006,'PT-DEMO-106','郑凯','MALE','1988-11-29','13800001006',83,'无已知药物过敏史','NO'),
(1007,'PT-DEMO-107','冯桂英','FEMALE','1948-05-06','13800001007',55,'无已知药物过敏史','NO'),
(1008,'PT-DEMO-108','何俊杰','MALE','1991-01-26','13800001008',72,'无已知药物过敏史','NO'),
(1009,'PT-DEMO-109','蒋丽','FEMALE','1982-10-15','13800001009',59,'头孢类过敏','UNKNOWN'),
(1010,'PT-DEMO-110','沈志远','MALE','1972-06-30','13800001010',79,'无已知药物过敏史','NO'),
(1011,'PT-DEMO-111','韩雪','FEMALE','1995-03-08','13800001011',50,'无已知药物过敏史','NO'),
(1012,'PT-DEMO-112','马国强','MALE','1961-08-19','13800001012',70,'无已知药物过敏史','NO'),
(1013,'PT-DEMO-113','罗静','FEMALE','1987-12-02','13800001013',57,'无已知药物过敏史','NO'),
(1014,'PT-DEMO-114','胡伟','MALE','1979-09-27','13800001014',81,'无已知药物过敏史','NO'),
(1015,'PT-DEMO-115','卢芳','FEMALE','2000-05-24','13800001015',54,'无已知药物过敏史','NO'),
(1016,'PT-DEMO-116','谢建军','MALE','1955-01-10','13800001016',73,'无已知药物过敏史','NO'),
(1017,'PT-DEMO-117','白露','FEMALE','1993-07-04','13800001017',60,'无已知药物过敏史','NO'),
(1018,'PT-DEMO-118','顾明','MALE','1984-02-28','13800001018',77,'无已知药物过敏史','NO'),
(1019,'PT-DEMO-119','彭玉兰','FEMALE','1945-11-16','13800001019',49,'无已知药物过敏史','NO'),
(1020,'PT-DEMO-120','方磊','MALE','1997-10-09','13800001020',69,'无已知药物过敏史','NO'),
(1021,'PT-DEMO-121','林芳','FEMALE','1966-04-17','13800001021',63,'无已知药物过敏史','NO'),
(1022,'PT-DEMO-122','邱成','MALE','1989-06-06','13800001022',75,'无已知药物过敏史','NO'),
(1023,'PT-DEMO-123','袁媛','FEMALE','1977-08-31','13800001023',56,'无已知药物过敏史','NO'),
(1024,'PT-DEMO-124','丁一','MALE','2002-12-12','13800001024',66,'无已知药物过敏史','NO'),
(1025,'PT-DEMO-125','任秀珍','FEMALE','1952-03-19','13800001025',58,'无已知药物过敏史','NO'),
(1026,'PT-DEMO-126','石磊','MALE','1970-07-23','13800001026',84,'无已知药物过敏史','NO'),
(1027,'PT-DEMO-127','夏青','FEMALE','1990-09-14','13800001027',53,'无已知药物过敏史','NO'),
(1028,'PT-DEMO-128','熊涛','MALE','1980-05-02','13800001028',78,'无已知药物过敏史','NO'),
(1029,'PT-DEMO-129','田甜','FEMALE','1999-01-29','13800001029',51,'无已知药物过敏史','NO'),
(1030,'PT-DEMO-130','高峰','MALE','1963-10-21','13800001030',71,'无已知药物过敏史','NO');
INSERT IGNORE INTO cl_encounter(id,number,patient_id,type,department_id,doctor_id,status,admitted_at) SELECT 1000+id,CONCAT('EN-DEMO-',id),id,'OUTPATIENT',6,CASE WHEN id%2=0 THEN 1001 ELSE 1002 END,'ACTIVE',CURRENT_TIMESTAMP(3) FROM cl_patient WHERE id BETWEEN 1001 AND 1030;
INSERT IGNORE INTO cl_encounter_diagnosis(encounter_id,diagnosis_id) SELECT 1000+id,CASE WHEN id%3=0 THEN 1 WHEN id%3=1 THEN 2 ELSE 3 END FROM cl_patient WHERE id BETWEEN 1001 AND 1030;
INSERT IGNORE INTO cl_patient_allergy(id,patient_id,item_id,reaction) VALUES (1001,1002,1001,'教学过敏记录：药物皮疹'),(1002,1005,1,'青霉素类皮疹'),(1003,1009,1001,'头孢类皮疹');
INSERT IGNORE INTO ru_rule(id,code,type,name,item_id,peer_item_id,diagnosis_id,min_dose,max_dose,dose_unit,severity,message,source_note) VALUES
(1001,'DEMO-INTER-001','INTERACTION','教学组合用药审查示例',1001,1002,NULL,NULL,NULL,'mg','WARNING','教学规则命中：请复核两种抗菌药同时使用的原因，此规则不代表临床禁忌','课程演示'),
(1002,'DEMO-CONTRA-001','CONTRAINDICATION','高血压患者用药提示',1006,NULL,3,NULL,NULL,'mg','WARNING','请结合患者血压记录复核用药','课程演示'),
(1003,'DEMO-DOSE-002','DOSE','布洛芬日剂量范围',1003,NULL,NULL,200,1200,'mg','WARNING','日剂量超出演示范围','课程演示'),
(1004,'DEMO-IND-001','INDICATION','发热患者解热药适应症',1003,NULL,2,NULL,NULL,'mg','WARNING','请结合诊断和症状确认适应症','课程演示'),
(1005,'DEMO-DOSE-003','DOSE','儿童剂量体重审查',1003,NULL,NULL,5,30,'mg','WARNING','按体重核对单次剂量','课程演示');
INSERT IGNORE INTO ru_knowledge(id,item_id,title,content,source_note) VALUES
(1001,1001,'头孢呋辛酯片用药提示','按医嘱完成疗程，出现皮疹或呼吸不适时立即报告。本文为课程演示资料。','课程演示'),
(1002,1003,'布洛芬缓释胶囊用药提示','注意胃肠道反应，避免与同类解热镇痛药重复使用。','课程演示'),
(1003,1005,'氨氯地平片用药提示','建议固定时间服用，关注血压变化和下肢水肿。','课程演示'),
(1004,1008,'蒙脱石散用药提示','与其他口服药物间隔服用，按医嘱补充水分。','课程演示'),
(1005,1015,'藿香正气口服液用药提示','服用前查看成分和禁忌，酒精相关提示仅供教学演示。','课程演示');
UPDATE ru_rule SET per_kg=TRUE,dose_basis='SINGLE',max_age=12 WHERE id=1005;
INSERT IGNORE INTO cl_encounter(id,number,patient_id,type,department_id,doctor_id,ward_location_id,bed_no,status)
SELECT 3000+id,CONCAT('IP-DEMO-',id),id,'INPATIENT',6,10,8,CONCAT('A',LPAD(id-1000,2,'0')),'ACTIVE'
FROM cl_patient WHERE id BETWEEN 1001 AND 1010;
INSERT IGNORE INTO cl_encounter_diagnosis(encounter_id,diagnosis_id) SELECT 3000+id,3 FROM cl_patient WHERE id BETWEEN 1001 AND 1010;
INSERT IGNORE INTO cl_deposit_account(id,encounter_id) SELECT 3000+id,3000+id FROM cl_patient WHERE id BETWEEN 1001 AND 1010;
INSERT IGNORE INTO md_item(id,code,name,specification,item_type,base_unit_id,quantity_scale,enabled) VALUES
(1101,'MAT101','蔗糖原料','药用教学原料','RAW_MATERIAL',5,3,1),
(1102,'MAT102','甘草提取物','教学原料','RAW_MATERIAL',5,3,1),
(1103,'MAT103','药用乙醇','教学辅料','AUXILIARY',5,3,1),
(1104,'MAT104','制剂封口膜','100ml瓶用','PACKAGING',1,0,1),
(1105,'MAT105','制剂纸盒','单瓶装','PACKAGING',2,0,1);
INSERT IGNORE INTO md_item_unit(id,item_id,unit_id,factor_to_base) SELECT id*10,id,base_unit_id,1 FROM md_item WHERE id BETWEEN 1101 AND 1105;
INSERT IGNORE INTO prep_formula(id,code,name,output_item_id,standard_output_qty,standard_hours,standard_energy_cost,created_by) VALUES
(1001,'F-DEMO-101','清凉合剂小批量配方',6,100,4,35,8),
(1002,'F-DEMO-102','清凉合剂标准化包装配方',6,100,3,25,8);
INSERT IGNORE INTO prep_formula_item(id,formula_id,input_item_id,standard_base_qty) VALUES
(1001,1001,1101,100),(1002,1001,1102,40),(1003,1001,102,100),
(1004,1002,101,80),(1005,1002,102,100),(1006,1002,1104,100),(1007,1002,1105,100);

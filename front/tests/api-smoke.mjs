import { request } from '@playwright/test'
import assert from 'node:assert/strict'
const base='http://127.0.0.1:8080/api/v1/'
async function login(username){const context=await request.newContext({baseURL:base});let r=await context.get('auth/csrf');const token=(await r.json()).data.token;r=await context.post('auth/login',{form:{username,password:'123456'},headers:{'X-XSRF-TOKEN':token}});assert.equal(r.status(),200,await r.text());r=await context.get('auth/csrf');return {context,token:(await r.json()).data.token}}
async function get(s,path){const r=await s.context.get(path.replace(/^\//,''));assert.equal(r.status(),200,await r.text());return (await r.json()).data}
async function post(s,path,data={},expected=200,key=crypto.randomUUID()){const r=await s.context.post(path.replace(/^\//,''),{data,headers:{'X-XSRF-TOKEN':s.token,'Idempotency-Key':key}});assert.equal(r.status(),expected,`${path}: ${await r.text()}`);return (await r.json()).data}
const sessions=[]
try{
 const admin=await login('admin'),reviewer=await login('pharmacist'),dispenser=await login('pharmacist2'),warehouse=await login('warehouse2'),doctor=await login('doctor'),quality=await login('quality'),producer=await login('preparation');sessions.push(admin,reviewer,dispenser,warehouse,doctor,quality,producer)
 assert.equal((await get(admin,'/auth/me')).username,'admin')
 assert.ok((await get(admin,'/inventory/stocks?locationId=1')).length>0)
 await get(reviewer,'/inventory/stocks?locationId=1').then(()=>assert.fail('cross scope allowed'),()=>{})
 const rx=await post(doctor,'/outpatient/prescriptions',{encounterId:'1',locationId:'3',items:[{itemId:'2',unitId:'1',quantity:'6',dose:'500',doseUnit:'mg',frequencyPerDay:3,days:2,route:'口服'}]})
 await post(doctor,`/outpatient/prescriptions/${rx.id}/submit`,{version:0})
 await post(reviewer,`/outpatient/prescriptions/${rx.id}/price`,{version:1,reason:''})
 await post(admin,`/outpatient/prescriptions/${rx.id}/pay`,{version:2})
 await post(reviewer,`/outpatient/prescriptions/${rx.id}/verify`,{version:3,reason:''})
 await post(reviewer,`/outpatient/prescriptions/${rx.id}/dispense`,{version:4,reason:''},422)
 const before=await get(dispenser,'/inventory/stocks?locationId=3');const key=crypto.randomUUID()
 await post(dispenser,`/outpatient/prescriptions/${rx.id}/dispense`,{version:4,reason:''},200,key)
 await post(dispenser,`/outpatient/prescriptions/${rx.id}/dispense`,{version:4,reason:''},200,key)
 const after=await get(dispenser,'/inventory/stocks?locationId=3')
 const sum=(rows,item)=>rows.filter(r=>r.itemId===item).reduce((s,r)=>s+Number(r.on_hand_qty),0)
 assert.equal(sum(before,'2')-sum(after,'2'),6)
 const details=await get(reviewer,`/outpatient/prescriptions/${rx.id}`)
 const ret=await post(reviewer,`/outpatient/prescriptions/${rx.id}/returns`,{itemId:details.items[0].id,quantity:'2',reason:'API smoke test',qualityDisposition:'QUARANTINE'})
 await post(admin,`/outpatient/returns/${ret.id}/refund`)
 const transfer=await post(admin,'/inventory/documents',{type:'TRANSFER',locationId:'1',targetLocationId:'3',reason:'API smoke transfer',items:[{itemId:'1',unitId:'1',quantity:'10'}]})
 await post(admin,`/inventory/documents/${transfer.id}/submit`,{version:0})
 await post(warehouse,`/inventory/documents/${transfer.id}/approve`,{version:1})
 await post(warehouse,`/inventory/documents/${transfer.id}/execute`,{version:2})
 await post(reviewer,`/inventory/documents/${transfer.id}/receive`,{version:3})
 const raw=await get(producer,'/preparation/raw-quality?locationId=7')
 for(const stock of raw){const test=await post(producer,'/preparation/raw-tests',{stockId:stock.id,standard:'Teaching quality standard',measured:'Pass',conclusion:'PASSED'});await post(quality,`/preparation/raw-tests/${test.id}/confirm`)}
 const production=await post(producer,'/preparation/orders',{formulaId:'1',locationId:'7',quantity:'10',batchNo:'SMOKE-'+Date.now(),expiresOn:'2028-12-31'})
 await post(quality,`/preparation/orders/${production.id}/approve`)
 await post(producer,`/preparation/orders/${production.id}/issue`)
 const materials=await get(producer,`/preparation/orders/${production.id}/materials`)
 await post(producer,`/preparation/orders/${production.id}/material-returns`,{allocationId:materials[0].id,quantity:'1',reason:'unused material'})
 await post(producer,`/preparation/orders/${production.id}/process`,{stepNo:1,name:'Mix',hours:'1',outputQuantity:'10',energyCost:'2'})
 await post(quality,`/preparation/orders/${production.id}/test`,{standard:'Teaching quality standard',measured:'Pass',conclusion:'PASSED'})
 await post(producer,`/preparation/orders/${production.id}/settle`,{goodQuantity:'10',laborCost:'5',otherCost:'0',abnormalLossCost:'1'})
 await post(admin,'/clinical/encounters/3/deposits',{amount:'100'})
 const today=new Date().toLocaleDateString('en-CA')
 const order=await post(doctor,'/inpatient/orders',{encounterId:'3',locationId:'5',itemId:'2',orderType:'TEMPORARY',startDate:today,endDate:today,intervalDays:1,times:['08:00'],quantity:'1',dose:'500',doseUnit:'mg',route:'口服'})
 await post(admin,'/inpatient/executions/generate',{locationId:'5',date:today})
 const executions=await get(admin,`/inpatient/executions?locationId=5&date=${today}`);const ex=executions.find(e=>e.order_id===order.id)
 await post(admin,`/inpatient/executions/${ex.id}/confirm`,{reason:''});await post(admin,`/inpatient/executions/${ex.id}/pick`,{})
 const inpatient=await login('inpatient');sessions.push(inpatient);await post(inpatient,`/inpatient/executions/${ex.id}/dispense`,{})
 await post(inpatient,`/inpatient/executions/${ex.id}/returns`,{reason:'inpatient unused medication'})
 const voucher=await post(admin,'/accounting/vouchers',{description:'manual voucher acceptance',entries:[{accountId:'5',debit:'1.00',credit:'0'},{accountId:'7',debit:'0',credit:'1.00'}]});const accountant=await login('accountant');sessions.push(accountant);await post(accountant,`/accounting/vouchers/${voucher.id}/post`)
 const rec=await get(admin,'/accounting/reconciliation');assert.equal(Number(rec.voucherDifference),0);assert.equal(Number(rec.inventoryCost),Number(rec.inventoryLedgerCost))
 console.log(JSON.stringify({status:'PASS',checks:['login','scope','prescription','independent review','idempotent dispensing','return/refund','transfer','raw QC','material return','production costing/abnormal loss','inpatient return','manual voucher','inventory reconciliation','balanced vouchers']},null,2))
}finally{await Promise.all(sessions.map(s=>s.context.dispose()))}

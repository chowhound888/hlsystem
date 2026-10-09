import { chromium } from '@playwright/test'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
await fs.mkdir('../.runtime/screenshots',{recursive:true})
const browser=await chromium.launch({headless:true,channel:'msedge'})
try{
 const page=await browser.newPage({viewport:{width:1440,height:900}}),errors=[]
 page.on('pageerror',e=>errors.push(e.message))
 await page.goto('http://127.0.0.1:5173/login')
 await page.getByLabel('用户名').fill('admin');await page.getByLabel('密码',{exact:true}).fill('123456');await page.getByRole('button',{name:'登录',exact:true}).click()
 await page.waitForURL('**/workbench');await page.getByText('库存成本（元）',{exact:true}).waitFor()
 await page.screenshot({path:'../.runtime/screenshots/workbench-desktop.png',fullPage:true})
 for(const route of ['/master/drugs','/master/materials','/inventory/stocks','/inventory/documents','/inventory/operations','/inventory/requisitions','/inventory/transit','/warehouse/purchasing','/outpatient/prescriptions','/inpatient/orders','/inpatient/ward','/accounting/vouchers','/preparation/orders','/preparation/quality-sales','/rational/rules','/clinical/patients','/reports','/reports/analysis','/system/users','/system/templates']){
  await page.goto('http://127.0.0.1:5173'+route);await page.waitForLoadState('networkidle');await page.screenshot({path:'../.runtime/screenshots/'+route.replaceAll('/','-').slice(1)+'.png',fullPage:true});assert.ok(!(await page.locator('.el-message--error').count()),route+' error message')
 }
 await page.goto('http://127.0.0.1:5173/inventory/documents');await page.waitForLoadState('networkidle');await page.getByRole('button',{name:'详情',exact:true}).first().click();await page.getByRole('button',{name:'打印单据'}).waitFor();const popupPromise=page.waitForEvent('popup');await page.getByRole('button',{name:'打印单据'}).click();const popup=await popupPromise;await popup.getByText('审核人：__________').waitFor();await popup.screenshot({path:'../.runtime/screenshots/print-inventory.png',fullPage:true});await popup.pdf({path:'../.runtime/screenshots/print-inventory.pdf',format:'A4'});await popup.close()
 await page.setViewportSize({width:390,height:844});await page.goto('http://127.0.0.1:5173/workbench');await page.waitForLoadState('networkidle');await page.getByTitle('展开或折叠菜单').click();await page.screenshot({path:'../.runtime/screenshots/workbench-mobile.png',fullPage:true});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'mobile overflow')
 assert.deepEqual(errors,[])
 console.log('UI smoke PASS: desktop routes, mobile viewport, no console exceptions')
}finally{await browser.close()}

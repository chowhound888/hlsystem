# 接口约定与主要端点

## 1. 基础协议

REST JSON，前缀 `/api/v1`。HTTPS 部署时同源代理。登录采用服务端会话 Cookie，修改请求携带 CSRF Token；以用户及授权机构为服务端上下文。

主键字符串、金额/数量字符串、日期 ISO 格式。时间带时区偏移，例如 `2026-10-09T10:30:00+08:00`；业务日为 `2026-10-09`。客户端不得把 BIGINT 转成 Number，也不得把显示计算值作为最终确认金额。

统一成功及失败响应：

```json
{
  "code": "OK",
  "message": "操作成功",
  "data": {
    "id": "1234567890123456789",
    "status": "APPROVED",
    "version": 3
  },
  "traceId": "request-trace-id"
}
```

错误 `data` 可包含 `fieldErrors`、`blockingReasons`、`currentVersion` 和逐行失败信息。批量确认首先提供预校验接口；关键批量发药默认整单原子执行，不返回表面成功却暗中扣部分库存。

分页参数 `page=1&pageSize=20&sort=createdAt,desc`；白名单排序字段，上限 100。返回 `records`、`page`、`pageSize`、`total`。汇总在 `summary` 中表示全部筛选范围。日期参数明确包含起止业务日期；时间查询使用左闭右开范围。

所有金额由后端返回十进制字符串；前端只格式化显示。订单执行接口不接受客户端传来的余额、成本或重算后价格，使用已经批准的服务端快照。

## 2. HTTP 与错误码

| HTTP | 业务码示例 | 含义 |
| --- | --- | --- |
| 200/201 | OK | 查询、动作或创建成功 |
| 400 | VALIDATION_FAILED | 参数格式、单位或字段缺失 |
| 401 | UNAUTHENTICATED | 会话失效，返回登录 |
| 403 | FORBIDDEN / CSRF_INVALID | 无动作权限或 CSRF 验证失败 |
| 404 | NOT_FOUND | 对象不存在或不可见，避免泄露其他机构对象 |
| 409 | VERSION_CONFLICT / INVALID_STATE / DUPLICATE_OPERATION | 版本、状态或幂等请求冲突 |
| 409 | STOCK_INSUFFICIENT / STOCK_FROZEN / PERIOD_CLOSED | 库存不足、盘点冻结或已结期间 |
| 422 | DRUG_EXPIRED / QUALITY_NOT_RELEASED / RATIONAL_CHECK_BLOCKED | 业务规则阻断 |
| 422 | INDEPENDENT_REVIEW_REQUIRED / DEPOSIT_INSUFFICIENT | 双人规则或住院余额不足 |
| 422 | REFUND_LIMIT_EXCEEDED / PAYMENT_LIMIT_EXCEEDED | 超额退款或付款 |
| 500 | INTERNAL_ERROR | 技术故障，返回 traceId，不返回数据库细节 |

## 3. 动作与并发

创建 `POST /resources`，编辑草稿 `PUT /resources/{id}`，详情 `GET /resources/{id}`。变更请求携带 `version`。提交、审核、发药、退款等以动作端点表达，不用通用 `PATCH status=...` 绕过业务。

```text
POST /inventory/documents/{id}/submit
POST /inventory/documents/{id}/approve
POST /inventory/documents/{id}/reject
POST /inventory/documents/{id}/execute
POST /inventory/documents/{id}/cancel
POST /inventory/documents/{id}/reversals
```

执行、付款、退款、调价、结账必须携带 `Idempotency-Key`。同一用户和端点、同一键和相同内容返回相同结果；同键不同内容返回 409。首次执行与幂等成功结果在同一数据库事务持久化；锁冲突或失败可安全重试。已执行源事件的数据库唯一键不会随幂等记录清理失效。

同一源单同时发生退款、发药或修改时锁住源单，确保不能一边退款一边发药。幂等结果只返回给原操作者并重新验证权限。

## 4. 接口分组

| 模块 | 主要接口 |
| --- | --- |
| 会话 | `GET /auth/csrf`、`POST /auth/login`、`POST /auth/logout`、`GET /auth/me` |
| 系统 | `/system/users`、`roles`、`permissions`、`departments`、`locations`、`configs`、`print-templates`、`audit-logs` |
| 基础 | `/master/drugs`、`items`、`units`、`suppliers`、`manufacturers`、`diagnoses`、`location-items` |
| 药品扩展 | `/master/drugs/{id}/aliases`、`ingredients`、`prices`、`GET /master/drugs/search` |
| 患者 | `/clinical/patients`、`/clinical/patients/{id}/allergies` |
| 就诊 | `/clinical/encounters`、`/clinical/encounters/{id}/diagnoses`、`admit`、`transfer`、`discharge` |
| 收费押金 | `/clinical/charges`、`payments`、`refunds`、`/clinical/encounters/{id}/deposit-account`、`deposit-payments` |
| 采购 | `/purchasing/suggestions`、`plans`、`orders`、`/purchasing/orders/{id}/receipts` |
| 库存 | `/inventory/stocks`、`ledger`、`alerts`、`documents`、`stocktakes`、`closings` |
| 领药 | `/inventory/requisitions`、`/inventory/requisitions/{id}/fulfillments` |
| 调拨 | `/inventory/transfers`、`/{id}/ship`、`/{id}/receipts`、`/{id}/returns`、`/{id}/transit-losses` |
| 门诊 | `/outpatient/prescriptions`、`/{id}/check`、`price`、`verify`、`dispense`、`cancel`、`/outpatient/returns` |
| 住院 | `/inpatient/orders`、`/{id}/check`、`stop`、`/inpatient/executions`、`/{id}/confirm`、`cancel` |
| 摆药 | `/inpatient/picking-lists`、`/{id}/verify`、`/{id}/dispense` |
| 基数药 | `/inpatient/ward-quotas`、`ward-usage`、`ward-replenishments` |
| 调价 | `/pricing/policies`、`adjustments`、`/pricing/adjustments/{id}/preview`、`approve`、`confirm` |
| 会计 | `/accounting/vouchers`、`/{id}/post`、`/{id}/reversals`、`payables`、`supplier-payments`、`reconciliations`、`periods` |
| 制剂 | `/preparation/formulas`、`/{id}/versions`、`production-orders`、`quality-tests`、`sales-orders`、`sales-payments` |
| 生产动作 | `/preparation/production-orders/{id}/material-issues`、`material-returns`、`process-records`、`outputs`、`cost-preview`、`cost-settlement` |
| 用药 | `/rational/rules`、`/{id}/versions`、`knowledge`、`checks`、`/rational/check-results/{id}/overrides` |
| 报表 | `/reports/inventory`、`consumption`、`prescriptions`、`price-changes`、`reconciliation`、`preparation-cost`、`department-consumption` |
| 打印导出 | `GET /print/{documentType}/{id}`、`GET /reports/{reportType}/export` |
| 导入附件 | `POST /imports/{type}/preview`、`POST /imports/{previewId}/confirm`、`POST /attachments`、`GET /attachments/{id}/content` |

模块接口支持所需提交、审批和执行动作，最终 OpenAPI 随模块开发补齐参数、权限及示例。上表给出业务资源边界，不代表规划阶段已实现接口。

## 5. 典型请求

调拨创建请求保存单据数量，由后端转换并预分配批次：

```json
{
  "sourceLocationId": "1001",
  "targetLocationId": "2001",
  "reason": "门诊药房补货",
  "items": [
    { "itemId": "3001", "unitId": "4001", "quantity": "10" }
  ]
}
```

执行请求只携带版本与确认信息：

```json
{
  "version": 4,
  "reason": "按已审核批次发货"
}
```

不足响应示例：

```json
{
  "code": "STOCK_INSUFFICIENT",
  "message": "可用库存不足",
  "data": {
    "blockingReasons": [
      {
        "itemId": "3001",
        "requiredBaseQty": "240",
        "availableBaseQty": "180",
        "baseUnitName": "片"
      }
    ]
  },
  "traceId": "request-trace-id"
}
```

## 6. 文件与集成边界

打印返回已授权的结构化数据、快照和模板版本，前端渲染打印页；不能让打印接口绕过详情权限。Excel 导出返回文件流，同时符合查询过滤和机构范围。

Excel 导入先预览，显示重复、未知别名、单位冲突和逐行错误；人工确认后入库数据。批号、效期与成本不明时不能直接导入有效库存。外部处方、医嘱、收费接口未来采用来源系统和来源业务 ID 进行幂等映射，课程第一版使用本地资源端点。

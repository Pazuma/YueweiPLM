# 2026-07-22 PLM供应商管理接入Inventory供应侧后端代码实现沉淀

## 引用文档

- `D:\work\资料\PLM\docs\README.md`
- `D:\work\资料\PLM\docs\01-开发框架总纲.md`
- `D:\work\资料\PLM\docs\02-系统架构设计.md`
- `D:\work\资料\PLM\docs\04-AI开发规范.md`
- `D:\work\资料\PLM\docs\05-数据模型与编码规范.md`
- `D:\work\资料\PLM\docs\07-权限与审批流规范.md`
- `D:\work\资料\PLM\docs\08-测试验收规范.md`
- `D:\work\资料\PLM\docs\modules\04-BOM管理.md`
- `D:\work\资料\PLM\docs\modules\07-模具治具管理.md`
- `D:\work\资料\PLM\docs\modules\08-成本与报价管理.md`

## 业务边界

供应商管理页面作为采购视角的聚合看板存在，但供应商本期不是系统七大核心对象，也不创建独立 Supplier 根对象。供应商资料来源于 `Inventory` 的供应侧字段和 `plm_inventory_supplier_item` 子表。

## 本次实现

- 新增后端接口：`GET /api/v1/inventories/suppliers/snapshot`
- 新增后端接口：`POST /api/v1/inventories/suppliers`
- 新增后端接口：`PUT /api/v1/inventories/suppliers/{supplierCode}`
- 新增后端模块：`com.yuewei.plm.module.inventory`
- 前端 `src/api/modules/supplier.ts` 从 `notConnected` 改为调用真实接口。
- 前端供应商页面的新增/编辑动作改为调用 `Inventory` 供应侧资料接口，避免前端假写入。

## 数据来源

- `plm_inventory.supplier_name`
- `plm_inventory_supplier_item.supplier_code`
- `plm_inventory.inventory_type`
- `plm_inventory.unit_cost`
- `plm_inventory.currency_code`
- `plm_inventory.lead_time_days`
- `plm_product.product_code/product_name`
- `plm_inventory_supplier_item.supplier_name`
- `plm_inventory_supplier_item.supplier_contact_person`
- `plm_inventory_supplier_item.supplier_contact_phone`
- `plm_inventory_supplier_item.supplier_contact_email`
- `plm_inventory_supplier_item.supplier_region`
- `plm_inventory_supplier_item.supply_categories`
- `plm_inventory_supplier_item.payment_term`
- `plm_inventory_supplier_item.cooperation_level`
- `plm_inventory_supplier_item.delivery_risk`

当 `plm_inventory_supplier_item` 不存在时，接口会降级只读取 `plm_inventory.supplier_name`。

## 新增/维护规则

- 新增供应商时创建一条 `plm_inventory` 供给侧资料记录，`inventory_code` 使用 `INV-SUP-*`，`inventory_type` 按首个供应品类映射到 material / packaging / tooling / fixture 等 Inventory 类型。
- 若 `plm_inventory_supplier_item` 存在，同步写入供应商编码、联系人、电话、区域、付款条件、合作等级、交期风险等供应侧扩展字段。
- 供应商状态不新增独立状态机：页面的 `draft / active / inactive` 会映射到 Inventory 的 `draft / available / closed`，供应侧子表保持 `draft / active / inactive`。
- 操作日志动作：`INVENTORY_SUPPLIER_CREATE`、`INVENTORY_SUPPLIER_UPDATE`，业务对象类型仍为 `INVENTORY`。

## 验收点

- `/suppliers` 页面不再出现“未接入后端”的提示。
- 供应商列表、详情、供应记录来自后端 `Inventory` 聚合。
- 不新增 Supplier 根表、根实体或独立生命周期状态机。
- 新增/编辑不会产生前端本地假数据，会调用 Inventory 供应侧接口并刷新后端快照。

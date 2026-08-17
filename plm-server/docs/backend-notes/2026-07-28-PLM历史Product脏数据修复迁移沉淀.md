# 2026-07-28 PLM 历史 Product 脏数据修复迁移沉淀

## 1. 背景
历史 `product_import.xlsx` 里的种子数据存在两类脏数据：
- 8 行本应是 `product_line` 的基础产品，被导成了 `model_variant`
- 后续颜色版本行缺少 `parent_product_id`，导致项目管理里的归档 SKU 只能计数，不能挂到父产品下展示

## 2. 修复策略
新增一条 Flyway 迁移，只修复 `seed` 导入批次的历史脏数据，不扫全表：
- `remark` 以 `seed基础产品%` 开头且 `color` 为空的记录，修正为 `product_line`
- `remark` 带 `base_product=...` 的记录，按备注里的基础产品编码回填 `parent_product_id`
- 同步把归档/发布状态的 `current_step_no` 回填到终点步数

## 3. 迁移文件
- `plm-server/src/main/resources/db/migration/V20260728_1105__repair_seed_product_import_data.sql`

## 4. 目标效果
- 旧的种子导入数据重新满足 Product 父子结构
- 项目管理里的已归档 Product / SKU 能正常进入列表和卡片展示
- 后续再导入新的已有产品数据时，不会被旧脏数据误导

## 5. 注意事项
- 这条迁移只针对历史 seed 数据
- 如果数据库里已经有人手工改过同类数据，迁移会按备注和空色字段做最小范围修复
- 如果你要导入新的产品数据，建议先重启后端让 Flyway 跑完这条迁移，再重新提交导入

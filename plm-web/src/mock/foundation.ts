import { mockResolve } from '@/api/request'
import type {
  BomCenterRow,
  FileSection,
  FoundationProductRef,
  InventoryListRow,
  InventoryTreeNode,
  ProductDetailPresentation,
  ReportCenterSnapshot,
  TestCategoryItem,
  TestRecordItem
} from '@/types/foundation'

const foundationProducts: FoundationProductRef[] = [
  {
    productId: 101,
    productCode: 'PRD-CD30-001',
    productName: '超队 3.0 磁吸手机壳',
    seriesName: '超队 3.0',
    model: 'iPhone 17',
    color: '曜石黑',
    customerName: '北美渠道 A',
    productType: 'product_line',
    versionNo: 'A.3',
    status: 'reviewing',
    currentStage: '红样测试',
    estimatedCost: 35,
    actualCost: 32.3,
    lastActiveAt: '2026-06-08',
    createdAt: '2026-05-20'
  },
  {
    productId: 102,
    productCode: 'PRD-CD30-IP18-BLK',
    productName: '超队 3.0 iPhone18 黑色',
    seriesName: '超队 3.0',
    model: 'iPhone 18',
    color: '黑色',
    customerName: '北美渠道 A',
    productType: 'model_variant',
    parentProductId: 101,
    versionNo: 'A.2',
    status: 'reviewing',
    currentStage: '差异测试验证',
    estimatedCost: 0,
    actualCost: 12.3,
    lastActiveAt: '2026-06-09',
    createdAt: '2026-06-02'
  },
  {
    productId: 103,
    productCode: 'PRD-CD30-IP18-BLU',
    productName: '超队 3.0 iPhone18 蓝色',
    seriesName: '超队 3.0',
    model: 'iPhone 18',
    color: '深海蓝',
    customerName: '北美渠道 A',
    productType: 'model_variant',
    parentProductId: 101,
    versionNo: 'A.1',
    status: 'developing',
    currentStage: '样品确认',
    estimatedCost: 0,
    actualCost: 11.8,
    lastActiveAt: '2026-06-06',
    createdAt: '2026-06-03'
  },
  {
    productId: 104,
    productCode: 'PRD-LJ30-001',
    productName: '亮甲 3.0 镜面手机壳',
    seriesName: '亮甲 3.0',
    model: 'iPhone 18',
    color: '樱花粉',
    customerName: '日本零售客户',
    productType: 'product_line',
    versionNo: 'B.1',
    status: 'released',
    currentStage: '正式发布',
    estimatedCost: 36.2,
    actualCost: 33.5,
    lastActiveAt: '2026-05-29',
    createdAt: '2026-04-18'
  },
  {
    productId: 105,
    productCode: 'PRD-LJ30-IP18-GLD',
    productName: '亮甲 3.0 iPhone18 金色',
    seriesName: '亮甲 3.0',
    model: 'iPhone 18',
    color: '香槟金',
    customerName: '日本零售客户',
    productType: 'model_variant',
    parentProductId: 104,
    versionNo: 'A.1',
    status: 'released',
    currentStage: '正式发布',
    estimatedCost: 0,
    actualCost: 13.1,
    lastActiveAt: '2026-05-18',
    createdAt: '2026-05-06'
  }
]

const productDetailPresentationMap: Record<number, ProductDetailPresentation> = {
  101: {
    productId: 101,
    title: '超队 3.0 磁吸手机壳',
    flowLabel: '新产品线',
    currentNode: '红样测试',
    nextNode: '整理生产资料',
    summary: '按完整新产品线流程推进，当前重点是红样测试收口、BOM 版本确认和生产资料冻结准备。',
    costPanel: {
      showEstimated: true,
      estimatedTotal: 35,
      estimatedLines: [
        { label: '材料成本', amount: 12.8, note: 'TPU 主材、PC 背板、磁吸组件' },
        { label: '模具成本', amount: 9.6, note: '注塑模与修模预估分摊' },
        { label: '工艺加工', amount: 6.1, note: '喷油、贴磁、组装与全检' },
        { label: '包装测试', amount: 3.2, note: '彩盒、标签、跌落与耐磨测试' },
        { label: '损耗预估', amount: 3.3, note: '试产损耗与波动缓冲' }
      ],
      actualTotal: 32.3,
      actualLines: [
        { label: '材料成本', amount: 12.1, note: '主材锁价后低于立项预估' },
        { label: '模具成本', amount: 8.4, note: '试模轮次控制在两轮内' },
        { label: '工艺加工', amount: 5.7, note: '量产前工艺参数已稳定' },
        { label: '包装测试', amount: 3.0, note: '包装版本确认后未追加返工' },
        { label: '研发验证', amount: 3.1, note: '红样与小批验证投入' }
      ]
    },
    bomCompareRows: [
      { versionNo: 'A.1', statusLabel: '已归档', materialCost: 27.8, processCost: 6.3, totalCost: 34.1, delta: 0 },
      { versionNo: 'A.2', statusLabel: '已归档', materialCost: 28.6, processCost: 6.8, totalCost: 35.4, delta: 1.3 },
      { versionNo: 'A.3', statusLabel: '当前', materialCost: 26.9, processCost: 5.4, totalCost: 32.3, delta: -3.1 }
    ],
    bomItems: [
      { inventoryCode: 'INV-MAT-023', inventoryName: 'TPU 原料 85A', quantity: 1.2, stockUom: 'kg', supplierName: '东莞塑胶 A', unitCost: 25.5 },
      { inventoryCode: 'INV-MAG-045', inventoryName: 'N52 磁吸组件', quantity: 1, stockUom: 'set', supplierName: '惠州材料 C', unitCost: 3.6 },
      { inventoryCode: 'INV-PAK-102', inventoryName: '渠道彩盒', quantity: 1, stockUom: 'pcs', supplierName: '深圳包材 D', unitCost: 1.4 }
    ],
    materialCategories: [
      {
        categoryKey: 'raw',
        categoryName: '原材料',
        items: [
          { itemCode: 'INV-MAT-023', itemName: 'TPU 原料 85A', spec: '25kg / 袋', supplierName: '东莞塑胶 A', note: '主体包胶' },
          { itemCode: 'INV-MAT-045', itemName: 'PC 背板', spec: '0.8mm', supplierName: '深圳板材 B', note: '背板支撑' }
        ]
      },
      {
        categoryKey: 'component',
        categoryName: '功能件',
        items: [
          { itemCode: 'INV-MAG-045', itemName: 'N52 磁吸组件', spec: '1 set', supplierName: '惠州材料 C', note: '磁吸功能核心件' },
          { itemCode: 'INV-DEC-010', itemName: '装饰环', spec: '黑钛色', supplierName: '深圳五金 E', note: '提升外观层次' }
        ]
      },
      {
        categoryKey: 'package',
        categoryName: '包材',
        items: [
          { itemCode: 'INV-PAK-102', itemName: '渠道彩盒', spec: '单盒', supplierName: '深圳包材 D', note: '北美渠道版' },
          { itemCode: 'INV-PAK-118', itemName: '标签贴纸', spec: '黑白标签', supplierName: '深圳包材 D', note: '版本条码' }
        ]
      },
      {
        categoryKey: 'tooling',
        categoryName: '模具治具',
        items: [
          { itemCode: 'INV-MOLD-201', itemName: '超队 3.0 注塑模', spec: '1 套', supplierName: '东莞模具 C', note: '试模完成待最终验收' },
          { itemCode: 'INV-JIG-005', itemName: '热压治具', spec: '1 套', supplierName: '东莞模具 C', note: '用于贴磁定位' }
        ]
      }
    ],
    suppliers: [
      { supplierName: '东莞塑胶 A', role: '主材供应', statusLabel: '已锁价', note: 'TPU 主材稳定供货' },
      { supplierName: '惠州材料 C', role: '磁吸组件', statusLabel: '待备选', note: '需要补二供风险预案' },
      { supplierName: '深圳包材 D', role: '彩盒 / 标签', statusLabel: '已确认', note: '渠道版包装已定稿' }
    ],
    documents: [
      { fileName: 'PRD-CD30 结构图纸 V3.pdf', category: '工程图纸', versionNo: 'V3', updatedAt: '2026-06-01' },
      { fileName: 'PRD-CD30 红样测试计划.xlsx', category: '测试资料', versionNo: 'A.1', updatedAt: '2026-06-05' },
      { fileName: 'PRD-CD30 注塑 SOP V1.pdf', category: '生产资料', versionNo: 'V1', updatedAt: '2026-06-08' }
    ],
    qualityRecords: [
      { testItem: '跌落测试', resultLabel: '通过', owner: '品质部', testedAt: '2026-06-05', note: '边角无裂纹' },
      { testItem: '酒精测试', resultLabel: '复测中', owner: '品质部', testedAt: '2026-06-08', note: 'LOGO 区域掉漆，已调喷油参数' },
      { testItem: '磁吸力测试', resultLabel: '进行中', owner: '工程 / 品质', testedAt: '2026-06-09', note: '验证 N52 稳定性' }
    ]
  },
  102: {
    productId: 102,
    title: '超队 3.0 iPhone18 黑色',
    flowLabel: '新型号线',
    currentNode: '差异测试验证',
    nextNode: '生产资料整理',
    summary: '该版本继承父产品的大部分 BOM、工艺与测试框架，当前只管理机型孔位、黑色色母与差异测试结果。',
    costPanel: {
      showEstimated: false,
      actualTotal: 12.3,
      actualLines: [
        { label: '改模成本', amount: 4.2, note: 'iPhone18 孔位与摄像头位置修模' },
        { label: '差异材料', amount: 3.6, note: '黑色 TPU 色母与标签差异件' },
        { label: '差异工艺', amount: 2.1, note: '注塑参数调整与试产确认' },
        { label: '差异测试', amount: 1.4, note: '孔位匹配、按键手感、外观验证' },
        { label: '资料整理', amount: 1.0, note: 'SIP / SOP 增量修改' }
      ]
    },
    bomCompareRows: [
      { versionNo: 'A.1', statusLabel: '已归档', materialCost: 8.9, processCost: 3.1, totalCost: 12.0, delta: 0 },
      { versionNo: 'A.2', statusLabel: '当前', materialCost: 9.2, processCost: 3.1, totalCost: 12.3, delta: 0.3 }
    ],
    bomItems: [
      { inventoryCode: 'INV-MAT-067', inventoryName: '黑色色母', quantity: 0.1, stockUom: 'kg', supplierName: '东莞塑胶 A', unitCost: 18.2 },
      { inventoryCode: 'INV-ACC-118', inventoryName: 'iPhone18 孔位治具', quantity: 1, stockUom: 'set', supplierName: '东莞模具 C', unitCost: 1.8 },
      { inventoryCode: 'INV-PAK-156', inventoryName: '黑色标签贴纸', quantity: 1, stockUom: 'pcs', supplierName: '深圳包材 D', unitCost: 0.6 }
    ],
    materialCategories: [
      {
        categoryKey: 'raw',
        categoryName: '原材料',
        items: [
          { itemCode: 'INV-MAT-067', itemName: '黑色色母', spec: '5kg / 箱', supplierName: '东莞塑胶 A', note: '仅此版本新增' },
          { itemCode: 'INV-MAT-023', itemName: 'TPU 原料 85A', spec: '25kg / 袋', supplierName: '东莞塑胶 A', note: '沿用父产品' }
        ]
      },
      {
        categoryKey: 'component',
        categoryName: '功能件',
        items: [
          { itemCode: 'INV-MAG-045', itemName: 'N52 磁吸组件', spec: '1 set', supplierName: '惠州材料 C', note: '沿用父产品' },
          { itemCode: 'INV-ACC-118', itemName: 'iPhone18 孔位治具', spec: '1 set', supplierName: '东莞模具 C', note: '用于差异验证' }
        ]
      },
      {
        categoryKey: 'package',
        categoryName: '包材',
        items: [
          { itemCode: 'INV-PAK-156', itemName: '黑色标签贴纸', spec: '单枚', supplierName: '深圳包材 D', note: '版本差异标签' }
        ]
      },
      {
        categoryKey: 'tooling',
        categoryName: '模具治具',
        items: [
          { itemCode: 'INV-MOLD-201', itemName: '父产品注塑模', spec: '沿用', supplierName: '东莞模具 C', note: '本次仅改模' }
        ]
      }
    ],
    suppliers: [
      { supplierName: '东莞塑胶 A', role: '色母 / TPU', statusLabel: '待确认', note: '黑色色母报价已回签，需补批次验证' },
      { supplierName: '东莞模具 C', role: '改模 / 治具', statusLabel: '进行中', note: '差异孔位改模中' },
      { supplierName: '深圳包材 D', role: '标签贴纸', statusLabel: '已确认', note: '渠道差异标签已定稿' }
    ],
    documents: [
      { fileName: 'PRD-CD30-IP18 孔位差异图.pdf', category: '差异图纸', versionNo: 'A.2', updatedAt: '2026-06-03' },
      { fileName: 'PRD-CD30-IP18 黑色外观确认.pdf', category: '客户确认件', versionNo: 'A.1', updatedAt: '2026-06-06' },
      { fileName: 'PRD-CD30-IP18 差异测试记录.xlsx', category: '测试资料', versionNo: 'A.1', updatedAt: '2026-06-09' }
    ],
    qualityRecords: [
      { testItem: '孔位匹配', resultLabel: '通过', owner: '工程部', testedAt: '2026-06-08', note: '摄像头与按键位置正常' },
      { testItem: '外观确认', resultLabel: '通过', owner: '项目部', testedAt: '2026-06-09', note: '客户确认黑色色差可接受' },
      { testItem: '磁吸力', resultLabel: '不通过', owner: '品质部', testedAt: '2026-06-09', note: '吸力偏弱，需继续验证' }
    ]
  },
  104: {
    productId: 104,
    title: '亮甲 3.0 镜面手机壳',
    flowLabel: '新产品线',
    currentNode: '正式发布',
    nextNode: '历史追溯',
    summary: '该产品线已完成发布，当前页面用于复盘 BOM 成本版本、冻结资料与供应商复用情况。',
    costPanel: {
      showEstimated: true,
      estimatedTotal: 36.2,
      estimatedLines: [
        { label: '材料成本', amount: 14.1, note: '镜面片、TPU、装饰件' },
        { label: '模具成本', amount: 8.8, note: '贴合相关治具与模具' },
        { label: '工艺加工', amount: 6.9, note: '贴合、喷涂与全检' },
        { label: '包装测试', amount: 3.1, note: '零售包装与渠道测试' },
        { label: '损耗预估', amount: 3.3, note: '良率与返工缓冲' }
      ],
      actualTotal: 33.5,
      actualLines: [
        { label: '材料成本', amount: 13.6, note: '镜面片降价后回落' },
        { label: '模具成本', amount: 7.9, note: '模具稳定后返修成本下降' },
        { label: '工艺加工', amount: 6.1, note: '贴合参数收敛' },
        { label: '包装测试', amount: 2.9, note: '资料一次性通过' },
        { label: '研发验证', amount: 3.0, note: '黄样与 MX 验证投入' }
      ]
    },
    bomCompareRows: [
      { versionNo: 'A.4', statusLabel: '已归档', materialCost: 28.8, processCost: 6.0, totalCost: 34.8, delta: 0 },
      { versionNo: 'B.1', statusLabel: '当前', materialCost: 27.4, processCost: 6.1, totalCost: 33.5, delta: -1.3 }
    ],
    bomItems: [
      { inventoryCode: 'INV-MAT-081', inventoryName: '镜面片', quantity: 1, stockUom: 'pcs', supplierName: '深圳板材 B', unitCost: 6.8 },
      { inventoryCode: 'INV-MAT-024', inventoryName: 'TPU 原料 90A', quantity: 1.1, stockUom: 'kg', supplierName: '东莞塑胶 A', unitCost: 24.6 },
      { inventoryCode: 'INV-PAK-188', inventoryName: '零售吊卡包装', quantity: 1, stockUom: 'pcs', supplierName: '深圳包材 D', unitCost: 1.9 }
    ],
    materialCategories: [
      {
        categoryKey: 'raw',
        categoryName: '原材料',
        items: [
          { itemCode: 'INV-MAT-081', itemName: '镜面片', spec: '高亮镜面', supplierName: '深圳板材 B', note: '核心外观件' },
          { itemCode: 'INV-MAT-024', itemName: 'TPU 原料 90A', spec: '25kg / 袋', supplierName: '东莞塑胶 A', note: '主体包胶' }
        ]
      },
      {
        categoryKey: 'component',
        categoryName: '功能件',
        items: [
          { itemCode: 'INV-DEC-022', itemName: '装饰边环', spec: '香槟金', supplierName: '深圳五金 E', note: '提升外观质感' }
        ]
      },
      {
        categoryKey: 'package',
        categoryName: '包材',
        items: [
          { itemCode: 'INV-PAK-188', itemName: '零售吊卡包装', spec: '单套', supplierName: '深圳包材 D', note: '零售渠道' }
        ]
      },
      {
        categoryKey: 'tooling',
        categoryName: '模具治具',
        items: [
          { itemCode: 'INV-JIG-011', itemName: '镜面贴合治具', spec: '1 套', supplierName: '东莞模具 C', note: '贴合工艺专用' }
        ]
      }
    ],
    suppliers: [
      { supplierName: '深圳板材 B', role: '镜面外观件', statusLabel: '稳定合作', note: '镜面片已转量产供应' },
      { supplierName: '东莞塑胶 A', role: 'TPU 主材', statusLabel: '稳定合作', note: '月结 45 天' },
      { supplierName: '深圳包材 D', role: '零售包装', statusLabel: '已冻结', note: '包装版本可直接复用' }
    ],
    documents: [
      { fileName: 'PRD-LJ30 外观确认稿 V2.pdf', category: '设计稿件', versionNo: 'V2', updatedAt: '2026-05-10' },
      { fileName: 'PRD-LJ30 包装结构图 V3.pdf', category: '包装资料', versionNo: 'V3', updatedAt: '2026-05-21' },
      { fileName: 'PRD-LJ30 黄样确认记录.pdf', category: '样品资料', versionNo: 'B.1', updatedAt: '2026-05-25' }
    ],
    qualityRecords: [
      { testItem: '百格测试', resultLabel: '通过', owner: '品质部', testedAt: '2026-05-21', note: '镜面层附着力合格' },
      { testItem: '跌落测试', resultLabel: '通过', owner: '品质部', testedAt: '2026-05-20', note: '边框无开裂' },
      { testItem: 'MX 小批验证', resultLabel: '通过', owner: '项目部', testedAt: '2026-05-28', note: '墨西哥端产线已跑通' }
    ]
  }
}

const fileSections: FileSection[] = [
  {
    key: 'product_files',
    title: '产品文件',
    description: '新产品线的图纸、SOP、测试计划和量产资料，按产品线归档。',
    groups: [
      {
        groupId: 'product-101',
        projectName: '超队 3.0',
        productCode: 'PRD-CD30-001',
        productId: 101,
        owner: '张敏',
        updatedAt: '2026-06-08',
        files: [
          { fileId: 'f-101-1', fileName: 'PRD-CD30 结构图纸 V3.pdf', category: '工程图纸', owner: '工程部', uploadedAt: '2026-06-01', versionNo: 'V3', productId: 101 },
          { fileId: 'f-101-2', fileName: 'PRD-CD30 红样测试计划.xlsx', category: '测试资料', owner: '品质部', uploadedAt: '2026-06-05', versionNo: 'A.1', productId: 101 },
          { fileId: 'f-101-3', fileName: 'PRD-CD30 注塑 SOP V1.pdf', category: '生产资料', owner: '工程部', uploadedAt: '2026-06-08', versionNo: 'V1', productId: 101 }
        ]
      }
    ]
  },
  {
    key: 'variant_files',
    title: '新型号文件',
    description: '新型号线只保留差异图纸、差异 BOM、客户确认件和差异测试记录。',
    groups: [
      {
        groupId: 'variant-102',
        projectName: '超队 3.0 iPhone18 黑色',
        productCode: 'PRD-CD30-IP18-BLK',
        productId: 102,
        owner: '刘浩',
        updatedAt: '2026-06-09',
        files: [
          { fileId: 'f-102-1', fileName: 'PRD-CD30-IP18 孔位差异图.pdf', category: '差异图纸', owner: '工程部', uploadedAt: '2026-06-03', versionNo: 'A.2', productId: 102 },
          { fileId: 'f-102-2', fileName: 'PRD-CD30-IP18 黑色外观确认.pdf', category: '客户确认件', owner: '销售部', uploadedAt: '2026-06-06', versionNo: 'A.1', productId: 102 },
          { fileId: 'f-102-3', fileName: 'PRD-CD30-IP18 差异测试记录.xlsx', category: '测试资料', owner: '品质部', uploadedAt: '2026-06-09', versionNo: 'A.1', productId: 102 }
        ]
      }
    ]
  }
]

const testCategories: TestCategoryItem[] = [
  { categoryId: 'drop', categoryName: '跌落测试', method: '1.2m 六面各两次', defaultFrequency: '红样', owner: '品质部' },
  { categoryId: 'wear', categoryName: '耐磨测试', method: '表面往复摩擦 1000 次', defaultFrequency: '黄样', owner: '品质部' },
  { categoryId: 'alcohol', categoryName: '酒精测试', method: '95% 酒精 500 次', defaultFrequency: '红样', owner: '品质部' },
  { categoryId: 'magnetic', categoryName: '磁吸力测试', method: '标准治具吸附验证', defaultFrequency: '黄样', owner: '工程 / 品质' }
]

const testRecords: TestRecordItem[] = [
  { recordId: 'tr-001', productId: 101, productName: '超队 3.0 磁吸手机壳', testCategory: '跌落测试', result: '通过', owner: '品质部', testedAt: '2026-06-05', note: '边角无裂纹' },
  { recordId: 'tr-002', productId: 101, productName: '超队 3.0 磁吸手机壳', testCategory: '酒精测试', result: '复测中', owner: '品质部', testedAt: '2026-06-08', note: 'LOGO 区域掉漆，已调参数' },
  { recordId: 'tr-003', productId: 102, productName: '超队 3.0 iPhone18 黑色', testCategory: '孔位匹配', result: '通过', owner: '工程部', testedAt: '2026-06-08', note: '按键与摄像头位置正常' }
]

const inventoryTree: InventoryTreeNode[] = [
  {
    nodeId: 'raw',
    label: '原材料',
    children: [
      { nodeId: 'raw-tpu', label: 'TPU' },
      { nodeId: 'raw-pc', label: 'PC' },
      { nodeId: 'raw-color', label: '色母' }
    ]
  },
  {
    nodeId: 'component',
    label: '功能件',
    children: [
      { nodeId: 'component-magnet', label: '磁吸组件' },
      { nodeId: 'component-deco', label: '装饰件' }
    ]
  },
  {
    nodeId: 'package',
    label: '包材',
    children: [
      { nodeId: 'package-box', label: '彩盒' },
      { nodeId: 'package-inlay', label: '内托' },
      { nodeId: 'package-label', label: '标签' }
    ]
  },
  {
    nodeId: 'tooling',
    label: '模具治具',
    children: [
      { nodeId: 'tooling-mold', label: '模具' },
      { nodeId: 'tooling-jig', label: '治具' }
    ]
  }
]

const inventoryItems: InventoryListRow[] = [
  { itemId: 'inv-001', nodeId: 'raw-tpu', code: 'INV-MAT-023', name: 'TPU 原料 85A', spec: '25kg / 袋', stock: '500kg', status: 'available', supplierName: '东莞塑胶 A', updatedAt: '2026-06-08' },
  { itemId: 'inv-002', nodeId: 'raw-pc', code: 'INV-MAT-045', name: 'PC 背板', spec: '0.8mm', stock: '320pcs', status: 'reserved', supplierName: '深圳板材 B', updatedAt: '2026-06-07' },
  { itemId: 'inv-003', nodeId: 'raw-color', code: 'INV-MAT-067', name: '黑色色母', spec: '5kg / 箱', stock: '20kg', status: 'available', supplierName: '东莞塑胶 A', updatedAt: '2026-06-09' },
  { itemId: 'inv-004', nodeId: 'component-magnet', code: 'INV-MAG-045', name: 'N52 磁吸组件', spec: '1 set', stock: '200set', status: 'reserved', supplierName: '惠州材料 C', updatedAt: '2026-06-08' },
  { itemId: 'inv-005', nodeId: 'package-box', code: 'INV-PAK-102', name: '渠道彩盒', spec: '1pcs', stock: '3000pcs', status: 'available', supplierName: '深圳包材 D', updatedAt: '2026-06-05' },
  { itemId: 'inv-006', nodeId: 'tooling-mold', code: 'INV-MOLD-201', name: '超队 3.0 注塑模', spec: '1 套', stock: '1 套', status: 'in_use', supplierName: '东莞模具 C', updatedAt: '2026-06-04' }
]

const bomCenterRows: BomCenterRow[] = [
  {
    productId: 101,
    productCode: 'PRD-CD30-001',
    productName: '超队 3.0 磁吸手机壳',
    bomType: 'EBOM',
    currentVersion: 'A.3',
    status: 'reviewing',
    materialCost: 26.9,
    processCost: 5.4,
    totalCost: 32.3,
    supplierNote: '磁吸组件已锁主供，替代料待备案。',
    updatedAt: '2026-06-08'
  },
  {
    productId: 102,
    productCode: 'PRD-CD30-IP18-BLK',
    productName: '超队 3.0 iPhone18 黑色',
    bomType: 'MBOM',
    currentVersion: 'A.2',
    status: 'reviewing',
    materialCost: 9.2,
    processCost: 3.1,
    totalCost: 12.3,
    supplierNote: '黑色色母锁价后即可冻结。',
    updatedAt: '2026-06-09'
  },
  {
    productId: 104,
    productCode: 'PRD-LJ30-001',
    productName: '亮甲 3.0 镜面手机壳',
    bomType: 'PACK',
    currentVersion: 'B.1',
    status: 'released',
    materialCost: 27.4,
    processCost: 6.1,
    totalCost: 33.5,
    supplierNote: '包装版本已冻结，可直接追溯。',
    updatedAt: '2026-05-29'
  }
]

const reportCenterSnapshot: ReportCenterSnapshot = {
  rangeLabel: '2026年6月',
  cards: [
    {
      key: 'development',
      title: '开发进度报表',
      icon: 'DataAnalysis',
      questionLines: ['当前多少产品在开发？', '哪些阶段卡住了？', '有没有逾期的？'],
      targetPath: '/reports?report=development'
    },
    {
      key: 'mold',
      title: '模具状态报表',
      icon: 'Tools',
      questionLines: ['模具都在什么状态？', '哪些快到期还没验收？', '试模成功率如何？'],
      targetPath: '/reports?report=mold'
    },
    {
      key: 'cost',
      title: '成本分析报表',
      icon: 'Money',
      questionLines: ['各产品成本是多少？', '预估 vs 实际差多少？', '哪个环节成本超了？'],
      targetPath: '/reports?report=cost'
    },
    {
      key: 'sampling',
      title: '打样统计报表',
      icon: 'Histogram',
      questionLines: ['本月打了多少样？', '一次通过率如何？', '平均几轮定版？'],
      targetPath: '/reports?report=sampling'
    },
    {
      key: 'change',
      title: '变更统计报表',
      icon: 'Refresh',
      questionLines: ['本月有多少变更？', '变更原因怎么分布？', '影响了哪些产品？'],
      targetPath: '/reports?report=change'
    },
    {
      key: 'quality',
      title: '质量分析报表',
      icon: 'CircleCheck',
      questionLines: ['测试通过率如何？', '哪些测试项经常挂？', '不良集中在哪？'],
      targetPath: '/reports?report=quality'
    }
  ],
  details: [
    {
      key: 'development',
      title: '开发进度报表',
      summary: '先看逾期与卡点，再看阶段分布。',
      metrics: [
        { label: '立项中', value: '6', hint: '平均停留 5 天' },
        { label: '模具阶段', value: '4', hint: '平均停留 15 天，当前最长' },
        { label: '半成品阶段', value: '8', hint: '平均停留 10 天' }
      ],
      alerts: [
        { title: '超队 3.0 iPhone18 黑色', subtitle: '半成品阶段已停留 12 天', owner: '张经理', level: 'medium', targetPath: '/products/102' },
        { title: '亮甲 3.0', subtitle: '模具阶段已停留 18 天', owner: '李工程', level: 'high', targetPath: '/products/104' }
      ],
      distribution: [
        { label: '立项中', value: 6, hint: '5 天' },
        { label: '模具', value: 4, hint: '15 天' },
        { label: '半成品', value: 8, hint: '10 天' },
        { label: '成品', value: 3, hint: '7 天' }
      ]
    },
    {
      key: 'mold',
      title: '模具状态报表',
      summary: '看逾期模具、供应商周期与试模成功率。',
      metrics: [
        { label: '开模中', value: '3', hint: '待开模验收' },
        { label: '试模中', value: '5', hint: '当前重点跟进' },
        { label: '已验收', value: '18', hint: '可复用' }
      ],
      alerts: [
        { title: 'INV-MOLD-0205', subtitle: '亮甲 3.0 试模中，逾期 5 天', owner: '模具供应商 C', level: 'high', targetPath: '/products/104' },
        { title: 'INV-MOLD-0189', subtitle: '骑士 2.0 开模中，逾期 2 天', owner: '模具供应商 E', level: 'medium', targetPath: '/products/101' }
      ],
      distribution: [
        { label: '开模中', value: 3, hint: '平均 12 天' },
        { label: '试模中', value: 5, hint: '平均 7 天' },
        { label: '已验收', value: 18, hint: '稳定' },
        { label: '维修中', value: 2, hint: '需排产' }
      ]
    },
    {
      key: 'cost',
      title: '成本分析报表',
      summary: '看偏差最大的产品，再看成本构成。',
      metrics: [
        { label: '超队 3.0', value: '¥82,300', hint: '比预估低 3.2%' },
        { label: '亮甲 3.0', value: '¥68,500', hint: '比预估高 10.5%' },
        { label: '单品均摊', value: '¥42.62', hint: '含模具均摊' }
      ],
      alerts: [
        { title: '亮甲 3.0', subtitle: '喷涂参数调整导致加工成本抬升', owner: '工程 / 采购', level: 'high', targetPath: '/products/104' },
        { title: '超队 3.0', subtitle: '材料成本稳定，已进入冻结确认', owner: '工程', level: 'low', targetPath: '/products/101' }
      ],
      distribution: [
        { label: '模具', value: 8000, hint: '占比最高' },
        { label: '材料', value: 7000, hint: '稳定' },
        { label: '打样', value: 3500, hint: '可控' },
        { label: '测试', value: 1800, hint: '正常' }
      ]
    },
    {
      key: 'sampling',
      title: '打样统计报表',
      summary: '先看一次通过率，再看失败原因。',
      metrics: [
        { label: '本月打样', value: '12 次', hint: '覆盖 4 条产品线' },
        { label: '一次通过率', value: '42%', hint: '低于目标' },
        { label: '平均定版轮次', value: '2.3 轮', hint: '仍需压缩' }
      ],
      alerts: [
        { title: '超队 3.0', subtitle: '打样 5 次，一次通过率仅 20%', owner: '工程 / 品质', level: 'high', targetPath: '/products/101' },
        { title: '圣殿 Case', subtitle: '打样通过率 100%，可复用流程', owner: '项目部', level: 'low', targetPath: '/products/104' }
      ],
      distribution: [
        { label: '外观不良', value: 40, hint: '占比最高' },
        { label: '尺寸不符', value: 25, hint: '需改结构' },
        { label: '颜色偏差', value: 15, hint: '需校色' },
        { label: '结构问题', value: 10, hint: '少量' }
      ]
    },
    {
      key: 'change',
      title: '变更统计报表',
      summary: '看变更原因分布与最近影响产品。',
      metrics: [
        { label: '本月变更', value: '8 次', hint: '审批中 2 次' },
        { label: '材料变更', value: '3 次', hint: '供应商切换 / 替代料' },
        { label: '工艺变更', value: '3 次', hint: '参数调整 / 工序优化' }
      ],
      alerts: [
        { title: '超队 3.0 iPhone18 黑色', subtitle: 'TPU 替代料已生效', owner: '工程 / 采购', level: 'medium', targetPath: '/products/102' },
        { title: '亮甲 3.0', subtitle: '喷涂参数变更仍在审批中', owner: '工程', level: 'high', targetPath: '/products/104' }
      ],
      distribution: [
        { label: '材料变更', value: 3, hint: '最多' },
        { label: '工艺变更', value: 3, hint: '并列最多' },
        { label: '设计变更', value: 1, hint: '客户需求' },
        { label: '包装变更', value: 1, hint: '条码更新' }
      ]
    },
    {
      key: 'quality',
      title: '质量分析报表',
      summary: '先看失败测试项，再看不通过记录。',
      metrics: [
        { label: '本月测试', value: '45 次', hint: '通过 38 次' },
        { label: '总体通过率', value: '84%', hint: '复测中 5 次' },
        { label: '最低通过项', value: '磁吸力 70%', hint: '需持续跟踪' }
      ],
      alerts: [
        { title: '超队 3.0 iPhone18 黑色', subtitle: '磁吸力不足，已列入差异验证', owner: '品质部', level: 'high', targetPath: '/products/102' },
        { title: '亮甲 3.0', subtitle: '酒精测试不通过，涂层脱落', owner: '品质 / 工程', level: 'high', targetPath: '/products/104' }
      ],
      distribution: [
        { label: '跌落', value: 92, hint: '通过率高' },
        { label: '耐磨', value: 100, hint: '稳定' },
        { label: '酒精', value: 78, hint: '需关注' },
        { label: '磁吸力', value: 70, hint: '最低' }
      ]
    }
  ]
}

export function getFoundationProducts() {
  return mockResolve(() => structuredClone(foundationProducts))
}

export function getFileSections() {
  return mockResolve(() => structuredClone(fileSections))
}

export function getTestCenterSnapshot() {
  return mockResolve(() => ({
    categories: structuredClone(testCategories),
    records: structuredClone(testRecords)
  }))
}

export function getInventoryCenterSnapshot() {
  return mockResolve(() => ({
    tree: structuredClone(inventoryTree),
    items: structuredClone(inventoryItems)
  }))
}

export function getBomCenterRows() {
  return mockResolve(() => structuredClone(bomCenterRows))
}

export function getProductPresentation(productId: number) {
  return mockResolve(() => structuredClone(productDetailPresentationMap[productId] || productDetailPresentationMap[101]))
}

export function getReportCenterSnapshot() {
  return mockResolve(() => structuredClone(reportCenterSnapshot))
}

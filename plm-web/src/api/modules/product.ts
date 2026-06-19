import { productDetails, productList } from '@/mock/data'
import type { ApprovalStep, CommonStatus, MoldAction, ProductFlowMode, ProductLifecycle, TimelineItem } from '@/types/common'
import type { ProductCostBreakdownItem, ProductDetail, ProductFormPayload, ProductSummary } from '@/types/product'
import { mockResolve } from '../request'

function clone<T>(value: T): T {
  return structuredClone(value)
}

function normalizeProductType(value?: string) {
  return value === 'model_variant' || value === '型号扩展' || value === '新型号线' ? 'model_variant' : 'product_line'
}

function getFlowMode(productType: 'product_line' | 'model_variant'): ProductFlowMode {
  return productType === 'product_line' ? 'new_product_line' : 'new_model_variant'
}

function getStepNo(productId: number, flowMode: ProductFlowMode) {
  if (productId === 101) return 14
  if (productId === 102) return 9
  if (productId === 103) return 16
  return flowMode === 'new_product_line' ? 1 : 1
}

function getMoldAction(productId: number, productType: 'product_line' | 'model_variant'): MoldAction | null {
  if (productType === 'product_line') return null
  if (productId === 103) return 'none'
  return 'modify'
}

function getLifecycleByStep(flowMode: ProductFlowMode, stepNo: number): ProductLifecycle {
  if (flowMode === 'new_product_line') {
    if (stepNo <= 2) return 'initiation'
    if (stepNo <= 4) return 'design'
    if (stepNo <= 7) return 'tooling'
    if (stepNo <= 8) return 'sampling'
    if (stepNo <= 16) return 'process'
    if (stepNo <= 17) return 'pilot'
    if (stepNo <= 21) return 'mx'
    return 'release'
  }

  if (stepNo <= 2) return 'initiation'
  if (stepNo <= 4) return 'design'
  if (stepNo <= 6) return 'tooling'
  if (stepNo <= 10) return 'process'
  if (stepNo <= 11) return 'pilot'
  if (stepNo <= 14) return 'mx'
  return 'release'
}

function buildStatus(stepNo: number, currentStepNo: number, released: boolean): CommonStatus {
  if (released) return 'released'
  if (stepNo < currentStepNo) return 'approved'
  if (stepNo === currentStepNo) return 'reviewing'
  return 'pending'
}

function buildNewProductLineTimeline(currentStepNo: number, released: boolean): TimelineItem[] {
  const titles = [
    '产品立项（含立项说明书）',
    '确认立项',
    '画图查看',
    '供应商确认外观图纸',
    '申请开模',
    '制作模具',
    '测试模具',
    '签样确认',
    '加工艺（工艺分类）',
    '敲定工序',
    '确认组件',
    '确认组件成品',
    '最终外观确认样',
    '红样测试',
    '整理生产资料（SIP/SOP）',
    '黄样',
    '小批量测试',
    '运模（选择物流）',
    'MX 验收',
    '测试验证（品质、数量）',
    'MX 小批量测试',
    '根据结果决定投产或继续优化'
  ]

  const phases = [
    '立项阶段',
    '立项阶段',
    '设计验证阶段',
    '设计验证阶段',
    '开模阶段',
    '开模阶段',
    '开模阶段',
    '签样阶段',
    '工艺定型阶段',
    '工艺定型阶段',
    '工艺定型阶段',
    '工艺定型阶段',
    '测试验证阶段',
    '测试验证阶段',
    '测试验证阶段',
    '测试验证阶段',
    '产线验证阶段',
    'MX 验证阶段',
    'MX 验证阶段',
    'MX 验证阶段',
    'MX 验证阶段',
    '发布决策阶段'
  ]

  const gates = new Set([2, 5, 8, 17, 21, 22])

  return titles.map((title, index) => {
    const stepNo = index + 1
    return {
      stepNo,
      title,
      time: null,
      phase: phases[index],
      status: buildStatus(stepNo, currentStepNo, released),
      gate: gates.has(stepNo),
      gateLabel:
        stepNo === 2
          ? '立项关口'
          : stepNo === 5
            ? '开模门禁'
            : stepNo === 8
              ? '签样关口'
              : stepNo === 17
                ? '产线验证'
                : stepNo === 21
                  ? 'MX 产线关口'
                  : stepNo === 22
                    ? '投产决策'
                    : undefined,
      nodeType: stepNo === 22 ? 'decision' : gates.has(stepNo) ? 'gate' : 'stage',
      lifecycle: getLifecycleByStep('new_product_line', stepNo),
      owner:
        stepNo <= 2
          ? '项目经理 / 管理层'
          : stepNo <= 7
            ? '工程 / 供应商'
            : stepNo <= 16
              ? '工程 / 品质 / 采购'
              : stepNo <= 21
                ? '生产 / MX 工厂'
                : '管理层 / 项目经理',
      description:
        stepNo === 5
          ? '开模前 6 项门禁必须全部达标后，才能进入模具制作。'
          : stepNo === 17
            ? '这是产线测试，不是市场测试，重点验证整条线能否顺畅跑通。'
            : stepNo === 22
              ? '根据 MX 小批量和质量结果，决定正式投产或回退优化。'
              : undefined
    }
  })
}

function buildNewModelVariantTimeline(currentStepNo: number, moldAction: MoldAction, released: boolean): TimelineItem[] {
  const titles = [
    '新型号需求确认',
    'Product 子版本建立',
    '图纸与外观差异确认',
    '开模/改模申请',
    '制作/修改模具',
    '测试模具',
    '组件与工艺差异确认',
    '样品确认',
    '差异测试验证',
    '生产资料整理',
    '小批量测试',
    '运模',
    'MX 验收',
    '墨西哥小批量生产验证',
    '版本冻结',
    '正式发布'
  ]

  const phases = [
    '扩展确认阶段',
    '扩展确认阶段',
    '差异设计阶段',
    '模具判断阶段',
    '模具处理阶段',
    '模具处理阶段',
    '差异确认阶段',
    '差异确认阶段',
    '差异验证阶段',
    '生产资料阶段',
    '产线验证阶段',
    'MX 验证阶段',
    'MX 验证阶段',
    'MX 验证阶段',
    '发布阶段',
    '发布阶段'
  ]

  const gates = new Set([2, 4, 9, 11, 15, 16])

  return titles.map((title, index) => {
    const stepNo = index + 1
    const moldSkipped = moldAction === 'none' && (stepNo === 5 || stepNo === 6)
    return {
      stepNo,
      title,
      time: null,
      phase: phases[index],
      status: moldSkipped ? 'skipped' : buildStatus(stepNo, currentStepNo, released),
      gate: gates.has(stepNo),
      gateLabel:
        stepNo === 2
          ? '扩展入口'
          : stepNo === 4
            ? '模具判断'
            : stepNo === 9
              ? '差异验证'
              : stepNo === 11
                ? '产线验证'
                : stepNo === 15
                  ? '版本冻结'
                  : stepNo === 16
                    ? '子版本发布'
                    : undefined,
      nodeType: stepNo === 4 ? 'decision' : gates.has(stepNo) ? 'gate' : 'stage',
      lifecycle: getLifecycleByStep('new_model_variant', stepNo),
      variantTag:
        stepNo === 2 || stepNo >= 10
          ? 'inherited'
          : stepNo === 4 || stepNo === 5 || stepNo === 6
            ? 'optional'
            : 'difference',
      branchLabel:
        stepNo === 5 || stepNo === 6
          ? moldAction === 'modify'
            ? 'A. 改模'
            : moldAction === 'new'
              ? 'B. 新开模'
              : 'C. 无需模具变更'
          : stepNo === 4
            ? moldAction === 'modify'
              ? '改模分支'
              : moldAction === 'new'
                ? '新开模分支'
                : '跳过模具分支'
            : undefined,
      branchStatus:
        stepNo === 4
          ? 'selected'
          : stepNo === 5 || stepNo === 6
            ? moldAction === 'none'
              ? 'skipped'
              : 'selected'
            : undefined,
      owner:
        stepNo <= 4
          ? '项目经理 / 工程'
          : stepNo <= 9
            ? '工程 / 品质 / 采购'
            : stepNo <= 11
              ? '工程 / 生产'
              : stepNo <= 14
                ? 'MX 工厂'
                : '工程 / 管理层',
      description:
        stepNo === 4
          ? '新型号不一定需要全新开模，可以是改模、全新开模，或完全跳过。'
          : stepNo === 9
            ? '只验证变化部分，不跑完整测试套件。'
            : moldSkipped && stepNo === 5
              ? '本次仅颜色或包装变化，跳过模具处理。'
              : moldSkipped && stepNo === 6
                ? '本次没有模具变更，因此跳过试模。'
                : undefined
    }
  })
}

function buildApprovals(flowMode: ProductFlowMode, released: boolean, moldAction: MoldAction | null): ApprovalStep[] {
  const source =
    flowMode === 'new_product_line'
      ? [
          { stepName: '立项审批', approver: '管理层', status: 'approved' as CommonStatus, time: '2026-05-29T16:30:00+08:00', comment: '同意按完整新产品线推进。' },
          { stepName: '开模审批', approver: '管理层', status: 'approved' as CommonStatus, time: '2026-06-02T17:30:00+08:00', comment: '6 项开模门禁已达标。' },
          { stepName: '签样确认', approver: '项目经理', status: 'approved' as CommonStatus, time: '2026-06-04T18:00:00+08:00', comment: '外观与结构样品确认完成。' },
          { stepName: '发布审批', approver: '管理层', status: 'pending' as CommonStatus, time: null, comment: '待 MX 验证和资料冻结完成后发布。' }
        ]
      : [
          { stepName: '扩展确认', approver: '项目经理', status: 'approved' as CommonStatus, time: '2026-06-02T18:00:00+08:00', comment: '同意基于父产品创建新型号子版本。' },
          {
            stepName: '模具判断记录',
            approver: '模具工程师',
            status: 'approved' as CommonStatus,
            time: '2026-06-04T15:00:00+08:00',
            comment:
              moldAction === 'none'
                ? '本次无需模具变更。'
                : moldAction === 'new'
                  ? '本次需要新开模。'
                  : '本次采用改模方案。'
          },
          { stepName: '版本冻结确认', approver: '品质主管', status: 'pending' as CommonStatus, time: null, comment: '待差异图纸、BOM、样品记录全部齐套后冻结。' },
          { stepName: '子版本发布审批', approver: '管理层', status: 'pending' as CommonStatus, time: null, comment: '待冻结完成后正式发布子版本。' }
        ]

  if (!released) return source

  return source.map((item, index) => ({
    ...item,
    status: 'approved',
    time: item.time || `2026-06-${String(12 + index).padStart(2, '0')}T16:00:00+08:00`
  }))
}

function buildCostBreakdown(flowMode: ProductFlowMode, estimatedCost: number, rdCost: number, productCost: number): ProductCostBreakdownItem[] {
  const total = Math.max(flowMode === 'new_product_line' ? estimatedCost : rdCost + productCost, 1)

  if (flowMode === 'new_product_line') {
    return [
      { category: '预计成本', amount: estimatedCost, ratio: Number((estimatedCost / total).toFixed(4)), note: '立项阶段记录的目标成本基线。' },
      { category: '研发成本', amount: rdCost, ratio: Number((rdCost / total).toFixed(4)), note: '模具、打样、测试等一次性研发投入。' },
      { category: '成品成本', amount: productCost, ratio: Number((productCost / total).toFixed(4)), note: '按 BOM、工艺、包装等汇总的成品成本。' }
    ]
  }

  return [
    { category: '差异研发成本', amount: rdCost, ratio: Number((rdCost / Math.max(rdCost + productCost, 1)).toFixed(4)), note: '改模、样品和差异测试带来的增量成本。' },
    { category: '差异成品成本', amount: productCost, ratio: Number((productCost / Math.max(rdCost + productCost, 1)).toFixed(4)), note: '新型号差异组件和替代料带来的增量成本。' },
    { category: '总成本', amount: rdCost + productCost, ratio: 1, note: '共用父产品资产，当前只统计增量部分。' }
  ]
}

function normalizeDetail(detail: ProductDetail, summary?: ProductSummary): ProductDetail {
  const normalized = clone(detail)
  const sourceSummary = summary || productList.find((item) => item.productId === normalized.productId)
  if (!sourceSummary) return normalized

  const flowMode = sourceSummary.productFlowMode || getFlowMode(sourceSummary.productType)
  const currentStepNo = sourceSummary.currentStepNo || getStepNo(sourceSummary.productId, flowMode)
  const moldAction = sourceSummary.moldAction || getMoldAction(sourceSummary.productId, sourceSummary.productType)
  const released = sourceSummary.status === 'released'
  const lifecycle = sourceSummary.lifecycle || getLifecycleByStep(flowMode, currentStepNo)

  normalized.basicInfo.productType = sourceSummary.productType === 'product_line' ? '新产品线' : '新型号线'
  normalized.basicInfo.productTypeLabel = sourceSummary.productType === 'product_line' ? '新产品完整研发流程' : '新型号差异扩展流程'
  normalized.basicInfo.productFlowMode = flowMode
  normalized.basicInfo.currentStepNo = currentStepNo
  normalized.basicInfo.lifecycle = lifecycle
  normalized.basicInfo.moldAction = moldAction
  normalized.basicInfo.actualCost = sourceSummary.actualCost ?? normalized.basicInfo.actualCost ?? null
  normalized.basicInfo.rdCost = sourceSummary.rdCost ?? normalized.basicInfo.rdCost ?? null
  normalized.basicInfo.productCost = sourceSummary.productCost ?? normalized.basicInfo.productCost ?? null
  normalized.basicInfo.totalCost = sourceSummary.totalCost ?? normalized.basicInfo.totalCost ?? null
  normalized.basicInfo.parentProductName =
    sourceSummary.parentProductId != null
      ? productList.find((item) => item.productId === sourceSummary.parentProductId)?.productName || null
      : null
  normalized.basicInfo.inheritedSummary =
    sourceSummary.productType === 'model_variant'
      ? `继承自 ${normalized.basicInfo.parentProductName || '父产品线'} 的基础 BOM、工艺、测试和文件模板，仅管理差异项。`
      : null
  normalized.basicInfo.nextAction =
    sourceSummary.nextAction ||
    (sourceSummary.productType === 'product_line' ? '继续推进下一项新产品开发节点。' : '继续确认差异项和新型号分支处理。')
  normalized.basicInfo.gateSummary =
    sourceSummary.gateSummary ||
    (sourceSummary.productType === 'product_line'
      ? '新产品需经过立项、开模、签样、产线验证和 MX 验证等关键关口。'
      : '新型号需经过扩展确认、模具判断、版本冻结和子版本发布等关键关口。')

  normalized.statusTimeline =
    flowMode === 'new_product_line'
      ? buildNewProductLineTimeline(currentStepNo, released)
      : buildNewModelVariantTimeline(currentStepNo, moldAction || 'modify', released)
  normalized.approvalTimeline = buildApprovals(flowMode, released, moldAction)

  if (!normalized.costBreakdown.length) {
    normalized.costBreakdown = buildCostBreakdown(
      flowMode,
      normalized.basicInfo.estimatedCost,
      normalized.basicInfo.rdCost || 0,
      normalized.basicInfo.productCost || 0
    )
  }

  return normalized
}

function createDefaultDetail(summary: ProductSummary, payload: Partial<ProductFormPayload>) {
  const flowMode = summary.productFlowMode || getFlowMode(summary.productType)
  const detail: ProductDetail = {
    productId: summary.productId,
    basicInfo: {
      productCode: summary.productCode,
      productName: summary.productName,
      seriesName: summary.seriesName,
      productType: summary.productType,
      productFlowMode: flowMode,
      ownerUserName: summary.ownerUserName,
      status: summary.status,
      versionNo: summary.versionNo,
      material: summary.material,
      packageType: payload.packageType || '待补充',
      surfaceProcess: payload.surfaceProcess || '待补充',
      coreProcess: payload.coreProcess || '待补充',
      composition: payload.composition || '待补充',
      customerName: summary.customerName,
      currentStage: summary.currentStage,
      currentStepNo: summary.currentStepNo,
      expectedReleaseDate: payload.expectedReleaseDate || null,
      model: summary.model,
      color: summary.color,
      estimatedCost: summary.estimatedCost,
      estimatedCostCurrency: summary.estimatedCostCurrency,
      actualCost: summary.actualCost || null,
      rdCost: summary.rdCost || null,
      productCost: summary.productCost || null,
      totalCost: summary.totalCost || null,
      productTypeLabel: summary.productType === 'product_line' ? '新产品线' : '新型号线',
      parentProductName:
        summary.parentProductId != null
          ? productList.find((item) => item.productId === summary.parentProductId)?.productName || null
          : null,
      inheritedSummary: null,
      lifecycle: summary.lifecycle,
      moldAction: summary.moldAction || null,
      nextAction: summary.nextAction || null,
      gateSummary: summary.gateSummary || null
    },
    statusTimeline: [],
    approvalTimeline: [],
    bomItems: [],
    attachments: [],
    qualityRecords: (payload.testItems || []).map((item) => ({
      testItem: item.name,
      result: item.result,
      owner: item.owner,
      dueDate: item.dueDate
    })),
    operationLogs: [
      {
        time: new Date().toISOString(),
        operator: summary.ownerUserName,
        action: summary.productType === 'product_line' ? '创建新产品初始资料。' : '创建新型号初始资料。',
        level: 'normal'
      }
    ],
    versionHistory: [
      {
        versionNo: summary.versionNo,
        releasedAt: null,
        releasedBy: '--',
        changeSummary: summary.productType === 'product_line' ? '新产品初始版本' : '新型号初始版本',
        status: 'draft',
        bomVersion: summary.activeBomVersion,
        estimatedCost: summary.estimatedCost,
        actualCost: null
      }
    ],
    costBreakdown: clone(payload.costBreakdown || buildCostBreakdown(flowMode, summary.estimatedCost, summary.rdCost || 0, summary.productCost || 0)),
    testItems: clone(payload.testItems || [])
  }

  return normalizeDetail(detail, summary)
}

export function getProductList() {
  return mockResolve(() =>
    clone(productList).map((item) => {
      const flowMode = item.productFlowMode || getFlowMode(item.productType)
      const currentStepNo = item.currentStepNo || getStepNo(item.productId, flowMode)
      const lifecycle = item.lifecycle || getLifecycleByStep(flowMode, currentStepNo)
      const moldAction = item.moldAction || getMoldAction(item.productId, item.productType)
      return {
        ...item,
        currentStepNo,
        productFlowMode: flowMode,
        lifecycle,
        moldAction,
        actualCost: item.actualCost ?? (item.status === 'released' ? item.estimatedCost - 0.8 : null),
        rdCost: item.rdCost ?? (item.productType === 'product_line' ? 12.6 : 3.5),
        productCost: item.productCost ?? (item.productType === 'product_line' ? 21.7 : 10.1),
        totalCost: item.totalCost ?? (item.productType === 'product_line' ? 34.3 : 13.6),
        nextAction:
          item.productType === 'product_line'
            ? '继续推进下一项新产品节点。'
            : '继续确认差异项和子版本发布条件。',
        gateSummary:
          item.productType === 'product_line'
            ? '关注立项、开模、签样、产线验证与 MX 关口。'
            : '关注扩展确认、模具判断、版本冻结与子版本发布。'
      }
    })
  )
}

export function getProductDetail(productId: number) {
  return mockResolve(() => {
    const detail = productDetails[productId]
    const summary = productList.find((item) => item.productId === productId)
    if (!detail || !summary) throw new Error('产品不存在')
    return normalizeDetail(detail, summary)
  })
}

export function createProduct(payload: Partial<ProductFormPayload>) {
  return mockResolve(() => {
    const nextId = Math.max(...productList.map((item) => item.productId)) + 1
    const productType = normalizeProductType(payload.productType)
    const flowMode = getFlowMode(productType)
    const currentStepNo = payload.currentStepNo || 1
    const rdCost = Number(payload.rdCost || 0)
    const productCost = Number(payload.productCost || 0)

    const summary: ProductSummary = {
      productId: nextId,
      parentProductId: payload.parentProductId || null,
      productCode: payload.productCode || `PRD-AUTO-${nextId}`,
      productName: payload.productName || '未命名产品',
      productType,
      seriesName: payload.seriesName || payload.productName || '新系列',
      model: payload.model || (productType === 'model_variant' ? '待定义' : '--'),
      color: payload.color || (productType === 'model_variant' ? '待定义' : '--'),
      material: payload.material || '待补充',
      ownerUserName: payload.ownerUserName || '当前用户',
      versionNo: payload.versionNo || 'A',
      status: 'draft',
      currentStage: payload.currentStage || (productType === 'product_line' ? '产品立项（含立项说明书）' : '新型号需求确认'),
      currentStepNo,
      customerName: payload.customerName || '未指定',
      frozenFlag: false,
      releasedAt: null,
      completionRate: productType === 'product_line' ? 0.05 : 0.08,
      estimatedCost: Number(payload.estimatedCost || 0),
      estimatedCostCurrency: payload.estimatedCostCurrency || 'CNY',
      actualCost: Number(payload.actualCost || rdCost + productCost || 0),
      rdCost,
      productCost,
      totalCost: rdCost + productCost,
      testItemCount: payload.testItems?.length || 0,
      activeBomVersion: productType === 'product_line' ? 'EBOM-A.0' : 'MBOM-A.0',
      productFlowMode: flowMode,
      lifecycle: getLifecycleByStep(flowMode, currentStepNo),
      moldAction: payload.moldAction || (productType === 'model_variant' ? 'modify' : null),
      nextAction:
        productType === 'product_line'
          ? '先完成立项说明书和立项确认。'
          : '先选择父产品线并明确差异范围。',
      gateSummary:
        productType === 'product_line'
          ? '新产品从立项开始推进完整研发链路。'
          : '新型号先走扩展确认，再判断模具分支。'
    }

    productList.unshift(summary)
    productDetails[nextId] = createDefaultDetail(summary, payload)
    return clone(productDetails[nextId])
  }, 260)
}

export function updateProduct(productId: number, payload: Partial<ProductFormPayload>) {
  return mockResolve(() => {
    const detail = productDetails[productId]
    const summary = productList.find((item) => item.productId === productId)
    if (!detail || !summary) throw new Error('产品不存在')

    const productType = normalizeProductType(payload.productType || summary.productType)
    const flowMode = getFlowMode(productType)
    const currentStepNo = payload.currentStepNo || summary.currentStepNo || getStepNo(productId, flowMode)

    summary.parentProductId = payload.parentProductId ?? summary.parentProductId
    summary.productCode = payload.productCode ?? summary.productCode
    summary.productName = payload.productName ?? summary.productName
    summary.productType = productType
    summary.seriesName = payload.seriesName ?? summary.seriesName
    summary.model = payload.model ?? summary.model
    summary.color = payload.color ?? summary.color
    summary.material = payload.material ?? summary.material
    summary.ownerUserName = payload.ownerUserName ?? summary.ownerUserName
    summary.versionNo = payload.versionNo ?? summary.versionNo
    summary.customerName = payload.customerName ?? summary.customerName
    summary.currentStage = payload.currentStage ?? summary.currentStage
    summary.currentStepNo = currentStepNo
    summary.estimatedCost = Number(payload.estimatedCost ?? summary.estimatedCost)
    summary.estimatedCostCurrency = payload.estimatedCostCurrency ?? summary.estimatedCostCurrency
    summary.rdCost = Number(payload.rdCost ?? summary.rdCost ?? 0)
    summary.productCost = Number(payload.productCost ?? summary.productCost ?? 0)
    summary.actualCost = Number(payload.actualCost ?? summary.actualCost ?? (summary.rdCost + summary.productCost))
    summary.totalCost = Number((summary.rdCost || 0) + (summary.productCost || 0))
    summary.testItemCount = payload.testItems?.length ?? summary.testItemCount
    summary.productFlowMode = flowMode
    summary.lifecycle = getLifecycleByStep(flowMode, currentStepNo)
    summary.moldAction = payload.moldAction ?? summary.moldAction ?? null

    detail.basicInfo = {
      ...detail.basicInfo,
      productCode: summary.productCode,
      productName: summary.productName,
      seriesName: summary.seriesName,
      productType: productType === 'product_line' ? '新产品线' : '新型号线',
      productFlowMode: flowMode,
      ownerUserName: summary.ownerUserName,
      versionNo: summary.versionNo,
      material: summary.material,
      packageType: payload.packageType ?? detail.basicInfo.packageType,
      surfaceProcess: payload.surfaceProcess ?? detail.basicInfo.surfaceProcess,
      coreProcess: payload.coreProcess ?? detail.basicInfo.coreProcess,
      composition: payload.composition ?? detail.basicInfo.composition,
      customerName: summary.customerName,
      currentStage: summary.currentStage,
      currentStepNo,
      expectedReleaseDate: payload.expectedReleaseDate ?? detail.basicInfo.expectedReleaseDate,
      model: summary.model,
      color: summary.color,
      estimatedCost: summary.estimatedCost,
      estimatedCostCurrency: summary.estimatedCostCurrency,
      actualCost: summary.actualCost ?? null,
      rdCost: summary.rdCost ?? null,
      productCost: summary.productCost ?? null,
      totalCost: summary.totalCost ?? null,
      productTypeLabel: productType === 'product_line' ? '新产品完整研发流程' : '新型号差异扩展流程',
      lifecycle: summary.lifecycle,
      moldAction: summary.moldAction,
      nextAction:
        productType === 'product_line'
          ? '继续推进下一项新产品开发节点。'
          : '继续确认差异项和子版本发布条件。',
      gateSummary:
        productType === 'product_line'
          ? '关注立项、开模、签样、产线验证和 MX 关口。'
          : '关注扩展确认、模具判断、版本冻结和子版本发布。'
    }

    if (payload.costBreakdown) {
      detail.costBreakdown = clone(payload.costBreakdown)
    } else {
      detail.costBreakdown = buildCostBreakdown(flowMode, summary.estimatedCost, summary.rdCost || 0, summary.productCost || 0)
    }

    if (payload.testItems) {
      detail.testItems = clone(payload.testItems)
      detail.qualityRecords = detail.testItems.map((item) => ({
        testItem: item.name,
        result: item.result,
        owner: item.owner,
        dueDate: item.dueDate
      }))
    }

    detail.operationLogs.unshift({
      time: new Date().toISOString(),
      operator: summary.ownerUserName,
      action: productType === 'product_line' ? '更新新产品资料。' : '更新新型号差异资料。',
      level: 'normal'
    })

    productDetails[productId] = normalizeDetail(detail, summary)
    return clone(productDetails[productId])
  }, 240)
}

export function publishProduct(productId: number) {
  return mockResolve(() => {
    const detail = productDetails[productId]
    const summary = productList.find((item) => item.productId === productId)
    if (!detail || !summary) throw new Error('产品不存在')

    summary.status = 'released'
    summary.releasedAt = new Date().toISOString()
    summary.completionRate = 1
    detail.basicInfo.status = 'released'
    detail.operationLogs.unshift({
      time: new Date().toISOString(),
      operator: summary.ownerUserName,
      action: '执行正式发布。',
      level: 'normal'
    })

    productDetails[productId] = normalizeDetail(detail, summary)
    return clone(productDetails[productId])
  }, 220)
}

export function freezeProduct(productId: number) {
  return mockResolve(() => {
    const detail = productDetails[productId]
    const summary = productList.find((item) => item.productId === productId)
    if (!detail || !summary) throw new Error('产品不存在')

    summary.frozenFlag = true
    detail.operationLogs.unshift({
      time: new Date().toISOString(),
      operator: summary.ownerUserName,
      action: '执行资料冻结。',
      level: 'normal'
    })

    productDetails[productId] = normalizeDetail(detail, summary)
    return clone(productDetails[productId])
  }, 220)
}

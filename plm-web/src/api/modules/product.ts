import { productDetails, productList } from '@/mock/data'
import type { ProductDetail, ProductFormPayload, ProductSummary } from '@/types/product'
import { mockResolve } from '../request'

function clone<T>(value: T): T {
  return structuredClone(value)
}

function normalizeProductType(value?: string) {
  return value === 'model_variant' || value === '型号扩展' ? 'model_variant' : 'product_line'
}

export function getProductList() {
  return mockResolve(() => clone(productList))
}

export function getProductDetail(productId: number) {
  return mockResolve(() => {
    const detail = productDetails[productId]
    if (!detail) throw new Error('产品不存在')
    return clone(detail)
  })
}

export function createProduct(payload: Partial<ProductFormPayload>) {
  return mockResolve(() => {
    const nextId = Math.max(...productList.map((item) => item.productId)) + 1
    const productType = normalizeProductType(payload.productType)
    const estimatedCost = Number(payload.estimatedCost || 0)
    const testItems = clone(payload.testItems || [])
    const costBreakdown = clone(payload.costBreakdown || [])

    const summary: ProductSummary = {
      productId: nextId,
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
      currentStage: payload.currentStage || 'Product 建档',
      customerName: payload.customerName || '未指定',
      frozenFlag: false,
      releasedAt: null,
      completionRate: 0.25,
      estimatedCost,
      estimatedCostCurrency: payload.estimatedCostCurrency || 'CNY',
      testItemCount: testItems.length,
      activeBomVersion: 'EBOM-draft'
    }

    productList.unshift(summary)
    productDetails[nextId] = {
      productId: nextId,
      basicInfo: {
        productCode: summary.productCode,
        productName: summary.productName,
        seriesName: summary.seriesName,
        productType: productType === 'product_line' ? '新产品产品线' : '型号扩展',
        productTypeLabel: productType === 'product_line' ? '新产品产品线' : 'SKU 型号视图',
        ownerUserName: summary.ownerUserName,
        status: 'draft',
        versionNo: summary.versionNo,
        material: summary.material,
        packageType: payload.packageType || '待补充',
        surfaceProcess: payload.surfaceProcess || '待补充',
        coreProcess: payload.coreProcess || '待补充',
        composition: payload.composition || '待补充',
        customerName: summary.customerName,
        currentStage: summary.currentStage,
        expectedReleaseDate: payload.expectedReleaseDate || null,
        model: summary.model,
        color: summary.color,
        estimatedCost,
        estimatedCostCurrency: payload.estimatedCostCurrency || 'CNY'
      },
      statusTimeline: [
        {
          title: 'Product 建档',
          time: new Date().toISOString(),
          owner: summary.ownerUserName,
          status: 'draft',
          description: '创建产品主数据记录。'
        }
      ],
      approvalTimeline: [],
      bomItems: [],
      attachments: [],
      qualityRecords: testItems.map((item) => ({
        testItem: item.name,
        result: item.result,
        owner: item.owner,
        dueDate: item.dueDate
      })),
      operationLogs: [
        {
          time: new Date().toISOString(),
          operator: summary.ownerUserName,
          action: '创建产品资料。',
          level: 'normal'
        }
      ],
      versionHistory: [
        {
          versionNo: summary.versionNo,
          releasedAt: null,
          releasedBy: '--',
          changeSummary: '创建初始版本。',
          status: 'draft',
          bomVersion: 'EBOM-draft',
          estimatedCost,
          actualCost: null
        }
      ],
      costBreakdown,
      testItems
    }

    return clone(productDetails[nextId])
  }, 260)
}

export function updateProduct(productId: number, payload: Partial<ProductFormPayload>) {
  return mockResolve(() => {
    const detail = productDetails[productId]
    const summary = productList.find((item) => item.productId === productId)
    if (!detail || !summary) throw new Error('产品不存在')

    const productType = normalizeProductType(payload.productType || detail.basicInfo.productType)

    detail.basicInfo = {
      ...detail.basicInfo,
      productCode: payload.productCode ?? detail.basicInfo.productCode,
      productName: payload.productName ?? detail.basicInfo.productName,
      seriesName: payload.seriesName ?? detail.basicInfo.seriesName,
      productType: productType === 'product_line' ? '新产品产品线' : '型号扩展',
      productTypeLabel: productType === 'product_line' ? '新产品产品线' : 'SKU 型号视图',
      ownerUserName: payload.ownerUserName ?? detail.basicInfo.ownerUserName,
      versionNo: payload.versionNo ?? detail.basicInfo.versionNo,
      material: payload.material ?? detail.basicInfo.material,
      packageType: payload.packageType ?? detail.basicInfo.packageType,
      surfaceProcess: payload.surfaceProcess ?? detail.basicInfo.surfaceProcess,
      coreProcess: payload.coreProcess ?? detail.basicInfo.coreProcess,
      composition: payload.composition ?? detail.basicInfo.composition,
      customerName: payload.customerName ?? detail.basicInfo.customerName,
      currentStage: payload.currentStage ?? detail.basicInfo.currentStage,
      expectedReleaseDate: payload.expectedReleaseDate ?? detail.basicInfo.expectedReleaseDate,
      model: payload.model ?? detail.basicInfo.model,
      color: payload.color ?? detail.basicInfo.color,
      estimatedCost: Number(payload.estimatedCost ?? detail.basicInfo.estimatedCost),
      estimatedCostCurrency: payload.estimatedCostCurrency ?? detail.basicInfo.estimatedCostCurrency
    }

    if (payload.costBreakdown) {
      detail.costBreakdown = clone(payload.costBreakdown)
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
      operator: payload.ownerUserName || detail.basicInfo.ownerUserName,
      action: '更新产品基础资料。',
      level: 'normal'
    })

    summary.productCode = detail.basicInfo.productCode
    summary.productName = detail.basicInfo.productName
    summary.productType = productType
    summary.seriesName = detail.basicInfo.seriesName
    summary.model = detail.basicInfo.model
    summary.color = detail.basicInfo.color
    summary.material = detail.basicInfo.material
    summary.ownerUserName = detail.basicInfo.ownerUserName
    summary.versionNo = detail.basicInfo.versionNo
    summary.customerName = detail.basicInfo.customerName
    summary.currentStage = detail.basicInfo.currentStage
    summary.estimatedCost = detail.basicInfo.estimatedCost
    summary.estimatedCostCurrency = detail.basicInfo.estimatedCostCurrency
    summary.testItemCount = detail.testItems.length

    if (detail.versionHistory.length) {
      detail.versionHistory[detail.versionHistory.length - 1].estimatedCost = detail.basicInfo.estimatedCost
    }

    return clone(detail)
  }, 240)
}

export function publishProduct(productId: number) {
  return mockResolve(() => {
    const detail = productDetails[productId]
    const summary = productList.find((item) => item.productId === productId)
    if (!detail || !summary) throw new Error('产品不存在')
    detail.basicInfo.status = 'released'
    summary.status = 'released'
    summary.releasedAt = new Date().toISOString()
    return clone(detail)
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
      operator: '当前用户',
      action: '执行资料冻结操作。',
      level: 'normal'
    })
    return clone(detail)
  }, 220)
}

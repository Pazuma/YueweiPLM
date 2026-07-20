import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn()
}))

vi.mock('../../request', () => ({
  default: requestMock,
  mockResolve: vi.fn(),
  unwrapResponse: <T>(response: { data: { data: T } }) => response.data.data,
  unwrapPage: <T>(response: { data: { data: T } }) => response.data.data
}))

import {
  addBomItem,
  createProjectBom,
  freezeBom,
  getBomLedger,
  getBomSkus,
  getBomWorkbench,
  getProductBomSummary,
  getProcessRouteSkus,
  saveBomRoutes,
  recalculateBomCosts,
  submitBomReview,
  publishBom,
  copyBomVersion,
  inheritBom,
  saveTestBom,
  confirmTestBom,
  previewBomImport,
  commitBomImport,
  downloadBomImportTemplate,
  downloadBomImportErrors,
  getBomDetail,
  getProjectBoms,
  getProductionConfirmation,
  confirmProductionRoutes
} from '../bom'
import {
  createProcessOperationMaster,
  createProcessRoute,
  freezeProcessRoute,
  getProcessOperationMasters,
  getProcessRouteDetail,
  getProcessRouteTemplates,
  getProjectProcessRoutes
} from '../process'
import {
  deleteAttachment,
  downloadAttachment,
  getFileCenterAttachments,
  getTimelineAttachments,
  uploadTimelineAttachment
} from '../attachment'

function apiResponse<T>(data: T) {
  return Promise.resolve({ data: { code: 0, message: 'success', data } })
}

describe('M4 API contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uses real project BOM endpoints and backend identifiers', async () => {
    const bom = { productBomId: 31, productId: 7, items: [] }
    requestMock.get.mockReturnValueOnce(apiResponse([bom])).mockReturnValueOnce(apiResponse(bom))
    requestMock.post.mockReturnValue(apiResponse(bom))

    await expect(getProjectBoms(7)).resolves.toEqual([bom])
    await expect(getBomDetail(31)).resolves.toEqual(bom)
    await createProjectBom(7, {
      bomName: '样品 BOM',
      bomType: 'ebom',
      versionNo: 'A',
      processId: 81,
      remark: 'M4'
    })
    await addBomItem(31, {
      itemName: 'PC 背板',
      lineNo: 10,
      quantity: 1,
      unit: 'pcs'
    })
    await freezeBom(31)

    expect(requestMock.get).toHaveBeenNthCalledWith(1, '/projects/7/boms')
    expect(requestMock.get).toHaveBeenNthCalledWith(2, '/boms/31')
    expect(requestMock.post).toHaveBeenNthCalledWith(1, '/projects/7/boms', {
      bomName: '样品 BOM',
      bomType: 'ebom',
      versionNo: 'A',
      processId: 81,
      remark: 'M4'
    })
    expect(requestMock.post).toHaveBeenNthCalledWith(2, '/boms/31/items', {
      itemName: 'PC 背板',
      lineNo: 10,
      quantity: 1,
      unit: 'pcs'
    })
    expect(requestMock.post).toHaveBeenNthCalledWith(3, '/boms/31/freeze')
  })

  it('confirms formal BOM and production operations per process route', async () => {
    const confirmation = {
      productId: 7,
      selectedOperationCount: 1,
      selectedColorCount: 0,
      createdSkuCount: 0,
      operationProcessIds: [91],
      routeSelections: [{
        processId: 9,
        productBomId: 31,
        productBomRouteId: 81,
        routeName: '染色工艺路线',
        bomVersionNo: 'V1',
        operationProcessIds: [91]
      }],
      colors: []
    }
    const payload = {
      routes: [{
        processId: 9,
        productBomId: 31,
        productBomRouteId: 81,
        operationProcessIds: [91]
      }],
      remark: 'M4 route confirmation'
    }
    requestMock.get.mockReturnValueOnce(apiResponse(confirmation))
    requestMock.post.mockReturnValueOnce(apiResponse(confirmation))

    await expect(getProductionConfirmation(7)).resolves.toEqual(confirmation)
    await expect(confirmProductionRoutes(7, payload)).resolves.toEqual(confirmation)

    expect(requestMock.get).toHaveBeenCalledWith('/projects/7/production-confirmation')
    expect(requestMock.post).toHaveBeenCalledWith('/projects/7/production-routes/confirm', payload)
  })

  it('uses workbench ledger route cost lifecycle inheritance and import endpoints', async () => {
    const data = { productBomId: 31 }
    requestMock.get.mockReturnValue(apiResponse(data))
    requestMock.put.mockReturnValue(apiResponse(data))
    requestMock.post.mockReturnValue(apiResponse(data))
    const routes = [{ processId: 81, routeCode: 'DYE', routeName: '染色', colors: ['蓝色'], items: [] }]

    await getBomLedger()
    await getBomWorkbench(31)
    await getBomSkus(31)
    await getProcessRouteSkus(81)
    await getProductBomSummary(7)
    await saveBomRoutes(31, routes)
    await recalculateBomCosts(31, routes)
    await submitBomReview(31)
    await publishBom(31)
    await copyBomVersion(31, { versionNo: 'V2', selectedColors: ['蓝色'] })
    await inheritBom(7, { sourceBomId: 31, selectedColors: ['蓝色'] })
    await saveTestBom(7, { versionNo: 'T1', items: [] })
    await confirmTestBom(7)
    const file = new File(['xlsx'], 'bom.xlsx')
    await previewBomImport(7, 31, file)
    await commitBomImport('token-1')

    expect(requestMock.get).toHaveBeenCalledWith('/bom-ledger')
    expect(requestMock.get).toHaveBeenCalledWith('/boms/31/workbench')
    expect(requestMock.get).toHaveBeenCalledWith('/boms/31/skus')
    expect(requestMock.get).toHaveBeenCalledWith('/process-routes/81/skus')
    expect(requestMock.get).toHaveBeenCalledWith('/products/7/bom-summary')
    expect(requestMock.put).toHaveBeenCalledWith('/boms/31/routes', routes)
    expect(requestMock.post).toHaveBeenCalledWith('/boms/31/costs/recalculate', routes)
    expect(requestMock.post).toHaveBeenCalledWith('/boms/31/submit-review')
    expect(requestMock.post).toHaveBeenCalledWith('/boms/31/publish')
    expect(requestMock.post).toHaveBeenCalledWith('/boms/31/copy-version', { versionNo: 'V2', selectedColors: ['蓝色'] })
    expect(requestMock.post).toHaveBeenCalledWith('/products/7/boms/inherit', { sourceBomId: 31, selectedColors: ['蓝色'] })

    requestMock.get.mockResolvedValue({ data: new Blob() })
    await downloadBomImportTemplate()
    await downloadBomImportErrors('token-1')
    expect(requestMock.get).toHaveBeenCalledWith('/boms/import/template', { responseType: 'blob' })
    expect(requestMock.get).toHaveBeenCalledWith('/boms/import/token-1/errors', { responseType: 'blob' })
  })

  it('sends complete process route payloads to real endpoints', async () => {
    const route = { processId: 81, productId: 7, status: 'draft', operations: [] }
    const payload = {
      processName: '样品工艺路线',
      versionNo: 'A',
      remark: 'M4',
      operations: [{
        sequenceNo: 10,
        operationMasterProcessId: 900,
        processName: '注塑成型',
        processParamJson: '{"temperature":82}',
        standardTimeMins: 15,
        qualityRequirement: '外观无缩水和披锋',
        remark: ''
      }]
    }
    requestMock.get.mockReturnValueOnce(apiResponse([route])).mockReturnValueOnce(apiResponse(route))
    requestMock.post.mockReturnValue(apiResponse(route))

    await expect(getProjectProcessRoutes(7)).resolves.toEqual([route])
    await expect(getProcessRouteDetail(81)).resolves.toEqual(route)
    await createProcessRoute(7, payload)
    await freezeProcessRoute(81)

    expect(requestMock.get).toHaveBeenNthCalledWith(1, '/projects/7/process-routes')
    expect(requestMock.get).toHaveBeenNthCalledWith(2, '/process-routes/81')
    expect(requestMock.post).toHaveBeenNthCalledWith(1, '/projects/7/process-routes', payload)
    expect(requestMock.post).toHaveBeenNthCalledWith(2, '/process-routes/81/freeze')
  })

  it('queries process route templates with product code filters', async () => {
    const template = {
      routeTemplateCode: 'ROUTE-STD-INJECTION',
      routeTemplateName: '标准注塑组装路线',
      versionNo: 'V1',
      operations: []
    }
    requestMock.get.mockReturnValueOnce(apiResponse([template]))

    await expect(getProcessRouteTemplates({ productCode: 'PRD-9', onlyDefault: true })).resolves.toEqual([template])

    expect(requestMock.get).toHaveBeenCalledWith('/process-route-templates', {
      params: { productCode: 'PRD-9', onlyDefault: true }
    })
  })

  it('uses process operation master endpoints as selectable process library', async () => {
    const master = {
      processId: 900,
      processCode: 'PROC_INJECTION',
      processName: '注塑成型',
      processCategory: 'forming',
      operationType: 'process',
      defaultStandardTimeMins: 12,
      defaultQualityRequirement: '外观无缩水和披锋',
      defaultProcessParamJson: '{"temperature":82}',
      status: 'confirmed'
    }
    const payload = {
      processCode: 'PROC_TEST_PRESS',
      processName: '测试压合',
      processCategory: 'assembly',
      operationType: 'process',
      defaultStandardTimeMins: 8,
      defaultQualityRequirement: '无偏位',
      defaultProcessParamJson: '{"pressure":30}'
    }
    requestMock.get.mockReturnValueOnce(apiResponse([master]))
    requestMock.post.mockReturnValueOnce(apiResponse(master))

    await expect(getProcessOperationMasters({ keyword: '注塑', status: 'confirmed' })).resolves.toEqual([master])
    await expect(createProcessOperationMaster(payload)).resolves.toEqual(master)

    expect(requestMock.get).toHaveBeenCalledWith('/process-operation-masters', {
      params: { keyword: '注塑', status: 'confirmed' }
    })
    expect(requestMock.post).toHaveBeenCalledWith('/process-operation-masters', payload)
  })

  it('uploads timeline attachment as FormData and queries by backend nodeKey', async () => {
    const attachment = { attachmentId: 91, originalFileName: '报告.txt' }
    requestMock.post.mockReturnValue(apiResponse(attachment))
    requestMock.get
      .mockReturnValueOnce(apiResponse([attachment]))
      .mockReturnValueOnce(apiResponse({ content: [attachment], page: 1, size: 20, totalElements: 1, totalPages: 1 }))

    const file = new File(['M4'], '报告.txt', { type: 'text/plain' })
    await expect(uploadTimelineAttachment(7, 'sampling-process', file, {
      fileCategory: 'testing',
      versionNo: 'V1',
      remark: '前端联调'
    })).resolves.toEqual(attachment)
    await getTimelineAttachments(7, 'sampling-process')
    await getFileCenterAttachments({ projectId: 7, nodeKey: 'sampling-process', page: 1, size: 20 })

    const [url, body] = requestMock.post.mock.calls[0]
    expect(url).toBe('/projects/7/timeline/sampling-process/attachments')
    expect(body).toBeInstanceOf(FormData)
    expect(body.get('file')).toBe(file)
    expect(body.get('fileCategory')).toBe('testing')
    expect(body.get('versionNo')).toBe('V1')
    expect(body.get('remark')).toBe('前端联调')
    expect(requestMock.get).toHaveBeenNthCalledWith(1, '/projects/7/timeline/sampling-process/attachments')
    expect(requestMock.get).toHaveBeenNthCalledWith(2, '/file-center/attachments', {
      params: { projectId: 7, nodeKey: 'sampling-process', page: 1, size: 20 }
    })
  })

  it('downloads binary content and deletes by attachment id', async () => {
    const blob = new Blob(['M4'], { type: 'text/plain' })
    requestMock.get.mockResolvedValue({ data: blob })
    requestMock.delete.mockReturnValue(apiResponse(null))

    await expect(downloadAttachment(91)).resolves.toBe(blob)
    await deleteAttachment(91)

    expect(requestMock.get).toHaveBeenCalledWith('/attachments/91/download', { responseType: 'blob' })
    expect(requestMock.delete).toHaveBeenCalledWith('/attachments/91')
  })
})

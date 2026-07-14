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
  getBomDetail,
  getProjectBoms
} from '../bom'
import {
  createProcessRoute,
  freezeProcessRoute,
  getProcessRouteDetail,
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

  it('sends complete process route payloads to real endpoints', async () => {
    const route = { processId: 81, productId: 7, status: 'draft', operations: [] }
    const payload = {
      processName: '样品工艺路线',
      versionNo: 'A',
      remark: 'M4',
      operations: [{
        sequenceNo: 10,
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

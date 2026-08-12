import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  get: vi.fn()
}))

vi.mock('../../request', () => ({
  default: requestMock,
  unwrapResponse: <T>(response: { data: { data: T } }) => response.data.data
}))

import { getInventoryCenterSnapshot } from '../foundation'

function apiResponse<T>(data: T) {
  return Promise.resolve({ data: { code: 0, message: 'success', data } })
}

describe('inventory center API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uses Inventory center snapshot endpoint instead of frontend mock data', async () => {
    const snapshot = {
      tree: [{ nodeId: 'all', label: '全部物料', nodeType: 'category', children: [] }],
      items: []
    }
    requestMock.get.mockReturnValue(apiResponse(snapshot))

    await expect(getInventoryCenterSnapshot()).resolves.toEqual(snapshot)
    expect(requestMock.get).toHaveBeenCalledWith('/inventories/center-snapshot')
  })
})

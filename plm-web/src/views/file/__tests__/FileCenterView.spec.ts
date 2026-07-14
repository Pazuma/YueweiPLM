import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const attachmentApi = vi.hoisted(() => ({
  getFileCenterAttachments: vi.fn(),
  downloadAttachment: vi.fn(),
  deleteAttachment: vi.fn()
}))
const foundationApi = vi.hoisted(() => ({ getFileSections: vi.fn() }))

vi.mock('@/api/modules/attachment', () => attachmentApi)
vi.mock('@/api/modules/foundation', () => foundationApi)
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

import FileCenterView from '../FileCenterView.vue'

describe('FileCenterView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    foundationApi.getFileSections.mockResolvedValue([])
    attachmentApi.getFileCenterAttachments.mockResolvedValue({
      content: [],
      page: 1,
      size: 20,
      totalElements: 0,
      totalPages: 0
    })
  })

  it('loads the real file-center page and shows a successful empty state', async () => {
    const wrapper = mount(FileCenterView, { global: { plugins: [ElementPlus] } })

    await flushPromises()

    expect(attachmentApi.getFileCenterAttachments).toHaveBeenCalledWith({ page: 1, size: 20 })
    expect(foundationApi.getFileSections).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('当前筛选条件下没有文件')
  })

  it('renders attachment metadata returned by the backend', async () => {
    attachmentApi.getFileCenterAttachments.mockResolvedValue({
      content: [{
        attachmentId: 91,
        ownerObjectType: 'Product',
        ownerObjectId: 7,
        timelineNodeKey: 'sampling-process',
        fileCategory: 'testing',
        fileName: 'M4测试报告.txt',
        originalFileName: 'M4测试报告.txt',
        fileExt: 'txt',
        contentType: 'text/plain',
        fileSize: 1024,
        checksum: 'abc',
        storageType: 'local',
        storageKey: 'projects/7/report.txt',
        versionNo: 'V1',
        status: 'draft',
        remark: '联调',
        createdAt: '2026-07-14T12:00:00',
        createdBy: 'engineer01'
      }],
      page: 1,
      size: 20,
      totalElements: 1,
      totalPages: 1
    })

    const wrapper = mount(FileCenterView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('M4测试报告.txt')
    expect(wrapper.text()).toContain('sampling-process')
    expect(wrapper.text()).toContain('1 KB')
  })
})

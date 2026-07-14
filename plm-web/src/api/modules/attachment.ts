import request, { unwrapPage, unwrapResponse, type PageResponse } from '../request'

/* ========== 时间轴节点附件（文档 7.6） ========== */

export interface AttachmentVO {
  attachmentId: number
  ownerObjectType: string
  ownerObjectId: number
  timelineNodeKey?: string | null
  fileCategory: string
  fileName: string
  originalFileName: string
  fileExt: string
  contentType?: string | null
  fileSize: number
  checksum: string
  storageType: string
  storageKey: string
  versionNo: string
  status: string
  remark?: string | null
  createdAt: string
  createdBy: string
}

export interface TimelineAttachmentMetadata {
  fileCategory: string
  versionNo?: string
  remark?: string
}

export interface FileCenterQuery {
  keyword?: string
  projectId?: number
  nodeKey?: string
  fileCategory?: string
  page?: number
  size?: number
}

/** POST /api/v1/projects/{projectId}/timeline/{nodeKey}/attachments */
export function uploadTimelineAttachment(
  projectId: number,
  nodeKey: string,
  file: File,
  metadata: TimelineAttachmentMetadata
) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('fileCategory', metadata.fileCategory)
  if (metadata.versionNo) formData.append('versionNo', metadata.versionNo)
  if (metadata.remark) formData.append('remark', metadata.remark)

  return request
    .post(`/projects/${projectId}/timeline/${nodeKey}/attachments`, formData)
    .then((response) => unwrapResponse<AttachmentVO>(response))
}

/** GET /api/v1/projects/{projectId}/timeline/{nodeKey}/attachments */
export async function getTimelineAttachments(projectId: number, nodeKey: string): Promise<AttachmentVO[]> {
  const response = await request.get(`/projects/${projectId}/timeline/${nodeKey}/attachments`)
  return unwrapResponse<AttachmentVO[]>(response)
}

/** GET /api/v1/file-center/attachments */
export async function getFileCenterAttachments(params: FileCenterQuery = {}): Promise<PageResponse<AttachmentVO>> {
  const response = await request.get('/file-center/attachments', { params })
  return unwrapPage<AttachmentVO>(response)
}

/** GET /api/v1/attachments/{attachmentId} */
export async function getAttachmentDetail(attachmentId: number): Promise<AttachmentVO> {
  const response = await request.get(`/attachments/${attachmentId}`)
  return unwrapResponse<AttachmentVO>(response)
}

/** GET /api/v1/attachments/{attachmentId}/download */
export async function downloadAttachment(attachmentId: number): Promise<Blob> {
  const response = await request.get<Blob>(`/attachments/${attachmentId}/download`, { responseType: 'blob' })
  return response.data
}

/** DELETE /api/v1/attachments/{attachmentId} */
export async function deleteAttachment(attachmentId: number) {
  const response = await request.delete(`/attachments/${attachmentId}`)
  return unwrapResponse<void>(response)
}

import { mockResolve } from '../request'

export function uploadAttachment(fileName: string) {
  return mockResolve(() => ({
    attachmentId: Date.now(),
    fileName
  }), 300)
}

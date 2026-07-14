import { AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it } from 'vitest'

import { prepareRequestContentType } from '../request'

function configWith(data: unknown): InternalAxiosRequestConfig {
  return {
    data,
    headers: new AxiosHeaders({ 'Content-Type': 'application/json' })
  } as InternalAxiosRequestConfig
}

describe('prepareRequestContentType', () => {
  it('removes fixed JSON content type for FormData', () => {
    const config = configWith(new FormData())

    prepareRequestContentType(config)

    expect(config.headers.has('Content-Type')).toBe(false)
  })

  it('keeps JSON content type for object payloads', () => {
    const config = configWith({ name: 'M4' })

    prepareRequestContentType(config)

    expect(config.headers.get('Content-Type')).toBe('application/json')
  })
})

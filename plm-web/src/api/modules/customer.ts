import { customerDetails, customerList } from '@/mock/data'
import type { CustomerDetail } from '@/types/customer'
import { mockResolve } from '../request'

function clone<T>(value: T): T {
  return structuredClone(value)
}

export function getCustomerList() {
  return mockResolve(() => clone(customerList))
}

export function getCustomerDetail(customerId: number) {
  return mockResolve(() => {
    const detail = customerDetails[customerId]
    if (!detail) {
      throw new Error('客户不存在')
    }
    return clone(detail)
  })
}

export function createCustomer(payload: Partial<CustomerDetail>) {
  return mockResolve(() => {
    const nextId = Math.max(...customerList.map((item) => item.customerId)) + 1
    const detail: CustomerDetail = {
      customerId: nextId,
      customerCode: payload.customerCode || `CUS-AUTO-${nextId}`,
      customerName: payload.customerName || '未命名客户',
      customerShortName: payload.customerShortName || 'NEW',
      countryCode: payload.countryCode || 'CN',
      contactName: payload.contactName || '待补充',
      contactPhone: payload.contactPhone || '待补充',
      contactEmail: payload.contactEmail || 'pending@example.com',
      status: 'draft',
      updatedAt: new Date().toISOString(),
      address: payload.address || '待补充',
      sourceType: payload.sourceType || '客户来源待补充',
      ownerUserName: payload.ownerUserName || '当前用户',
      relatedOrders: [],
      operationLogs: [
        {
          time: new Date().toISOString(),
          operator: '当前用户',
          action: '创建客户档案。',
          level: 'normal'
        }
      ]
    }

    customerList.unshift({
      customerId: detail.customerId,
      customerCode: detail.customerCode,
      customerName: detail.customerName,
      customerShortName: detail.customerShortName,
      countryCode: detail.countryCode,
      contactName: detail.contactName,
      contactPhone: detail.contactPhone,
      contactEmail: detail.contactEmail,
      status: detail.status,
      updatedAt: detail.updatedAt
    })
    customerDetails[nextId] = detail

    return clone(detail)
  }, 240)
}

export function updateCustomer(customerId: number, payload: Partial<CustomerDetail>) {
  return mockResolve(() => {
    const detail = customerDetails[customerId]
    const summary = customerList.find((item) => item.customerId === customerId)

    if (!detail || !summary) {
      throw new Error('客户不存在')
    }

    Object.assign(detail, payload, {
      updatedAt: new Date().toISOString()
    })
    detail.operationLogs.unshift({
      time: new Date().toISOString(),
      operator: payload.ownerUserName || detail.ownerUserName,
      action: '更新客户资料。',
      level: 'normal'
    })

    Object.assign(summary, {
      customerCode: detail.customerCode,
      customerName: detail.customerName,
      customerShortName: detail.customerShortName,
      countryCode: detail.countryCode,
      contactName: detail.contactName,
      contactPhone: detail.contactPhone,
      contactEmail: detail.contactEmail,
      status: detail.status,
      updatedAt: detail.updatedAt
    })

    return clone(detail)
  }, 240)
}

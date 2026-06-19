import { mockResolve } from '@/api/request'
import type { ReportCenterSnapshot } from '@/types/foundation'

function clone<T>(value: T): T {
  return structuredClone(value)
}

const reportCenterSnapshot: ReportCenterSnapshot = {
  rangeLabel: '2026年6月',
  cards: [
    {
      key: 'development',
      title: '开发进度报表',
      icon: 'DataAnalysis',
      questionLines: ['当前多少产品在开发？', '哪些阶段卡住了？', '有没有逾期项目？'],
      targetPath: '/reports?report=development'
    },
    {
      key: 'mold',
      title: '模具状态报表',
      icon: 'Tools',
      questionLines: ['模具都在什么状态？', '哪些快到期还没验收？', '试模成功率如何？'],
      targetPath: '/reports?report=mold'
    },
    {
      key: 'cost',
      title: '成本分析报表',
      icon: 'Money',
      questionLines: ['各产品成本是多少？', '预计和实际差多少？', '哪个环节超了？'],
      targetPath: '/reports?report=cost'
    }
  ],
  details: [
    {
      key: 'development',
      title: '开发进度报表',
      summary: '先看逾期与卡点，再看阶段分布。',
      metrics: [
        {
          key: 'project_setup',
          label: '立项中',
          value: '6',
          hint: '平均停留 5 天',
          targetPath: '/products?report_status=project_setup',
          detailTitle: '立项中的项目列表',
          detailSummary: '当前停留在立项确认、立项审批或立项资料补充阶段的项目。',
          detailItems: [
            {
              itemId: 'dev-setup-101',
              title: '超队 3.0',
              subtitle: '产品线 / 北美渠道 A',
              owner: '张经理',
              currentNode: '产品立项',
              durationText: '停留 4 天',
              riskText: '供应商比价还差最后一家未回。',
              targetPath: '/products/101'
            },
            {
              itemId: 'dev-setup-106',
              title: '护甲 Air 2.0',
              subtitle: '产品线 / 欧洲渠道 B',
              owner: '陈工',
              currentNode: '确认立项',
              durationText: '停留 6 天',
              riskText: '预计成本还未和管理层确认。',
              targetPath: '/products'
            },
            {
              itemId: 'dev-setup-107',
              title: '亮甲 3.0 iPhone19 紫色',
              subtitle: '新型号 / 日本零售客户',
              owner: '李工程',
              currentNode: '需求确认',
              durationText: '停留 3 天',
              targetPath: '/products/104'
            }
          ]
        },
        {
          key: 'mold_stage',
          label: '模具阶段',
          value: '4',
          hint: '平均停留 15 天',
          targetPath: '/products?report_status=mold_stage',
          detailTitle: '模具阶段项目列表',
          detailSummary: '当前处于申请开模、制作模具、试模或改模确认中的项目。',
          detailItems: [
            {
              itemId: 'dev-mold-102',
              title: '超队 3.0 iPhone18 黑色',
              subtitle: '新型号 / 改模分支',
              owner: '刘浩',
              currentNode: '改模申请',
              durationText: '停留 2 天',
              riskText: '磁吸力验证还未通过。',
              targetPath: '/products/102'
            },
            {
              itemId: 'dev-mold-108',
              title: '战甲 Pro 1.0',
              subtitle: '产品线 / 美国 KA 客户',
              owner: '王工',
              currentNode: '制作模具',
              durationText: '停留 13 天',
              targetPath: '/products'
            }
          ]
        },
        {
          key: 'semi_finished_stage',
          label: '半成品阶段',
          value: '8',
          hint: '平均停留 10 天',
          targetPath: '/products?report_status=semi_finished_stage',
          detailTitle: '半成品阶段项目列表',
          detailSummary: '当前已进入工艺验证、组件确认或半成品打样阶段的项目。',
          detailItems: [
            {
              itemId: 'dev-semi-103',
              title: '超队 3.0 iPhone18 蓝色',
              subtitle: '新型号 / 市场储备',
              owner: '赵工',
              currentNode: '样品确认',
              durationText: '停留 5 天',
              targetPath: '/products'
            },
            {
              itemId: 'dev-semi-109',
              title: '亮甲 3.0 iPhone18 金色',
              subtitle: '新型号 / 日本零售客户',
              owner: '周主管',
              currentNode: '组件成品确认',
              durationText: '停留 8 天',
              riskText: '包材版本还未冻结。',
              targetPath: '/products'
            }
          ]
        }
      ],
      alerts: [
        { title: '超队 3.0 iPhone18 黑色', subtitle: '差异测试卡住 2 天', owner: '张经理', level: 'medium', targetPath: '/products/102' },
        { title: '亮甲 3.0', subtitle: '版本已发布，可复用', owner: '李工程', level: 'low', targetPath: '/products/104' }
      ],
      distribution: [
        { label: '立项中', value: 6, hint: '平均 5 天' },
        { label: '模具', value: 4, hint: '平均 15 天' },
        { label: '半成品', value: 8, hint: '平均 10 天' }
      ]
    },
    {
      key: 'mold',
      title: '模具状态报表',
      summary: '看逾期模具、供应商周期和试模成功率。',
      metrics: [
        {
          key: 'tooling_opening',
          label: '开模中',
          value: '3',
          hint: '待开模验收',
          targetPath: '/inventories?report_status=tooling_opening',
          detailTitle: '开模中的模具列表',
          detailSummary: '当前已发起开模或改模，尚未进入正式验收的模具项目。',
          detailItems: [
            {
              itemId: 'mold-opening-218',
              title: 'iPhone18 改模注塑模',
              subtitle: '超队 3.0 iPhone18 黑色',
              owner: '东莞模具 C',
              currentNode: '改模中',
              durationText: '停留 6 天',
              riskText: '预计后天完成首轮试模。',
              targetPath: '/inventories'
            },
            {
              itemId: 'mold-opening-301',
              title: '战甲 Pro 外壳模',
              subtitle: '战甲 Pro 1.0',
              owner: '惠州模具 A',
              currentNode: '开模中',
              durationText: '停留 11 天',
              targetPath: '/inventories'
            }
          ]
        },
        {
          key: 'tooling_trial',
          label: '试模中',
          value: '5',
          hint: '当前重点跟进',
          targetPath: '/inventories?report_status=tooling_trial',
          detailTitle: '试模中的模具列表',
          detailSummary: '已进入试模验证，重点关注样品精度、孔位和验收节奏。',
          detailItems: [
            {
              itemId: 'mold-trial-201',
              title: '超队 3.0 注塑模',
              subtitle: '超队 3.0',
              owner: '东莞模具 C',
              currentNode: '测试模具',
              durationText: '停留 3 天',
              riskText: '酒精测试对应外观件仍在同步验证。',
              targetPath: '/products/101'
            },
            {
              itemId: 'mold-trial-402',
              title: '亮甲 3.0 镜面贴合治具',
              subtitle: '亮甲 3.0',
              owner: '东莞模具 C',
              currentNode: '试模确认',
              durationText: '停留 5 天',
              targetPath: '/inventories'
            }
          ]
        },
        {
          key: 'tooling_accepted',
          label: '已验收',
          value: '18',
          hint: '可复用',
          targetPath: '/inventories?report_status=tooling_accepted',
          detailTitle: '已验收模具列表',
          detailSummary: '已完成验收并可投入后续生产、复用或运模流程的模具与治具。',
          detailItems: [
            {
              itemId: 'mold-accepted-233',
              title: '亮甲 3.0 注塑模',
              subtitle: '亮甲 3.0',
              owner: '东莞模具 C',
              currentNode: 'MX 验收完成',
              durationText: '完成 18 天',
              targetPath: '/inventories'
            },
            {
              itemId: 'mold-accepted-011',
              title: '镜面贴合治具',
              subtitle: '亮甲 3.0',
              owner: '东莞模具 C',
              currentNode: '可复用',
              durationText: '完成 20 天',
              targetPath: '/inventories'
            }
          ]
        }
      ],
      alerts: [
        { title: 'INV-MOLD-218', subtitle: 'iPhone18 改模注塑模正在验证', owner: '东莞模具 C', level: 'high', targetPath: '/products/102' }
      ],
      distribution: [
        { label: '开模中', value: 3, hint: '平均 12 天' },
        { label: '试模中', value: 5, hint: '平均 7 天' },
        { label: '已验收', value: 18, hint: '稳定' }
      ]
    },
    {
      key: 'cost',
      title: '成本分析报表',
      summary: '先看偏差最大的产品，再看成本构成。',
      metrics: [
        {
          key: 'cost-chaodui',
          label: '超队 3.0',
          value: '¥32,300',
          hint: '比预计低 3.2%',
          targetPath: '/products/101',
          detailTitle: '超队 3.0 成本明细',
          detailSummary: '当前围绕超队 3.0 的版本、成本差异和主要构成项。',
          detailItems: [
            {
              itemId: 'cost-cd30-a3',
              title: 'A.3 当前版本',
              subtitle: '超队 3.0 / 正在评审',
              owner: '工程部',
              currentNode: '红样测试',
              durationText: '实际 ¥32.3 / 预计 ¥35.0',
              riskText: '酒精测试复测可能影响喷油工艺成本。',
              targetPath: '/products/101'
            },
            {
              itemId: 'cost-cd30-a2',
              title: 'A.2 归档版本',
              subtitle: '超队 3.0 / 历史版本',
              owner: '工程部',
              currentNode: '已归档',
              durationText: '实际 ¥35.4',
              targetPath: '/products/101'
            }
          ]
        },
        {
          key: 'cost-liangjia',
          label: '亮甲 3.0',
          value: '¥28,500',
          hint: '比预计低 7.5%',
          targetPath: '/products/104',
          detailTitle: '亮甲 3.0 成本明细',
          detailSummary: '当前围绕亮甲 3.0 的正式发布版本和主要节省项。',
          detailItems: [
            {
              itemId: 'cost-lj30-b1',
              title: 'B.1 发布版本',
              subtitle: '亮甲 3.0 / 已发布',
              owner: '工程部',
              currentNode: '正式发布',
              durationText: '实际 ¥33.5 / 预计 ¥36.2',
              riskText: '镜面件替换后单价下降明显。',
              targetPath: '/products/104'
            }
          ]
        },
        {
          key: 'cost-average',
          label: '单品均摊',
          value: '¥2.62',
          hint: '含模具分摊',
          targetPath: '/costs',
          detailTitle: '单品均摊构成列表',
          detailSummary: '当前均摊口径下，单品成本由哪些项目共同构成。',
          detailItems: [
            {
              itemId: 'cost-average-tooling',
              title: '模具分摊',
              subtitle: '按当前量产预测分摊',
              owner: '财务 / 工程',
              currentNode: '已测算',
              durationText: '均摊 ¥1.08',
              targetPath: '/costs'
            },
            {
              itemId: 'cost-average-material',
              title: '材料分摊',
              subtitle: '主材 + 功能件 + 包材',
              owner: '采购 / 工程',
              currentNode: '已测算',
              durationText: '均摊 ¥1.54',
              targetPath: '/costs'
            }
          ]
        }
      ],
      alerts: [
        { title: '超队 3.0', subtitle: '成本已进入冻结确认', owner: '工程', level: 'low', targetPath: '/products/101' }
      ],
      distribution: [
        { label: '模具', value: 80, hint: '占比最高' },
        { label: '材料', value: 70, hint: '稳定' },
        { label: '测试', value: 18, hint: '正常' }
      ]
    }
  ]
}

export function getReportCenterSnapshot() {
  return mockResolve(() => clone(reportCenterSnapshot))
}

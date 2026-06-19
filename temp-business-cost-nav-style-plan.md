# 41-商务区父子层级展开样式前端优化方案

## 1. 本次需求理解

这次你要调整的重点，不是商务区的数据内容，也不是展开逻辑本身，而是展开后的视觉气质。

你现在补充得很明确，目标效果要尽量靠近左侧栏当前这种展开方式：

1. 看起来舒服
2. 层级清楚
3. 展开很自然
4. 不要现在这种突兀的蓝色边框

也就是说，商务区的展开态不应该继续走“蓝框强调”的方向，而应该改成更像左侧栏那种：

- 靠背景深浅变化表达状态
- 靠父子层级表达结构
- 靠字重和缩进表达从属关系

本次只输出方案文档，不修改项目代码。

---

## 2. 当前问题判断

从当前页面效果看，商务区已经能做到：

- 点击父项展开
- 显示对应成本明细
- 展开态有明显状态变化

但是当前视觉问题主要有两个：

### 2.1 展开态太像“被蓝框框起来”

当前的展开感主要来自：

- 外层蓝色边框
- 整块区域被强行高亮

这会带来一种偏“硬”的视觉感受。

问题在于：

- 商务区本质是阅读区，不是强操作区
- 这种蓝框太抢眼
- 和页面整体浅灰、柔和的风格不够协调

### 2.2 层级关系还不够像左侧栏那种自然展开

现在展开后更像：

- 一张卡片
- 下面追加了几行内容

而不是像左侧栏那样：

- 上面是父节点
- 下面是展开后的子项
- 子项天然从属于父节点

所以这次优化的核心不是“更明显”，而是“更自然、更舒服”。

---

## 3. 目标效果

商务区建议改成一种“轻量父子层级展开”的样子。

### 3.1 父节点效果

例如：

- 预计成本
- 实际成本

它们是父节点。

父节点展开时应该让人感受到：

- 已经被打开
- 但不是靠强烈蓝框提醒
- 而是靠背景略深、字重略强、状态箭头明确来表达

这种效果要尽量接近左侧栏当前展开组的感觉：

- 有状态变化
- 但不刺眼
- 看着顺

### 3.2 子节点效果

例如：

- 材料成本
- 模具成本
- 工艺加工
- 包装测试

这些应该像父节点下面挂出来的子项。

子项建议表现为：

- 字体更小
- 颜色更轻
- 有缩进
- 有轻量分隔
- 有一条很淡的引导线或层级线

这样用户会更自然地理解：

- 上面是总项
- 下面是分项

---

## 4. 推荐视觉方向

这次我建议不要把商务区理解成“卡片展开”，而要理解成“业务面板里的父子折叠项”。

也就是借用左侧栏的舒服感，但不完全照搬左侧栏样式。

### 4.1 重点保留

建议保留：

- 父节点点击展开
- 同时只展开一个父节点
- 父节点和子节点的结构关系

### 4.2 重点调整

建议重点调整：

1. 去掉当前这种突兀的蓝框感
2. 改成浅灰蓝或浅灰背景层次
3. 让父节点像左侧栏展开组标题
4. 让子节点像左侧栏展开后的子项，但更适合阅读

---

## 5. 模板层建议

相关文件：

- `plm-web/src/views/product/ProductDetail.vue`

当前结构本身已经够用，不需要大改。

建议只是把子节点区域再明确一层语义容器，让后续样式更好控制：

```vue
<section class="cost-card" :class="{ 'is-expanded': expandedCost === 'estimated' }">
  <button class="cost-card__summary" type="button" @click="toggleCostPanel('estimated')">
    ...
  </button>

  <div v-if="expandedCost === 'estimated'" class="cost-card__detail">
    <div class="cost-card__children">
      <div
        v-for="line in presentation.costPanel.estimatedLines || []"
        :key="line.label"
        class="cost-line"
      >
        <div class="cost-line__content">
          <strong>{{ line.label }}</strong>
          <p class="subtle-text">{{ line.note }}</p>
        </div>
        <strong class="cost-line__amount">{{ formatAmount(line.amount) }}</strong>
      </div>
    </div>
  </div>
</section>
```

建议补出的类名：

- `.cost-card__children`
- `.cost-line__content`
- `.cost-line__amount`

这次不是为了改逻辑，而是为了更清晰地控制“父节点”和“子节点”的视觉层次。

---

## 6. 样式层建议

## 6.1 父节点：弱化蓝框，改成柔和展开态

父节点建议像左侧栏的展开组一样，通过背景和字重变化表达状态，而不是靠明显蓝边。

推荐方向：

```css
.cost-card {
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 10px;
  background: #ffffff;
  box-shadow: none;
  transition: background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.cost-card.is-expanded {
  border-color: rgba(203, 213, 225, 0.72);
  background: #f8fafc;
  box-shadow: 0 8px 18px rgba(148, 163, 184, 0.08);
}

.cost-card__summary {
  background: transparent;
  transition: background 0.2s ease;
}

.cost-card.is-expanded .cost-card__summary {
  background: rgba(148, 163, 184, 0.12);
}

.cost-card.is-expanded .cost-card__summary > div strong {
  color: #0f172a;
}

.cost-card.is-expanded .cost-card__summary-right strong {
  color: #0f172a;
}
```

这套方向的重点是：

- 父节点有变化
- 但变化来自柔和背景
- 而不是一圈强烈蓝框

这会比现在更接近左侧栏那种“舒服的打开方式”。

---

## 6.2 子节点：做成舒服的下挂明细

子节点建议不要继续像普通正文块，而要像“挂在父节点下面的子项”。

推荐方向：

```css
.cost-card__detail {
  padding: 10px 16px 14px 18px;
  background: #f8fafc;
  border-top: 1px solid rgba(226, 232, 240, 0.9);
}

.cost-card__children {
  position: relative;
  padding-left: 12px;
}

.cost-card__children::before {
  content: '';
  position: absolute;
  left: 0;
  top: 4px;
  bottom: 4px;
  width: 1px;
  background: rgba(148, 163, 184, 0.28);
}

.cost-line {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-top: 1px solid rgba(226, 232, 240, 0.88);
}

.cost-line:first-child {
  border-top: 0;
}

.cost-line__content strong {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.cost-line__content .subtle-text {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.45;
  color: #64748b;
}

.cost-line__amount {
  font-size: 13px;
  color: #0f172a;
}
```

这里有几个关键点：

1. 子节点字体更小
2. 子节点说明更轻
3. 子节点有缩进
4. 子节点之间靠轻分隔线区分
5. 左侧淡淡一条引导线，建立父子关系

这样会明显比现在“蓝框里一堆内容”更协调。

---

## 7. 交互建议

### 7.1 展开箭头替代“展开/收起”主视觉

当前右侧如果主要靠“展开 / 收起”文字，会偏硬。

建议改成：

- 箭头为主
- 文字为辅

例如：

- 未展开：向下箭头
- 已展开：向上箭头

这会更像左侧栏当前展开组的感觉。

### 7.2 保持一次只展开一个父节点

这一点建议继续保留现在的逻辑：

- `expandedCost` 只能展开一个父项

这是对的。

如果两个都一起展开，商务区会重新变乱，舒服感会下降。

---

## 8. 修改后的页面效果预期

如果按这份方案改，商务区会从现在这种感觉：

- 有一个比较显眼的蓝框
- 展开后视觉偏硬
- 明细像被塞进框里的内容

变成这种感觉：

- 父节点像一个自然展开的分组标题
- 展开后下面挂出子项
- 父节点背景略深，但不过度强调
- 子节点字体更小、更轻、更顺
- 整个展开态更像左侧栏的舒服感

这样用户看过去时，会觉得它是“自然展开的结构”，不是“被蓝框强调的一块区域”。

---

## 9. 推荐修改文件

如果后面确认开发，建议只改这一处：

- `plm-web/src/views/product/ProductDetail.vue`

本次不需要动：

- 后端接口
- mock 数据结构
- 其他页面
- 左侧栏逻辑

这是一个很收敛的前端样式和模板层优化。

---

## 10. 最终建议结论

这次商务区优化，我建议明确换一个方向：

不要再继续强化蓝框，
而是改成像左侧栏那样的“柔和展开”。

具体落地方向就是：

1. 父节点保留头部结构
2. 父节点展开时只做背景轻加深
3. 去掉突兀的强蓝框感
4. 子节点改成缩进的小号明细项
5. 用淡引导线、留白、字重差异建立层级

这样出来的效果会更协调，也更符合你说的那个重点：

“打开方式给人看着很舒服。”

本次只完成方案文档更新，不修改项目代码。

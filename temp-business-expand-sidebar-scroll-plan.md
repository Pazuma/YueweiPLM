# 40-商务区展开态强化与左侧栏固定布局前端优化方案

## 1. 本次需求理解

这次要补充和统一的是 3 个前端体验优化点，当前都只停留在文档方案阶段，不修改项目代码：

1. 商务区展开后的明细不够明显，需要更直观的展开态表现。
2. 首页布局希望左侧栏固定，只有右侧内容区滚动。
3. 左侧栏样式希望改成“与背景融合的一体式导航”，保留原有导航内容，只调整视觉样式，不改导航信息结构。

这 3 个点本质上都在解决同一类问题：页面层级不够清晰，用户不容易一眼判断“哪里是导航、哪里是当前选中、哪里是已经展开的详细内容”。

---

## 2. 当前代码位置理解

### 2.1 商务区展开态

相关文件：

- `plm-web/src/views/product/ProductDetail.vue`

当前关键结构：

- `expandedCost`
- `toggleCostPanel(type)`
- `.cost-card`
- `.cost-card__summary`
- `.cost-card__detail`
- `.cost-line`

说明：

当前交互逻辑已经具备，点击后也确实能展开，但视觉反馈偏弱。用户会觉得只是“多出来一段内容”，而不是“当前这张卡已经进入展开明细状态”。

### 2.2 左侧栏固定与右侧滚动

相关文件：

- `plm-web/src/layout/index.vue`
- `plm-web/src/layout/ContentArea.vue`
- `plm-web/src/layout/Sidebar.vue`

当前关键结构：

- `layout/index.vue` 负责整体双栏布局
- `ContentArea.vue` 负责右侧主内容容器
- `Sidebar.vue` 负责左侧导航栏

说明：

目前布局已经是左右结构，但滚动边界还不够明确，需要进一步把“整页滚动”调整为“右侧内容区独立滚动”。

### 2.3 左侧栏视觉样式

相关文件：

- `plm-web/src/layout/Sidebar.vue`
- 如有全局背景联动，可补充调整 `plm-web/src/layout/index.vue`

说明：

当前左侧栏更接近“分组卡片式导航”，每个导航组的块感比较重。你这次希望改成更贴背景的一体式侧边导航，也就是：

- 保留原有菜单内容和分组
- 让整个左侧栏更像系统框架的一部分
- 弱化一块一块白底卡片的感觉
- 强化当前选中项和分组展开态

这次改的是样式，不是改导航结构。

---

## 3. 问题判断

### 3.1 商务区展开态的问题本质

不是功能缺失，而是状态表达不够强。

用户现在虽然能点击，也能看到展开结果，但展开前后层级变化不明显，导致：

- 不容易意识到当前哪一块已经展开
- 不容易区分摘要区和明细区
- 长时间浏览后，容易找不到视觉重心

### 3.2 左侧栏滚动的问题本质

不是布局结构错误，而是滚动容器边界不清晰。

理想状态应该是：

- 左侧导航固定
- 顶部栏保持在右侧主区顶部
- 右侧内容区作为唯一主滚动容器

这样更适合 PLM 这种页面长、模块多、信息密度高的系统。

### 3.3 左侧栏样式的问题本质

不是菜单内容有问题，而是视觉风格偏“卡片堆叠”，不够轻，不够统一。

你想要的方向更像：

- 导航背景与系统底色自然融合
- 分组和菜单项更扁平
- 选中态更清楚
- 展开后的子项更像同一套导航体系的延伸

---

## 4. 优化方案一：商务区展开态强化

## 4.1 目标

让用户点击后，不只是看到内容出现，还能明显感受到“当前卡片进入了展开状态”。

## 4.2 建议改法

在现有逻辑不变的前提下，给展开卡片增加一个激活态类名，例如：

- `.is-expanded`

然后围绕展开态做 4 类强化：

1. 外层边框加深
2. 卡片头部背景微亮
3. 明细区使用单独底色
4. 展开按钮增加箭头方向变化或状态提示

## 4.3 建议代码写法

文件：

- `plm-web/src/views/product/ProductDetail.vue`

模板可按这个方向改：

```vue
<section
  class="cost-card"
  :class="{ 'is-expanded': expandedCost === 'estimated' }"
>
```

```vue
<section
  class="cost-card"
  :class="{ 'is-expanded': expandedCost === 'actual' }"
>
```

样式建议：

```css
.cost-card.is-expanded {
  border-color: #2563eb;
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.14);
  background: #ffffff;
}

.cost-card.is-expanded .cost-card__summary {
  background: rgba(37, 99, 235, 0.08);
}

.cost-card.is-expanded .cost-card__summary-right strong {
  color: #1d4ed8;
}

.cost-card__detail {
  padding: 12px 16px 16px;
  background: #f5f9ff;
  border-top: 1px solid rgba(37, 99, 235, 0.16);
}

.cost-line {
  padding: 14px 0;
}
```

## 4.4 修改后效果

修改后用户会更容易感知到：

- 哪张卡片已经被打开
- 展开出来的是明细区域，不是普通正文
- 当前关注焦点在展开卡片本身

---

## 5. 优化方案二：左侧栏固定，只有右侧内容区滚动

## 5.1 目标

把页面调整成更标准的后台工作台结构：

- 左侧导航固定
- 右侧内容滚动
- 长页面浏览时导航位置不乱跑

## 5.2 建议改法

### 第一步：锁定页面主壳高度

文件：

- `plm-web/src/layout/index.vue`

建议：

```css
.layout-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.layout-shell__main {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
}
```

### 第二步：把右侧内容区变成唯一主滚动容器

文件：

- `plm-web/src/layout/ContentArea.vue`

建议：

```css
.content-area {
  flex: 1;
  min-height: 0;
  padding: 20px;
  overflow-y: auto;
  overflow-x: hidden;
}
```

### 第三步：让左侧栏跟随框架高度

文件：

- `plm-web/src/layout/Sidebar.vue`

建议：

```css
.sidebar {
  height: 100%;
  overflow: hidden;
}

.sidebar__menus {
  flex: 1;
  overflow-y: auto;
}
```

## 5.3 修改后效果

修改后页面浏览会变成：

- 左侧栏固定在左边
- 右边滚动时不会把左栏一起带着跑
- 菜单很多时，左栏自己内部滚动
- 整个系统更像成熟后台而不是整页自然下拉

---

## 6. 优化方案三：左侧栏改成与背景融合的一体式导航样式

## 6.1 目标

在不改变菜单内容的前提下，把左侧栏从“卡片式导航”调整成“融合背景的一体式导航”。

重点不是改内容，而是改观感：

- 导航像页面框架的一部分
- 分组和子项更轻、更顺
- 选中态更明显
- 展开态更统一

## 6.2 建议视觉方向

建议把左侧栏样式从当前这种感觉：

- 每组像一个独立小白块
- 分组块边界较强
- 菜单显得有些碎

调整为这种感觉：

- 整个左栏是一个统一背景面
- 分组之间通过留白和文字层级区分
- 菜单项只有 hover 和 active 时出现轻量底色
- 当前选中项用细条、浅色底、字重变化来突出

简单说，就是从“卡片堆叠感”改成“系统导航感”。

## 6.3 建议改法

文件：

- `plm-web/src/layout/Sidebar.vue`

建议改动重点：

1. 弱化 `.sidebar__group` 的白底卡片感
2. 整体背景色与主框架底色更接近
3. 菜单项默认透明，悬停时轻微提亮
4. 当前项使用浅底色 + 左侧标识条 + 更清晰字色
5. 分组展开时不额外出现厚重边框
6. 子菜单缩进保留，但改成更轻的层级展示

## 6.4 建议代码写法

下面这段是后续正式开发时可参考的样式方向：

```css
.sidebar {
  background: #f3f5f7;
  border-right: 1px solid rgba(148, 163, 184, 0.14);
}

.sidebar__group {
  background: transparent;
  border: none;
  border-radius: 0;
  box-shadow: none;
}

.sidebar__group-trigger,
.sidebar__item {
  background: transparent;
  border-radius: 8px;
  color: #475569;
}

.sidebar__group-trigger:hover,
.sidebar__item:hover {
  background: rgba(148, 163, 184, 0.12);
  color: #1f2937;
}

.sidebar__item.is-active {
  background: rgba(37, 99, 235, 0.08);
  color: #1d4ed8;
  font-weight: 600;
  position: relative;
}

.sidebar__item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 999px;
  background: #2563eb;
}
```

如果当前组件里有分组外层白底容器，也建议拆掉或弱化：

```css
.sidebar__group-panel {
  background: transparent;
  padding: 4px 0 8px;
}
```

## 6.5 修改后效果

修改后左侧栏会有下面这些变化：

- 看起来和整体背景是一体的
- 不会再像多个卡片拼在一起
- 当前选中的菜单会更容易识别
- 分组展开后仍然显得轻，不会很重很乱

这会更适合你现在这个 PLM 系统，因为页面模块已经很多，导航如果再过重，会进一步加剧视觉负担。

---

## 7. 三个优化点之间的关系

这 3 个点不是分散的小修补，而是同一个方向上的统一优化：

1. 商务区展开态强化，解决“内容层级不明显”
2. 左侧栏固定布局，解决“滚动边界不清楚”
3. 左侧栏一体式样式，解决“导航视觉太重、不够融入框架”

统一后的页面会更清楚地表达出：

- 哪里是导航
- 哪里是主内容
- 哪里是当前展开的详细信息

---

## 8. 后续正式开发时建议修改的文件

如果你确认进入代码实施阶段，建议只改这几处：

### 8.1 商务区展开态

- `plm-web/src/views/product/ProductDetail.vue`

### 8.2 左侧固定与右侧滚动

- `plm-web/src/layout/index.vue`
- `plm-web/src/layout/ContentArea.vue`
- `plm-web/src/layout/Sidebar.vue`

### 8.3 左侧栏融合式样式

- `plm-web/src/layout/Sidebar.vue`
- 如需联动整体壳层背景，可补充 `plm-web/src/layout/index.vue`

---

## 9. 实施顺序建议

后续真要改代码，我建议按这个顺序来：

1. 先改布局滚动关系
2. 再改左侧栏一体式样式
3. 最后改商务区展开态强化

原因很简单：

- 先把整体壳层滚动关系理顺
- 再统一左侧导航风格
- 最后补页面内部的展开态细节

这样做更稳，也更容易观察每一步带来的效果变化。

---

## 10. 本次结论

这次文档确认后的最终理解是：

### 商务区

不需要重做逻辑，重点是强化展开态的视觉差异，让用户明显知道“这里已经展开了详细内容”。

### 页面滚动

不需要大改结构，重点是把滚动权收敛到右侧内容区，让左侧导航固定下来。

### 左侧栏样式

不需要改菜单内容，重点是把样式改成与背景融合的一体式导航，弱化卡片块感，强化选中项与层级感。

本次只完成方案沉淀，不修改实际代码。后续如果你确认开发，就可以直接按这份文档进入前端实施。

# StockFlow 界面设计

2026-10-23 完成绿色工作台的视觉优化；2026-10-24 增加注册登录、订单履约与鼠标悬停动画。以下 Figma / Canva 文件保留为前一轮视觉参考，当前完整页面以本地实现和最新截图为准。

## 设计文件

- [Figma 工作概览](https://www.figma.com/design/OVp7EQbg2Dt7RwRphNCWJz?node-id=3-203)
- [Figma 登录页](https://www.figma.com/design/OVp7EQbg2Dt7RwRphNCWJz?node-id=3-342)
- [Canva 两页草案](https://canva.link/7sbb7attc1fboz8)

Figma 包含两个 1440 × 960 的可编辑画板，使用原生文字、自动布局和组件实例，没有把整页截图作为设计图层。复用了 Simple Design System 的按钮和输入框，并设置 StockFlow 品牌颜色变量与五级文字样式。

Canva 已生成两页，工具读取确认其中包含商品指标、库存表和登录文案；其缩略图服务在本次核对时返回空白。Canva 草案中的版权年份和完整履约宣传文案未采用。前一轮本地实现以当时的 P0 业务能力和经截图检查的 Figma 方向为准；本轮已接入真实 P1 状态操作。

## 实现规则

- 延续绿色品牌色，使用深绿导航区区分操作入口与内容。
- 表格主要内容 14px，辅助内容至少 12px；加大操作按钮并保留键盘焦点提示。
- 保留原生表格、表单和弹窗，继续使用现有 ES Modules，不新增前端依赖。
- Figma 使用可用的 Noto Sans SC；本地使用 Segoe UI / Microsoft YaHei 等系统字体回退。两者字体渲染存在差异，不声明像素级一致。
- 指标仍来自本次加载的真实演示数据。订单确认、取消、发货与收货已接通后端，界面按身份和订单状态提供操作。
- 内嵌 SF 标签页图标避免浏览器请求受保护的默认 favicon 路径。

## 本地查看

运行项目后打开 [StockFlow](http://127.0.0.1:8082)。账号口令保存在本机 .local/demo-login.txt，不进入设计文件或 Git。

## 鼠标悬停动画

精细鼠标停留时：按钮抬升 2px，指标卡抬升 4px 并增加阴影，导航平移 3px，登录步骤平移 5px，输入框边框平滑变化。表格行仅改变背景，保持列对齐。减少动态效果或触控设备不启用这些位移动画。

真实 Edge 已测得指标卡 0.24s 过渡和 4px 位移，并确认减少动态效果时 transform 为 none；记录见 artifacts/p1-20261024/browser-validation.json。


## 2026-10-26：暗夜粒子流

本轮按用户要求调用 Figma 与 Canva，并完成实际前端改版。

- [Figma 粒子登录](https://www.figma.com/design/OVp7EQbg2Dt7RwRphNCWJz?node-id=12-52) 与 [粒子概览](https://www.figma.com/design/OVp7EQbg2Dt7RwRphNCWJz?node-id=12-96)。沿用已有 Noto Sans SC、颜色变量、按钮及输入组件；两张稿有原生文字、组件实例与可编辑粒子图层，不是整页截图。
- [Canva 粒子参考](https://www.canva.com/M/MAHZHBDb1p4?utm_source=OC-AaBlKrqBJJNF&utm_campaign=agent_connector_create_image_asset_opened)。媒体 MAHZHBDb1p4，1680×944；采用它的墨黑、薄荷绿、冰蓝和波形粒子方向，未把200px缩略图拉伸进页面。
- 实际粒子由本地 Canvas 绘制，适配区域尺寸，细鼠标进入时轻微避让。登录展示区与概览横幅使用粒子，业务表格保持安静。
- 支持暂停/恢复并保存当前浏览器的偏好；遵循系统减少动态效果。页面后台、粒子离屏时停止动画，手机减少绘制密度，手机登录隐藏装饰区。
- 图标沿用本地 Lucide，新增原版 pause/play；没有引入动效库、CDN 或运行时图片请求。
- 主要代码在 assets/ui/atmosphere.css、ambient.mjs，既有业务状态和 API 保留。

验收见 [粒子界面记录](particle-ui-qa.md)，本轮实际截图见 [工作概览](screenshots/particle-overview.png) 与 [登录](screenshots/particle-login.png)。


### 同日后续：全界面粒子扩展

按用户反馈，上一版局部横幅已扩展为全界面效果。登录/注册使用全屏粒子；工作台共享固定背景，每个页面的内容面板与弹窗有低亮度粒子。

点阵不再压低左侧亮度；全域漂浮、三条流动带、呼吸闪烁、鼠标短连线共同组成动效。商品/库存偏薄荷绿，流水/买家订单偏冰蓝，预警偏暖金。手机登录也保留轻量背景。动态控制、减少动态效果和离屏停止绘制沿用同一调度器。

此轮是对上一轮视觉方向的代码扩展；没有新增Figma或Canva设计文件。实际截图和验收见 docs/all-pages-particles-qa.md。


### 2026-10-26 业务界面收尾

在现有深色粒子风格中加入角色待办、七日真实趋势、盘点/对账/审计页面，统一表格可读性、固定表头、编号复制与失败数据保留。粒子支持标准/低密度/关闭。此轮直接修改应用代码，没有新增设计服务文件；[验收](operations-hardening-qa.md)。

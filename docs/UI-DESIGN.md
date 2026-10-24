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

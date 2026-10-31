# StockFlow 粒子视觉验收

日期：2026-10-26。范围：现有深色界面视觉改版，独立 D:/project/StockFlow。没有改动Java服务、数据库、订单或库存数据。

## 结果与设计来源

final result: passed for the bounded visual upgrade, with intentional product differences.

Figma：https://www.figma.com/design/OVp7EQbg2Dt7RwRphNCWJz?node-id=12-52；https://www.figma.com/design/OVp7EQbg2Dt7RwRphNCWJz?node-id=12-96。登录1440×960，概览1440×1150参考整页；有Noto Sans SC文字、现有组件实例及可编辑粒子，未引入整页位图，字体族检查无错误。
Canva：媒体MAHZHBDb1p4，提供粒子波形与色彩参考。实际动效为Canvas本地实现，未放大缩略图。Lucide官方pause/play原SVG及原有许可保留。

## 视觉对照与修正

本机 .stockflow-work/particle-design-comparison.png 将Figma登录参考与实际页面放入同一图像；同为1440×960状态后统一到1080×720比较。

- 字体：Figma Noto Sans SC；页面使用已有 StockFlow Sans 子集，缺字由系统字体回退。标题、正文和表单层级明确；不声明像素级字体一致。
- 间距/布局：保留左侧展示、右侧浮层表单及底部三步说明；实际表单保留注册入口、折叠帮助、当前账号等完整功能，所以比参考稿略高。该差异有产品依据。
- 颜色：墨蓝底、薄荷绿主色、少量冰蓝/暖色粒子；修复Figma旧组件的白色输入框和低对比标签。实际数据区不铺设动画。
- 图像/效果：参考点阵静态；页面为随尺寸重绘的实时Canvas，使用更柔和光感、低强度渐变和鼠标避让。没有整页图片背景、外部CDN或额外渲染依赖。
- 文案/图标：业务指标均来自原有服务端；没有装饰性虚构趋势或百分比。保留真实注册、账号、帮助内容。Lucide图标同源。
- 可读性：将数据正文从初稿11px回调12px，保留原表格主要商品名13px与数据间距；导航、卡片、表单/弹窗有一致的边界和焦点提示。手机为两列指标与纵向内容。
- 初稿曾在1150px以下隐藏快捷操作，已恢复显示。手机检查未发现整体横向溢出。

上述白色输入框、正文偏小和小屏快捷操作隐藏已修正；完成后的登录对照及实际桌面/手机检查没有发现需要阻止本轮交付的布局问题。不是正式无障碍或多浏览器认证。

## 自动化与运行检查

- 前端61项全部通过，含原有56项业务回归与5项动效调度测试。证据：本机 .stockflow-work/particle-test-final.log。
- 测试覆盖：粒子可见/离屏、页面前后台、系统减少动态效果、暂停保存与恢复、销毁清理、Canvas不支持时降级；不把测试当作帧率基准。
- 本轮仅UI修改，未重复运行上一轮已经通过的63项Java/MySQL测试。
- Maven package（skipTests）BUILD SUCCESS，最终20:40:54；.local/particle-package-final.log。git diff --check通过。最终JAR静态文件与源码逐项SHA256核对。
- 匿名可加载ambient.mjs、atmosphere.css、pause.svg、play.svg，业务dashboard保持401。进程重启后的首个探测早于就绪曾被拒绝，待就绪后重新确认，不作为功能失败或成功凭证。

## 实际浏览器验收

通过Computer Use控制IAB，操作实际JAR与MySQL：

- 1440×960登录：粒子显示；暂停后按钮和状态变化，刷新保留暂停偏好，随后恢复动态。
- 仓管登录：概览读取真实12种商品、实存225、预留0、4种预警；新横幅可进入预警页。
- 商品详情读取实存19、预留0、安全库存20，流水可打开SF-10的完整履约时间线；无新增业务写操作。
- 手机390×844：登录实际pageWidth=scrollWidth=390，卡片350；注册及概览实际pageWidth=scrollWidth=375（显示纵向滚动条）。概览粒子、两列指标、菜单及按钮保持可用。
- 浏览器错误/警告日志检查为空。右上角控制提供动效暂停，系统减少动态效果由媒体查询和调度测试覆盖；未改变用户系统设置。

实际截图：screenshots/particle-login.png、particle-overview.png、particle-alerts.png、particle-product.png、particle-mobile-login.png、particle-mobile-overview.png。

边界：单浏览器引擎、有限视口与业务入口验收；未做生产性能基准或广泛浏览器兼容认证。未提交或推送GitHub。

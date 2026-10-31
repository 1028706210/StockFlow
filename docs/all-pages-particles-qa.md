# 全界面粒子扩展验收

日期：2026-10-26。用户反馈旧粒子只在局部右下方，希望丰富到各界面。保留此前未提交成果，目录D:/project/StockFlow。

## 实现

- 登录与注册改为全屏画布；桌面左右均可见，手机也显示轻量粒子。
- 工作台共享一个固定背景；商品、库存、流水、预警、履约、本人订单、创建订单及概览的面板各有轻量粒子。所有现有dialog也加入粒子层。
- 普通面板粒子位于内容下方，dialog保留原定位；所有canvas为aria-hidden、pointer-events:none。不会成为表单字段，不拦截操作。
- 全域漂浮光点、三条流动带、亮度闪烁、鼠标避让与最多7条近距离连线；移除了旧版随x坐标衰减的亮度，四象限均有分布。
- page由既有showPage鉴权后写到workspace的dataset，动效按页面读取色调；不影响路由权限、业务数据或API。
- 仍共用一个requestAnimationFrame调度器，实际绘制间隔至少42ms。只绘制可见画布，后台停止，暂停偏好跨页和刷新保留，系统减少动态效果时静态显示。
- 手机全屏背景175个基础粒子、面板85个，登录230个；像素比上限1.5。没有新增依赖。

## 自动化

66项前端测试全部通过，0失败/跳过。新测试覆盖四象限/左上和右下、切页色调与单调度循环、动态及暂停全区域静态、鼠标短连线数量和离开清理、手机数量与高度覆盖。原登录、注册、退出、订单、筛选、幂等等回归保留。
证据：本机工作区 .stockflow-work/particle-all-pages-test.log。

仅改UI，没有重复运行上一轮已通过的63项Java/MySQL测试。Maven package(skipTests) BUILD SUCCESS，20:53:57完成，.local/particle-all-pages-package.log。最终静态源码/JAR SHA256核对；git diff --check通过。

## 真实浏览器

通过Computer Use/IAB操作实际JAR和数据库，无业务写入：

- 仓管逐页检查商品、库存、流水、预警、履约与概览；每页存在非零尺寸的workspace与当前面板画布。概览额外保留横幅画布。
- 买家检查“我的订单”“创建订单”；订单页有背景与面板，创建页有背景与两块面板粒子。
- 编辑商品弹窗仍居中，实际position:fixed，480×约520，canvas为479×518；关闭按钮工作，未保存任何商品变更。
- 手机390×844，订单和创建订单pageWidth==scrollWidth==375（扣除竖向滚动条），主背景375×844，内容画布341px宽。菜单、退出中心点命中真实控件，未被canvas拦截。
- 手机暂停后通过菜单切到创建订单，motion继续为paused；恢复后统一运行。登录手机页面同样检查完整宽度与粒子显示。
- 浏览器error/warn日志为空。维持原鼠标、键盘和表单操作方式。

这些是单浏览器引擎、指定视口的验证，不是帧率基准或正式无障碍认证。

## 截图

docs/screenshots/particles-everywhere-products.png
docs/screenshots/particles-everywhere-ledger.png
docs/screenshots/particles-everywhere-alerts.png
docs/screenshots/particles-everywhere-orders.png
docs/screenshots/particles-everywhere-overview.png
docs/screenshots/particles-everywhere-dialog.png
docs/screenshots/particles-everywhere-login.png
docs/screenshots/particles-everywhere-mobile-order.png
docs/screenshots/particles-everywhere-mobile-login.png

上一轮Figma/Canva仍作为原视觉来源；本轮扩展直接实现于现有Canvas、CSS、HTML与显示状态，不声称设计文件已同步本轮所有页面。

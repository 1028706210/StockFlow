# 聊天界面交付验证

2026-10-27 后续繁简输入修复已完成：新增统一业务用语规范化、保留原文商品搜索与一次规范写法回退；108项Node、74项StockFlow前端、10项繁简输入真实浏览器检查通过。两个服务已更新。此处下文保留聊天界面初次交付证据；详细修复记录见 [繁简输入验收](D:/project/Node.js/docs/traditional-input-qa.md)。

用户要求在现有 StockFlow 中加入聊天界面。已增加 StockFlow 工作台右下角咨询抽屉，并使 Node 8787 首页提供独立聊天页，使用同一个聊天组件、纯回复核心、规则和数据校验。

## 实际修改

- Node：新增 web/，原回复逻辑和数据校验提取为两端共享模块；HTTP 明确清单提供页面、CSS、JS 和规则，不暴露配置或任意路径。保留原有 API、Host/Origin 检查，静态页面增加 CSP。
- StockFlow：index.html 增加 CSS/模块入口；app.js 仅增加会话发布和聊天会话失效处理；新增 assets/ui/chat 资源。现有 app-flow 测试补充会话桥行为验证。
- StockFlow 中通过现有 requestJson 的 Cookie 和 epoch 查询，不转发密码、不跨域、不改变权限或 Java 业务。仅同源 GET 商品和本人订单接口。
- 独立页身份只在内存中；验证后清空密码输入。切换、断开、旧 401、关闭验证窗口均有版本隔离。消息仅在内存保留最近 40 条。

## 自动化与审查

- Node `npm test`：**90 项通过，0 失败、0 跳过**。含原 API/CLI 回归、浏览器资源、会话适配、重复发送和异步旧回复隔离。
- StockFlow `node --test src/test/frontend/*.test.mjs`：**74 项通过，0 失败、0 跳过**；修改前基线 73 项通过。
- 首页测试先实际观察到原服务返回 404，加入静态页面后通过。
- 独立只读代码审查未发现阻止交付的重要问题。审查另用实际组件与入口的内存 DOM 探针验证 IME、Shift+Enter、账号 A→B 后迟到的 401、旧成功回复、关闭验证窗口和当前会话失效清除。

## 运行与打包

当前执行环境中旧 MAVEN_HOME 已失效，实际采用本机 IntelliJ 附带 Maven：D:/za/IntelliJ IDEA 2024.1/plugins/maven/lib/maven3/bin/mvn.cmd。

已用 `-o -B -DskipTests package` 离线打包 StockFlow，BUILD SUCCESS。**Java/MySQL 测试本轮没有运行**，因为本次修改前端资源和接入，避免测试清空测试库；不以本轮前端检查代替 Java 正确性验收。

已启动既有独立 MySQL 3308、打包后的 StockFlow 8082 和 Node 8787；使用既有开发库，没有初始化新库或修改结构。8082 健康与聊天资源、8787 首页均实际返回 200。

## 真实浏览器验收

使用本机已有 Microsoft Edge **156.0.4314.42**，通过 bundled Playwright 运行，10 项检查通过，**pageErrors=[]**。原 CUA/node_repl 工具会话初始化报路径不存在，未将该工具失败当作验收；后续以实际启动的 Edge 完成下列验证。

1. 8787 首页真实发送规则问题，得到正确取消条件。
2. 中文输入法确认 Enter 不发送；Shift+Enter 换行。
3. 独立页验证买家身份，查询真实商品与本人订单；验证后密码输入为空。
4. 独立页断开/切换账号清空消息，buyer2 查询 buyer 的已有订单被拒绝。
5. 独立页 390px 手机宽度无横向溢出。
6. StockFlow 抽屉沿用 buyer 的 Cookie，无额外密码输入；真实商品与订单状态查询正常。
7. Escape 关闭抽屉，焦点回到启动按钮。
8. StockFlow 退出后入口隐藏、对话清空；buyer2 重登后看不到旧消息，不能查询 buyer 订单。
9. warehouse 查询买家订单得到权限错误并保持登录，仍可咨询业务规则。
10. StockFlow 抽屉完整处于 390×844 视口内。

验收仅登录、退出、咨询与 GET 查询；未创建或修改商品、订单、库存或账号。凭据只在验收进程内读取，不进入截图、源码或结果文件。现有演示数据的状态可见于本机截图，未对外发布。

截图已实际逐张查看：../Node.js/.artifacts/chat-browser/ 下 standalone-desktop.png、standalone-mobile.png、stockflow-desktop.png、stockflow-mobile.png。完整浏览器结果在该目录 result.json；测试与构建日志在 ../Node.js/.artifacts/，均不进入分发包。

## 使用

打开 http://127.0.0.1:8082，登录后点击右下角“咨询助手”；或打开 http://127.0.0.1:8787 使用独立页面。若已有旧页面，使用 Ctrl+F5 刷新资源。

本次为单个真实浏览器的桌面/手机尺寸、键盘与权限流程验证，不声称所有浏览器兼容或正式可访问性认证。规则式咨询仍有表达范围，不接入大模型、真实物流、微信/QQ 或订单写操作。

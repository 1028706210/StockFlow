# StockFlow 账号、履约与悬停完善设计
日期：2026-10-23。用户已确认同时完善登录注册、订单流程与悬停动画，继续使用 D:/project/StockFlow。

## 方案与边界
沿用 Spring Boot + MyBatis + MySQL 单体和原生前端。服务端会话采用 Spring Security 标准表单登录、HttpOnly/SameSite Cookie 与 CSRF；不自行签发 JWT。不引入 Redis、消息队列、真实支付或物流服务。
保留现有 HTTP Basic 供显式携带 Authorization 的本地 API 示例与原有测试使用；该类请求不依赖环境 Cookie，可豁免 CSRF。浏览器使用会话 Cookie，所有写入操作（包括登录、注册、退出）必须携带 CSRF Token。
账号持久化到 app_user，密码仅保存 BCrypt 哈希。公开注册固定为 BUYER。演示用户 ID 1/2/3/4 保持不变，已有订单与流水归属不变；新用户从 ID 100 起分配。初始化只插入缺失演示账号，不覆盖已有密码。
订单沿用已确认的原始设计：PENDING_CONFIRM -> CONFIRMED -> SHIPPED -> COMPLETED；PENDING_CONFIRM 可人工取消或到期取消为 CANCELLED，取消原因为 USER/EXPIRED。确认是模拟业务确认，不接入支付。
取消释放 reserved；发货同时扣减 on_hand/reserved；订单、库存和流水在同一 MySQL 事务内更新。先锁订单，按 SKU 升序操作库存；重复命令不重复改库存。
增加 cancel_reason 字段，保留已有数据。提供独立 SQL 升级脚本；只升级 StockFlow 的 dev/test 库，不修改 zzyl10。默认每30秒扫描100个到期订单，测试关闭定时扫描并直接验证任务方法。
鼠标悬停采用 CSS 按钮轻抬升、卡片阴影、导航平移、输入边框和登录步骤反馈，仅精细指针且未请求减少动态效果时启用。

## API 合同
- GET /api/auth/csrf -> {token, headerName}
- POST /api/auth/register (JSON username/password) -> 201 {id,username,roles}; 不自动登录；拒绝额外 role 字段。
- POST /api/auth/login (application/x-www-form-urlencoded username/password + CSRF) -> 200 {id,username,roles}
- POST /api/auth/logout (+CSRF) -> 204；会话作废。
- GET /api/auth/me -> {id,username,roles}; 匿名401。
- POST /api/orders/{id}/confirm, /cancel, /complete：本人买家。
- GET /api/fulfillment/orders；GET /api/fulfillment/orders/{id}；POST /api/fulfillment/orders/{id}/ship：仓管/管理员。
- 订单响应延续原字段，追加 buyerId 与 cancelReason；列表也包含 cancelReason。原有金额、ID与请求幂等合同保留。
- 买家访问他人订单404。越角色403。非法状态或已到期确认409。重复确认在 CONFIRMED/SHIPPED/COMPLETED 返回现有订单；重复发货在 SHIPPED/COMPLETED 返回现有订单；重复完成/取消返回终态。
- 截止时间使用数据库时间；已到期订单不能人工确认，取消/定时任务仍可释放。
- 前端保留最近100条的数据范围提示，增加仓管履约列表、状态筛选和订单状态操作。退出等服务器成功后清空页面状态，刷新可恢复有效会话。

## 验收
新增真实 HTTP/MySQL 测试覆盖注册持久化与越权、会话登录退出、CSRF、重复命令、归属、确认/取消竞争、到期释放、重复发货、扣减失败回滚。保留现有并发库存和幂等测试。
前端测试验证 Cookie/CSRF 请求、旧账号响应隔离、状态操作和表单防重复。真实浏览器走注册→登录→下单→确认→仓管发货→买家收货，并验证取消释放和悬停/减少动画设置。

# 运行状态与故障排查

管理员登录后台后进入“运行状态”，或访问 `/#runtime`，点击顶部“刷新数据”进行一次只读检查。仓管、买家没有该入口，也不能访问管理员诊断接口。

## 页面含义

- 服务与数据库：服务端能够返回本次诊断，且数据库就绪查询通过时显示“数据库就绪”；失败或无法鉴权时显示无法核验，不能据此推定正常。
- API请求与累计平均耗时：本次组件启动后的累计数，包含诊断、认证失败等API请求；平均值是累计时长除以计数，不是P95/P99。并发读取为近似快照，不承诺每个桶和总数在同一瞬间一致。
- 历史累计5xx：用于排查历史错误，不表示故障当前仍持续。进程重启后计数重新累计。
- 库存核对：复用只读对账，按当前全部商品核对库存余额、流水与订单预留，显示checkedCount和mismatchCount；不自动修复。此结果与运行指标来自独立请求，不是同一事务快照。
- 订单超时扫描：显示配置开关、间隔、本批状态与起止时间、最近成功和历史最近失败时间。空批次也可以成功；成功处理候选只是未抛异常，不等于实际取消了订单。

最近批次状态：NEVER等待首次扫描、RUNNING扫描中、SUCCEEDED正常完成、PARTIAL_FAILED存在候选处理失败、FAILED候选查询失败。自动任务关闭时优先显示“自动扫描未启用”，不显示成正常运行。下一批成功可恢复最近状态，历史失败时间仍保留。

## 接口

GET `/api/admin/runtime` 仅ROLE_ADMIN，响应字段如下：

| 字段 | 含义 |
|---|---|
| observedAt | 服务端本次观测时间，UTC ISO时间 |
| databaseStatus | UP或DOWN，仅反映本次就绪检查 |
| expiryEnabled / expiryDelayMillis | 超时任务配置开关与延迟毫秒数 |
| metrics.process | startedAt、uptimeSeconds；时长采用单调时钟 |
| metrics.requests | 既有count、durationNanos与固定1xx–5xx桶 |
| metrics.expiry | 既有batches/succeeded/failed/batchFailures，及lastBatch、lastSuccessfulAt、lastFailureAt |
| metrics.expiry.lastBatch | status、startedAt、completedAt、processed、failed、queryFailed |

未发生的时间返回null。既有 `/api/runtime/metrics` 保持仓管/管理员权限，并增加上述指标字段，不移除旧字段。响应不含数据库地址、密码、主机路径、异常正文或订单列表。

页面每次刷新都清除上次检查结果，再分别显示本次运行和对账结果。一项失败不会伪造另一项的成功；错误区域显示无法核验，并提供重新检查。退出或换身份会清空界面，在途旧响应不能写回。

## 排查顺序

1. 记录发生时间、页面错误与requestId。不要复制私有配置、Cookie、密码或完整连接串到截图或公开Issue。
2. 服务不可达时检查本机展示Java进程和 `.local/showcase-java.log`；存活探针是 `/api/health`，数据库就绪探针是 `/api/health/readiness`。对数据库故障的外部探测应使用无Cookie请求。
3. 当前账号认证版本核验依赖数据库。数据库断开可能先返回503 ACCOUNT_CHECK_UNAVAILABLE；管理员页面也会无法鉴权，此时不能以旧状态判断系统健康。
4. 扫描失败时检查任务日志、数据库连接及相关订单。已有游标会继续访问后续候选并在以后批次重试失败单，不直接改库存或订单状态。
5. 对账差异进入“库存对账”，核对对应流水、订单预留与审计。查明原因后按业务操作处理；本页不会自动修正。

本阶段未接入短信、邮件、Webhook或外部监控平台，没有自动发送告警。实际部署需要由经营/运维负责人确定渠道、凭据安全配置、阈值、通知接收人、去重恢复策略，再做测试通知与故障演练。不能把本机页面描述为已具备全天候监控。

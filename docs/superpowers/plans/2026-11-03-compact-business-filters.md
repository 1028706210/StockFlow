# 业务查询区减负实施计划

> 执行方式：superpowers:executing-plans，当前独立源码副本分支codex/compact-business-filters，基准b8d72f3。按任务记录验证，完成后独立只读审查。

**目标：**减少4个业务页常驻范围字段，保持查询可见性和原流程。

**结构：**复用app.js中的readQuery/persistQuery/restoreQuery及原加载函数；原生details/summary负责展开，不新增前端模块或依赖。

**技术栈：**Java17、现有Spring Boot、原生ES模块、Node测试、MySQL8、Edge。

**设计：**../specs/2026-11-03-compact-business-filters.md。

## 约束与审查重点

全部默认文案简体中文；不改接口、库表、权限、订单与库存写入。关注隐藏条件仍可辨认、金额0、不在查询刷新时抢展开状态、清空保持其他条件、退出/异步身份隔离、窄屏原生日期输入不溢出。

## 任务1：查询行为与布局

文件：src/main/resources/static/index.html、app.js、assets/ui/atmosphere.css；测试src/test/frontend/app-flow.test.mjs。

- [x] 先增加旧hash自动展开与清空、手动折叠和金额0、角色/退出隔离的行为测试，运行确认失败。
- [x] 在app.js增加syncRangeDisclosure(kind,query={},restore=false)，只处理orders/fulfillment/ledger/audit；在恢复、保存及退出接线，清空调用现有加载函数。
- [x] HTML保留全部原ID和字段，仅包装原生折叠区、添加摘要与清空按钮；将原导出控件移入常用工具条。
- [x] 在现有主题文件增加折叠区、工具条和手机布局，使用现有变量、焦点和reduced-motion规范。
- [x] 相关行为测试与全套Node通过，语法/差异检查通过。

## 任务2：验收和交付

- [x] Java verify及打包成功；核对本机进程路径/监听，备份旧JAR后同步明确文件并启动。
- [x] Edge验证默认/展开/范围查询/清空/刷新后恢复、空结果；买家端及320像素无溢出，保留真实截图。详情入口保持原实现，本轮未在浏览器重跑详情操作。
- [x] 独立只读审查；补日期摘要和原生invalid回归后修复，聚焦复核通过，更新验收记录与设计说明。
- [ ] 核对双仓库同一提交、新提交CI和源码包，保留最终交付记录。

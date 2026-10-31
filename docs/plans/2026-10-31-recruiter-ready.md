# StockFlow 招聘展示优化设计与实施记录

日期：2026-10-31。基准：`f3f968f`。目标：招聘者能快速读懂 Java 主线、启动项目，并从代码和可复核记录判断工程能力。

## 范围与选择

沿用当前 Java 17、Spring Boot、MySQL 和原生前端，不迁移框架、不新增业务模块、不改开发库。既有授权允许常规代码、排版和本地验证自行推进；本轮不重新设置讨论或批准环节。使用独立发布副本 `codex/recruiter-ready`，保留原目录工作分支。

考虑过继续堆叠功能、全面更换界面、整理现有交付三种方式。本轮选择整理已有交付并修正启动行为：当前主要缺口是说明过长、验收状态过时和首次运行反馈不足。新增模块不能解决这些问题。

## 文件与目的

- README.md：前置业务价值、技术栈、代码与测试证据、Docker 快速启动；附属商城降为目录说明。
- docs/recruiter-guide.md：五分钟实操、库存期望值、技术追问与源代码入口、启动排障。
- docs/recruiter-readiness.md：当前检查和历史证据分开记录；不将测试数混成后端数量。
- docs/handbook/README.md、08-testing-acceptance.md：纠正默认分支与过时的浏览器阻塞说法；PDF 保留历史基准说明。
- tools/Start-Container.ps1：复用已有入口，检查 Docker 与 Compose，沿用既有口令和端口，等待应用就绪，超时明确报错。
- .dockerignore：避免把数字商城私有交付物和其他环境文件发送到 Java 镜像构建上下文。
- .github/workflows/verify.yml：保留 Java/MySQL 与容器实测，补充独立数字商城 Node 测试；容器 CI 使用实际启动脚本。

## 验证与完成条件

- [x] 检查 README、导读与本轮文档中的本地链接及文件路径：72处文件路径存在。
- [x] 重跑现有 124 项 Node 测试：全部通过；与 Java/MySQL 数量分开。
- [x] PowerShell 语法与启动行为检查：7组受控检查与真实缺Docker失败路径通过；真实容器由CI补验。
- [x] 只读独立审查：无Critical/Important，修正两处环境说明。
- [ ] GitHub 默认分支发布，查看本次 CI；再将准确同一提交同步 Gitee。
- [ ] 同步本地修改并记录通过范围，未执行的浏览器或容器检查如实说明。

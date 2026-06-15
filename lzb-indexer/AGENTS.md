# lzb-indexer 项目约定

## 环境与安装

- 所有软件安装到 D 盘，禁止安装到 C 盘，除非技术上必须（如 Docker Desktop 的部分组件）。
- 默认安装路径优先使用 `D:\<软件名>`，例如 PostgreSQL 装到 `D:\PostgreSQL`。
- 数据库数据目录同样放 D 盘（如 `D:\pgdata`）。

## 技术栈

- Java 8 + Maven 3.8 + Spring Boot 2.7.18
- Web3j 4.9.8
- PostgreSQL 16（生产），H2（仅测试）
- Foundry / Anvil（测试用本地链）

## 编码与注释

- 源码编码 UTF-8 无 BOM
- 注释使用中文，面向 Java 后端开发者视角

## 项目结构

```
src/main/java/com/lzb/indexer/
  config/       —— Spring 配置
  controller/   —— REST API
  domain/       —— JPA 实体 + Repository
  dto/          —— 数据传输对象
  scanner/      —— 区块链扫描核心（BlockScanner、EventDecoder、ScannerScheduler）
  service/      —— 业务逻辑
```
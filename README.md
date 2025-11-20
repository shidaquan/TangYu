# TangYu Go CRUD Example

一个简单的 Go HTTP 服务，提供增删改查接口，并演示调用外部 API 获取一句提示信息。

## 运行

```bash
# 可根据需要调整端口，默认 8080
PORT=8080 go run .
```

如果所在环境无法访问公共 Go 模块代理，可在运行或测试时指定：

```bash
GOPROXY=off GOSUMDB=off go test ./...
```

## HTTP 接口
- `POST /items`：创建记录，JSON 示例：`{"name":"demo","description":"something"}`
- `GET /items`：列出所有记录。
- `GET /items/{id}`：查询单条记录。
- `PUT /items/{id}`：更新记录。
- `DELETE /items/{id}`：删除记录。
- `GET /api/quote`：调用外部 API（默认 GitHub Zen），返回一句短语。
- `GET /health`：健康检查。

## 设计说明

- 使用内存存储并通过读写锁保证并发安全。
- `apiQuote` 使用可注入的 HTTP 客户端与 URL，便于测试时替换外部依赖。
- 提供单元测试覆盖核心 CRUD 逻辑、HTTP 处理流程以及外部 API 调用失败场景。

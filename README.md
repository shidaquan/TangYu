# TangYu

轻量级 Go 示例项目，作为简单的任务管理 HTTP 代理，对接外部任务接口并提供基础的增删改查。

## 运行

```bash
go run ./...
```

服务器默认监听在 `:8080`，可以通过 `PORT` 环境变量覆盖。代理的上游接口默认使用 https://jsonplaceholder.typicode.com ，可通过 `TASK_API_BASE` 环境变量修改。

## 接口一览

- `GET /health`：健康检查，返回 `{ "status": "ok" }`
- `GET /tasks`：从外部接口获取所有任务
- `POST /tasks`：创建任务，请求体 `{ "title": "任务标题" }`
- `PUT /tasks/{id}`：更新任务，请求体 `{ "title": "标题", "completed": true }`
- `DELETE /tasks/{id}`：删除任务

## 运行测试

```bash
go test ./...
```

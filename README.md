# TicketBox

自营式演出票务平台：Spring Boot 单体 + MySQL + Redis + MinIO，React 用户端与 Vue 管理端。

## 本地开发

```bash
# 基础设施（MySQL 3307、Redis 6380、MinIO 9001/9002）
docker compose -f ops/docker-compose.yml up -d mysql redis minio

# 后端（:8888；dev profile 会创建管理员 admin / ticketadmin）
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# 用户端（:5173）
cd frontend/web && pnpm dev

# 管理端（:5174）
cd frontend/admin && pnpm dev
```

设计与术语见 `docs/design.md`、`GLOSSARY.md` 与 `docs/adr/`。

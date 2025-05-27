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

## 生产构建

```bash
# 构建两端并复制进 Spring 静态资源
./ops/build-frontends.sh

# 打包并以 dev profile 运行（注入开发默认密钥与演示管理员）
mvn -DskipTests package
SPRING_PROFILES_ACTIVE=dev java -jar target/ticketbox-*.jar
```

用户端：`http://localhost:8888/`；管理端：`http://localhost:8888/admin/`。

非 dev 环境需注入 `JWT_SECRET`、`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`PAYMENT_CALLBACK_SECRET`、`TICKET_QR_SECRET` 等环境变量。

## 一键启动（Docker）

```bash
docker compose -f ops/docker-compose.yml up --build
```

`app` 服务默认使用 `dev` profile 以便生成演示管理员与数据；正式部署时设置 `SPRING_PROFILES_ACTIVE` 为空并注入真实密钥。

设计与术语见 `docs/design.md`、`GLOSSARY.md` 与 `docs/adr/`。

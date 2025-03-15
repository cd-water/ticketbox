# TicketBox 设计文档

自营式票务平台，单体 Spring Boot 应用。本文件是经评审确认的设计基线；术语以 `GLOSSARY.md` 为准。

## 1. 目标与范围

- 个人作品集项目，但按真实场景设计：必须扛住热点场次并发购票且不超卖。
- 单一运营方（平台自身）发布演出并售票，不做多主办方入驻。
- 单机部署：一个 Spring Boot 进程 + MySQL + Redis + MinIO。
- 前后端分离开发：React 用户端、Vue 管理端，生产由 Spring 托管静态资源。

### 本期不做

退款/换票、优惠券/会员、实名核验（只记录不校验）、搜索/推荐、多语言、真实分账、闸机/硬件核销、CI、k6/Playwright 等外围测试。

## 2. 架构总览

```
┌──────────────┐   ┌──────────────┐
│ web (React)  │   │ admin (Vue)  │   开发: Vite 5173 / 5174
└──────┬───────┘   └──────┬───────┘   生产: 打进 Spring static/
       │  /api             │  /api
       └────────┬──────────┘
        ┌───────▼─────────────────────────────┐
        │  ticketbox-app (Spring Boot, :8080) │
        │  auth │ event │ performance │ seat  │
        │  order │ payment │ ticket │ waitingroom
        └───┬───────────┬───────────┬─────────┘
            │           │           │
        ┌───▼───┐  ┌────▼────┐  ┌───▼────┐
        │ MySQL │  │  Redis  │  │ MinIO  │
        │ 事实源 │  │ 锁/库存/ │  │ 对象存储│
        │       │  │ 等待室   │  │        │
        └───────┘  └─────────┘  └────────┘
```

一致性原则：**MySQL 是唯一事实源，Redis 只做加速与准入**。Redis 丢失后可从数据库重建，不会导致超卖。

## 3. 代码结构

单模块 Maven，按功能分包；管理端接口放在各功能包内，统一 `/api/admin/**` 前缀，不单设 admin 包。

```
com.cdwater.ticketbox
├── common        # 异常、ProblemDetail、分页、校验
├── auth          # 注册/登录、JWT、用户
├── event         # 演出、城市筛选、封面
├── performance   # 场次、区域、座位生成
├── seat          # 座位图查询、座位状态
├── order         # 下单、取消、超时、限购
├── payment       # mock 支付、回调
├── ticket        # 出票、核销
├── waitingroom   # 等待室准入
└── storage       # MinIO 上传、预签名 URL
```

## 4. 数据模型

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `user_account` | id, username UK, password_hash, role(USER/ADMIN), created_at | BCrypt |
| `event` | id, title, description, city, cover_object_key, status(DRAFT/PUBLISHED) | 演出属于某城市 |
| `performance` | id, event_id, starts_at, venue_name, venue_address, sale_start_at, waiting_room_enabled, admission_rate, token_ttl_seconds | 场馆为字符串 |
| `price_tier` | id, performance_id, name, price DECIMAL(10,2), stock | stock 仅无座场次使用 |
| `section` | id, performance_id, name, price_tier_id, row_count, seats_per_row | 区域同价 |
| `seat` | id, performance_id, section_id, row_no, seat_no, UK(performance_id, section_id, row_no, seat_no) | 出售状态不落库 |
| `orders` | id, order_no UK, user_id, performance_id, status(PENDING_PAYMENT/PAID/CANCELLED), total_amount, idempotency_key, expires_at, created_at, paid_at, UK(user_id, idempotency_key) | 一个订单只含一个场次 |
| `order_item` | id, order_id, price_tier_id, seat_id NULL, quantity, unit_price | 有座：一票一行 qty=1；无座：qty=n |
| `ticket` | id, order_id, performance_id, price_tier_id, seat_id NULL, attendee_name, attendee_id_no, status(ISSUED/CHECKED_IN), checked_in_at, UK(performance_id, seat_id) | 支付成功时生成，防超卖最终防线 |
| `payment` | id, order_id, provider(MOCK), amount, status(PENDING/SUCCESS/FAILED/EXPIRED), provider_txn_no UK | 回调幂等键 |
| `purchase_quota` | user_id, performance_id, used_quantity, PK(user_id, performance_id) | 限购计数器，原子增减 |

座位可售状态为派生值：已售 = 存在 `ticket`；锁定 = Redis 键存在；其余可售。

## 5. 并发与库存

### 有座

1. `SET seat:{performanceId}:{seatId} <orderId> NX EX 900` 抢占，失败即 409 `SEAT_ALREADY_LOCKED`。
2. 写订单（`PENDING_PAYMENT`），`order_item` 记录 seat_id。
3. 支付成功插入 `ticket`，`UK(performance_id, seat_id)` 是最终防线。
4. 取消/超时删除锁键；Redis 重启后按 `PENDING_PAYMENT` 订单重建。

### 无座

1. Redis `DECRBY stock:{performanceId}:{tierId} n`，不足即 409 `SOLD_OUT`。
2. 数据库事务 `UPDATE price_tier SET stock = stock - n WHERE id = ? AND stock >= n`，影响行数为 0 则回滚并回补 Redis。
3. 取消/超时在数据库回补库存并同步 Redis；Redis 计数缺失时从数据库懒加载重建。

### 限购

`INSERT INTO purchase_quota ... ON DUPLICATE KEY UPDATE used = IF(used + n <= 4, used + n, used)`，校验结果；取消/超时回减。

### 热点场次等待室

- 仅 `waiting_room_enabled = true` 的场次启用。用户领号入 Redis 有序集合，按 `(now - sale_start_at) × admission_rate` 放行，轮询返回位次；获放行者拿到 Redis 准入令牌（TTL = `token_ttl_seconds`）。
- 选座/下单接口在限流场次要求令牌，缺失返回 409 `TOKEN_REQUIRED`。
- 未启用场次直连选座。

## 6. 关键流程

```
下单:   鉴权 → 等待室令牌 → Idempotency-Key 查重
        → 抢占座位/扣无座库存 → 事务写订单+明细+限购 → 15 分钟过期
支付:   POST /api/orders/{id}/payments 创建 mock 支付单
        → mock 网关 HTTP 回调 /api/payments/callback（HMAC 签名）
        → 按 provider_txn_no 幂等 → 订单 PAID → 生成 ticket → 释放座位锁
超时:   @Scheduled 每分钟扫 PENDING_PAYMENT 且过期 → CANCELLED
        → 释放座位锁 / 回补无座库存与限购 → 支付单 EXPIRED
核销:   管理端扫码 → POST /api/admin/tickets/check-in
        → 验 HMAC 签名 → 乐观更新 ISSUED → CHECKED_IN → 重复扫码 409
```

订单状态：`PENDING_PAYMENT → PAID | CANCELLED`。票状态独立：`ISSUED → CHECKED_IN`。

## 7. API 清单

统一：REST JSON、`/api/**`、错误用 RFC 7807 `ProblemDetail` + 业务 `code`、分页 `{items, page, size, total}`、金额 `DECIMAL(10,2)`、时间 UTC ISO-8601。

| 端 | 接口 |
| --- | --- |
| 认证 | `POST /api/auth/register`、`POST /api/auth/login`、`GET /api/me` |
| 浏览 | `GET /api/events`(城市/日期/分页)、`GET /api/events/{id}`、`GET /api/performances/{id}/seat-map` |
| 等待室 | `POST /api/performances/{id}/waiting-room/join`、`GET .../waiting-room/status` |
| 订单 | `POST /api/orders`(Idempotency-Key)、`GET /api/orders`、`GET /api/orders/{id}`、`POST /api/orders/{id}/cancel` |
| 支付 | `POST /api/orders/{id}/payments`、`POST /api/payments/callback`、mock 端模拟成功/超时 |
| 管理-演出 | `POST/PUT /api/admin/events`、`POST /api/admin/files` |
| 管理-场次 | `POST/PUT /api/admin/performances`、区域与座位生成、限流配置 |
| 管理-票档 | `POST/PUT /api/admin/price-tiers` |
| 管理-订单 | `GET /api/admin/orders`、`GET /api/admin/orders/{id}` |
| 管理-核销 | `POST /api/admin/tickets/check-in` |

业务错误码（示例）：`SEAT_ALREADY_LOCKED`、`SEAT_SOLD`、`SOLD_OUT`、`PURCHASE_LIMIT_EXCEEDED`、`ORDER_EXPIRED`、`TOKEN_REQUIRED`、`ALREADY_CHECKED_IN`、`INVALID_TICKET`。

## 8. 前端

```
frontend/
├── web/     React + TS + Tailwind + shadcn/ui，:5173
│   页面：演出列表、演出详情、等待室、选座、登录注册、我的订单、订单详情
└── admin/   Vue + TS + Element Plus，:5174
    页面：登录、演出管理、场次管理、票档管理、座位生成、订单查询、扫码核销
```

- 开发：Vite proxy `/api` → `http://localhost:8080`。
- 生产：构建产物复制进 Spring `static/`，web 挂 `/`，admin 挂 `/admin/`。
- 核销：`@zxing/browser` 摄像头扫码 + 手动输入票号兜底。

## 9. 安全

- JWT，`Authorization: Bearer`，24 小时，无 refresh；角色 `USER`/`ADMIN`。
- BCrypt 存密码；JWT secret、MinIO 凭据均来自环境变量。
- 上传文件校验类型与大小；MinIO 桶 `ticketbox` 不公开读，接口返回 7 天预签名 GET URL。
- 二维码内容：HMAC 签名的 `ticketId + performanceId + 时间戳`，一次性核销。

## 10. 部署与配置

`ops/docker-compose.yml`：

| 服务 | 镜像 | 端口 | 容器名 | 卷 | 凭据 |
| --- | --- | --- | --- | --- | --- |
| mysql | `mysql:8.0.46` | 3306 | `ticketbox-mysql` | `mysql-data` | 用户 `ticketmysql`/`ticketmysql`，root 密码 `ticketmysql`，库 `ticketbox` |
| redis | `redis:7.4.11` | 6379 | `ticketbox-redis` | `redis-data` | `requirepass ticketredis` |
| minio | `quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z` | 9001(API) / 9002(控制台) | `ticketbox-minio` | `minio-data` | `ticketminio`/`ticketminio` |
| app | 本地构建（多阶段 Dockerfile） | 8080 | `ticketbox-app` | — | 环境变量注入 |

网络统一 `ticketbox-network`。开发时只起 mysql/redis/minio，后端 `./mvnw spring-boot:run`；生产 `docker compose up` 全量启动。

Flyway 管 schema。`dev` profile 的 `ApplicationRunner` 创建管理员（环境变量注入，dev 有默认值）和演示数据（1 场对号入座 + 1 场无座）；生产不启用。

## 11. 测试

Testcontainers（MySQL + Redis），不使用 H2。必须覆盖：

1. 两线程抢同一位，仅一方成功；
2. 无座并发扣减不超卖；
3. 重复 `Idempotency-Key` 只产生一个订单；
4. 支付回调重复到达幂等；
5. 超时取消后座位/库存可再售。

不做 k6、Playwright、CI（本期）。

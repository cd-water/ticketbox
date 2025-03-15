# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Existing codebase: Spring Boot 4 / Java 21 monolith; React + TypeScript (Vite) user app; Vue + TypeScript (Vite, Element Plus) admin app. Confirmed in `docs/design.md`.

## Users

- 购票用户：在城市化的演出目录里发现演出，在开票峰值抢票、选座、支付，并凭电子票入场。
- 运营/管理员：发布演出与场次、配置票档与座位、控制热点场次流量、查询订单、扫码核销。

## Product Purpose

自营式演出票务平台，让购票用户完成"浏览 → 选座 → 下单 → 支付 → 电子票 → 核销"，并在热点场次并发下不超卖、不重复出票、超时释放库存。

## Positioning

单体部署也能演示真实抢票正确性：MySQL 唯一约束与条件更新是最终防线，Redis 只做选座锁、库存预扣与等待室准入，缓存丢失可从数据库重建。

## Operating Context

- 演出按城市与日期组织，一个演出有多个场次。
- 热点场次在开票瞬间涌入，非热点场次流量平稳。
- 支付在本期由 mock 网关经真实 HTTP 回调驱动。

## Capabilities and Constraints

Confirmed capabilities: 注册/登录（JWT）、演出浏览与筛选、场次与票档、座位图、热点场次等待室、下单与防超卖、mock 支付与出票、扫码核销。

Constraints: 单一运营方、单体、单机；个人作品集项目但按真实并发正确性设计。本期不做退款/换票、优惠券/会员、实名核验、搜索/推荐、多语言、真实支付渠道、CI 与外围测试。

## Product Principles

1. 正确性优先：并发下的库存与票据状态不可出错。
2. 熟悉胜过惊奇：票务流程使用用户已知的模式，视觉克制。
3. 峰值下保持清晰：排队、锁定、支付、超时等状态始终可见。
4. 一屏一任务：每个页面只推进当前步骤。

## Accessibility & Inclusion

键盘可操作，表单有可见标签与焦点环，正文对比度不低于 4.5:1。

## Evidence on Hand

- `docs/design.md`、`docs/adr/`、`GLOSSARY.md` 与 GitHub spec #13 是产品事实来源。
- 尚无真实演出、图片或客户素材；不得编造评价、客户或数据。开发环境种子数据仅用于演示。
- [assumed] 界面语言为简体中文；[assumed] 品牌视觉方向尚未由用户确认，本阶段登录页采用克制的产品化方向。

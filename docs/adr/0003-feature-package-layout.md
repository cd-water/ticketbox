# 按 feature 分包，包内再分 web 与 domain

项目是单模块 Spring Boot 单体，业务代码按 feature 分包后，每个包内部曾经是平铺的：Controller、请求/响应 record、实体、仓储、服务与 Redis 适配器混在一起。两个最大的业务包堆了数十个类，目录本身无法说明 HTTP 边界在哪、领域规则在哪，依赖方向也没有任何约束。

**决定**：每个业务 feature 内部再分两段：

- `web/` 放 Controller，以及只被 Controller 使用的映射器；
- `domain/` 放实体、仓储、服务、枚举，以及入站 command 与响应读模型；
- 配置类留在 feature 根（`SecurityConfig` 在 auth，`MinioConfig` 在 storage）；`dev` 的种子数据不拆分。

依赖方向固定为 `web → domain` 单向：任何代码不得依赖 `web`，Spring 的 `@Configuration` 除外（配置类负责装配过滤器等 web 组件）；`common` 不依赖任何业务 feature；feature 之间只允许依赖对方的 `domain`，`dev` 豁免。这三条规则由 `ArchitectureTest` 固化，违反即测试失败。

**考虑过的方案**：

- 严格分层——响应 record 全部放 `web`，服务只返回实体，由 Controller 映射。被否决：需要改写多个服务的返回值与现有映射器，为的是教科书式整齐，却引入真实行为风险。
- 四段式（`api` / `application` / `domain` / `infrastructure`）——被否决：`waitingroom` 只有两个类，四段式会造出空层，单体项目不为未来可能的分层预付成本。
- 顶层按技术层分包（`controller` / `service` / `repository` / `entity`）——被否决：一次业务改动要横跨五个目录，等于放弃按业务聚合。

实施过程中还发现一处原始规则的自相矛盾：入站 command 若留在 `web`，接收它的 domain 服务就会反向依赖 `web`。因此 command 随其服务放在 `domain`，`web` 只保留 Controller 与映射器。响应 record 同理，它是该 feature 的读模型，不含任何 Spring MVC 类型，不当作 HTTP 传输对象。

**后果**：

- 新增或移动 feature 时，要同步更新引用它的 feature 的 import；小步、逐 feature 提交可保证每次编译通过。
- `web` 包是被框架反射调用的终点，不能作为其他代码的依赖来源；`@Configuration` 是唯一显式豁免。
- 入站 command 与响应读模型放在 `domain`，其他 feature 会直接依赖它们，因此它们成为 feature 间的契约：改名或改形状就是跨 feature 变更。
- 测试布局跟随职责：HTTP 测试留在 feature 根，模块测试进 `domain`，共享测试夹具集中在 `support`。

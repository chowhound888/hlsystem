# 药品管理分系统

Spring Boot 3 / Java 21 / MyBatis-Plus / MySQL 8.4，Vue 3 / TypeScript / Element Plus。当前实现为课程项目的第一版业务应用，六大模块均有真实数据库驱动的核心流程。完整需求设计见 [docs/README.md](docs/README.md)，当前覆盖范围见 [docs/08-implementation-status.md](docs/08-implementation-status.md)。

## 本机连接

| 项目 | 值 |
| --- | --- |
| 前端 | http://127.0.0.1:5173 |
| 后端 | http://127.0.0.1:8080 |
| 健康检查 | http://127.0.0.1:8080/api/v1/health |
| 数据库 | MySQL 8.4.9，127.0.0.1:3307，hlsystem |
| 数据库用户 | memconi |

数据库密码仅存在 `back/src/main/resources/application-local.yml`，该文件被 Git 忽略。通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 也可覆盖连接。启动执行 Flyway 增量迁移；不会删除已有数据。

## 启动

后端支持从项目根目录或 `back` 启动，均能加载本地数据库配置。本地配置不会被打包进 JAR。Java 21 已安装，本机 Maven 位于 IDEA 安装目录；其他电脑可直接使用已加入 PATH 的 `mvn`。

### IDEA 启动

打开项目根目录后，右键根目录 `pom.xml`，选择 **Add as Maven Project（添加为 Maven 项目）**，等待依赖导入完成。已导入时在 Maven 工具窗口选择 **Reload All Maven Projects**。

Project SDK 与 Maven Importer JDK 选择 **21**。导入后可直接运行 `back/src/main/java/com/hlsystem/HlsystemApplication.java` 的 `main` 方法，或选择共享运行配置 **HlsystemApplication**（工作目录预设为 `back`）。

如果出现 `程序包 com.baomidou.mybatisplus... 不存在`，在 Maven 工具窗口执行 **Reload All Maven Projects** 并等待全部依赖导入完成。本地 Maven 编译通过但 IDEA 编译失败时，通常是 IDEA 模块的依赖类路径尚未同步。项目提供 **Backend (Maven)** 共享配置，可直接用 Maven 执行 `compile spring-boot:run`。

可在 **Settings → Build, Execution, Deployment → Build Tools → Maven → Runner** 勾选 **Delegate IDE build/run actions to Maven**。若 IDEA 导入日志报 `Could not acquire lock(s)`，待其他 Maven 命令结束后重新加载 Maven；持续出现时关闭并重新打开 IDEA，再导入 `back/pom.xml`。

启动前保证 8080 未被另一份后端占用。数据库配置从被 Git 忽略的 `back/src/main/resources/application-local.yml` 加载，不需要在 IDEA 中再次填写数据库密码。

```powershell
cd D:\code\hlsystem\back
& 'D:\Program Files\develop software\IntelliJ IDEA 2025.2\plugins\maven\lib\maven3\bin\mvn.cmd' -DskipTests package
& .\start.ps1
```

```powershell
cd D:\code\hlsystem\front
npm install
& .\start.ps1
```

### HBuilder X 启动

可在 HBuilder X 中打开整个 `D:\code\hlsystem` 项目。根目录 `package.json` 的 `dev`、`start`、`build`、`preview` 命令会转发到 `front`。重新加载项目后，从 `package.json` 的 npm 脚本入口运行 `dev`。

也可以使用 **文件 → 导入 → 从本地目录导入**，单独导入 `D:\code\hlsystem\front`。HBuilder X 根据已导入项目的根目录识别 `package.json`，仅选中父项目下面的 `front` 文件夹不一定会改变项目根目录。

终端方式在项目根目录执行：

```powershell
cd D:\code\hlsystem
npm run dev
```

依赖安装仍在 `front` 执行，或在根目录执行 `npm --prefix front install`。这是 Vue 3 + Vite 项目，使用 npm 脚本启动。开发服务器输出实际访问地址；后端在 IDEA 中启动，端口为 8080。

也可以在终端前台运行 `java -jar target/hlsystem-0.1.0.jar` 与 `npm run dev`。后台脚本记录 PID 和日志到项目 `.runtime`，端口占用时会拒绝启动，不终止其他程序。

## 演示账号

所有初始演示账号密码为 `123456`。

| 账号 | 角色 |
| --- | --- |
| admin | 管理员，拥有课程演示所需业务权限 |
| warehouse / warehouse2 | 药库制单与审核 |
| pharmacist / pharmacist2 | 门诊复核与发药 |
| inpatient | 住院药师 |
| accountant | 药品会计 |
| preparation / quality | 制剂生产与独立质检 |
| doctor | 内科医生 |

同一人不能审核自己制作的库存、采购、生产或调价单；门诊和住院复核者与发药者不能相同。可使用管理员配合对应业务账号演示。

选择对应机构后操作：医生门诊处方选择门诊药房；住院医嘱选择住院药房；生产和原料质检选择制剂库；病区基数药选择外科病区。界面机构切换不扩大账号授权。

## 演示初始化与检查

本机配置启用了 `app.demo.enabled`。初始化在空账号表时创建演示账号，业务示例由独立标记控制，只导入一次。库存使用真实入库单与流水初始化，包含近效期批次和待检材料。之后启动不会清库或覆盖用户数据。

如需把当前数据库扩展为更丰富的答辩演示数据，在后端目录执行：

```powershell
cd D:\code\hlsystem\back
& .\populate-demo.ps1
```

脚本会先在 `.runtime` 生成数据库备份，再导入约 30 个药品规格、30 个患者、门诊与住院记录、处方、医嘱、批次库存、合理用药规则、制剂配方和多个待处理单据。扩展数据通过 `expanded_demo_v1` 标记保证只导入一次；已有业务数据不会清空。新增教学医生账号为 `doctor2`、`doctor3`，密码同样是 `123456`。

以下测试在运行中的本项目数据库执行，会新增带验收说明的交易记录；用于课程演示库，不对其他数据库执行。

```powershell
cd D:\code\hlsystem\front
npm run build
npm test
npm run test:ui
```

API 检查覆盖登录、范围权限、处方发药、预留、双人规则、幂等、退药退款、调拨分次接收、生产质检、成本、住院、盘点冻结、结账重开及 Excel 输出。UI 检查使用本机 Edge，截图写入 `.runtime/screenshots`。

## 文件说明

- `front/src/modules`：按业务组织的页面。
- `back/src/main/java/com/hlsystem`：权限、基础资料、库存、临床、药房、会计、制剂和规则服务。
- `back/src/main/resources/db/migration`：版本化建表与升级 SQL。
- `docs`：需求、架构、数据库、UI、接口及实施状态。

合理用药资料明确为课程示例。自动调价必须先由不同人员批准；未检原料不能用于生产；待结算产出不能出售。账务报表区分库存成本、零售估值与患者收款。

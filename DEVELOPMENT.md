# 国邦运输管理系统 — 开发文档

## 1. 项目概述

国邦运输管理系统（Guobang Transport）是一个基于 Spring Boot 3 + Vue 3 + PostgreSQL 的运输管理系统，主要用于管理运输记录、过磅单审核、OCR 扫描识别、运价维护、月度报表等功能。

### 1.1 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.3.6 |
| Java 版本 | Java | 17+ |
| ORM | MyBatis-Plus | 3.5.9 |
| 数据库 | PostgreSQL | 12+ |
| 前端框架 | Vue | 3.5.34 |
| UI 组件库 | Naive UI | 2.44.1 |
| 构建工具 | Vite | 8.0.12 |
| 类型系统 | TypeScript | 6.0.3 |
| 状态管理 | Pinia | 3.0.4 |
| 路由 | Vue Router | 5.0.7 |
| CSS 框架 | UnoCSS | - |
| 代码规范 | oxlint + eslint | - |
| 格式化 | oxfmt | - |
| 代码生成 | Lombok | 1.18.38 |

### 1.2 端口配置

| 服务 | 端口 | 配置方式 |
|------|------|----------|
| 后端 | 8000 | `SERVER_PORT` 环境变量 |
| 前端 | 9527 | `vite.config.ts` |

---

## 2. 项目结构

### 2.1 后端结构

```
backend/src/main/java/com/guobang/transport/
├── common/              # 公共工具类
│   ├── Api.java         # 统一响应封装
│   ├── BusinessException.java  # 业务异常
│   ├── DateRange.java   # 日期范围
│   ├── DateSupport.java # 日期工具
│   └── GlobalExceptionHandler.java  # 全局异常处理
├── auth/                # 认证模块
│   ├── AuthController.java  # 登录/登出接口
│   ├── AuthFilter.java      # Cookie 认证过滤器
│   └── AuthService.java     # 认证服务
├── record/              # 运输记录模块
│   ├── Record.java      # 记录实体
│   ├── RecordController.java  # 记录 CRUD 接口
│   ├── RecordService.java     # 记录服务
│   └── ExportSupport.java     # 导出工具
├── collection/          # 基础资料模块
│   ├── Collection.java  # 基础资料实体
│   ├── CollectionController.java  # 基础资料接口
│   └── CollectionService.java     # 基础资料服务
├── rate/                # 运价模块
│   ├── FreightRate.java # 运价实体
│   ├── RateController.java  # 运价接口
│   └── RateService.java     # 运价服务
├── report/              # 月度报表模块
│   ├── ReportController.java  # 报表接口
│   └── ReportService.java     # 报表服务
├── quality/             # 数据质量模块
│   ├── DataQualityController.java  # 质量检查接口
│   └── DataQualityService.java     # 质量检查服务
├── image/               # 图片模块
│   ├── Image.java       # 图片实体
│   ├── ImageController.java   # 图片接口
│   └── ImageService.java      # 图片服务
├── ocr/                 # OCR 模块
│   ├── OcrTask.java     # OCR 任务实体
│   ├── OcrController.java     # OCR 扫描接口
│   ├── AdminOcrController.java # OCR 管理接口
│   └── OcrService.java        # OCR 服务
├── mapper/              # MyBatis Mapper 接口
│   ├── RecordMapper.java
│   ├── CollectionMapper.java
│   ├── FreightRateMapper.java
│   ├── ReportMapper.java
│   ├── DataQualityMapper.java
│   ├── ImageMapper.java
│   └── OcrTaskMapper.java
├── db/                  # 数据库工具
│   └── DbSupport.java
└── config/              # 配置类
    ├── DatabaseInitializer.java  # 数据库初始化
    └── WebConfig.java            # Web 配置
```

### 2.2 前端结构

```
frontend/src/
├── views/
│   ├── records/         # 运输记录页面
│   ├── review/          # 过磅单审核页面
│   ├── rates/           # 运价管理页面
│   ├── collections/     # 基础资料管理页面
│   ├── report/          # 月度报表页面
│   ├── data-quality/    # 数据质量检查页面
│   ├── images/          # 图片管理页面
│   ├── ocr/             # OCR 扫描页面
│   └── home/            # 首页
├── service/
│   ├── api/
│   │   ├── business.ts  # 业务 API 函数
│   │   └── auth.ts      # 认证 API 函数
│   └── request/
│       └── index.ts     # Axios 配置
├── store/               # Pinia 状态管理
├── layouts/             # 布局组件
│   ├── base-layout/     # 基础布局
│   └── modules/         # 布局模块（header、footer、tab 等）
└── router/              # 路由配置
```

---

## 3. 快速启动

### 3.1 后端启动

```bash
# 进入后端目录
cd backend

# 设置环境变量并启动
TRANSPORT_AUTH_PASSWORD=552300 \
TRANSPORT_AUTH_SECRET=guobang-secret-key-2024 \
PG_PASSWORD=552300 \
mvn spring-boot:run
```

**必需环境变量**：`TRANSPORT_AUTH_SECRET` — 如果未设置，所有 API 返回 503。

### 3.2 前端启动

```bash
# 进入前端目录
cd frontend

# 安装依赖
pnpm install

# 启动开发服务器（端口 9527）
pnpm dev
```

### 3.3 构建

```bash
# 前端构建
pnpm build        # 生产环境构建
pnpm build:test   # 测试环境构建

# 后端构建
mvn package       # 生成 JAR 文件
```

---

## 4. 代码规范

### 4.1 Pre-commit Hook

提交前自动运行：
```bash
pnpm typecheck && pnpm lint && pnpm fmt && git diff --exit-code
```

### 4.2 单独运行检查

```bash
pnpm typecheck    # TypeScript 类型检查
pnpm lint         # 代码规范检查（oxlint + eslint）
pnpm fmt          # 代码格式化（oxfmt）
```

### 4.3 后端编译

```bash
mvn compile       # 编译后端代码
mvn clean compile # 清理并重新编译
```

---

## 5. API 约定

### 5.1 响应格式

所有 API 响应使用 `{ok: true/false}` 格式，**不是**标准 HTTP 状态码。

```typescript
// 前端成功判断
isBackendSuccess(response) {
  return response.data?.ok === true;
}
```

### 5.2 后端响应模式

```java
// 成功响应
return Api.ok();                           // {ok: true}
return Api.ok("key", value);               // {ok: true, key: value}
return Api.ok(Map.of("k1", v1, "k2", v2)); // {ok: true, k1: v1, k2: v2}

// 错误响应
return Api.error("message", HttpStatus.BAD_REQUEST); // {ok: false, error: "message", status: 400}
```

**注意**：`Api.ok()` 不接受单个 List 参数，应使用 `Api.ok("key", list)`。

### 5.3 认证方式

- 基于 Cookie 的会话认证
- 前端在 localStorage 存储 token 用于路由守卫
- 401 响应触发自动登出

### 5.4 分页参数

统一使用 `offset` 和 `limit` 参数：
- `offset`：偏移量，默认 0
- `limit`：每页数量，默认 20

分页返回格式：
```json
{
  "ok": true,
  "items": [...],
  "total": 100
}
```

---

## 6. 数据库

### 6.1 连接配置

```yaml
# application.yml
spring.datasource:
  url: jdbc:postgresql://${PG_HOST:127.0.0.1}:${PG_PORT:5432}/${PG_DATABASE:transport}?sslmode=disable
  username: ${PG_USER:transport}
  password: ${PG_PASSWORD:}
```

### 6.2 MyBatis-Plus 配置

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true  # 下划线转驼峰
  global-config:
    db-config:
      id-type: auto  # 自增 ID
```

### 6.3 主要数据表

| 表名 | 说明 |
|------|------|
| `records` | 运输记录 |
| `images` | 图片数据 |
| `record_images` | 记录-图片关联 |
| `collections` | 基础资料（开单公司、发货单位、收货单位、车牌号） |
| `freight_rates` | 运价 |
| `ocr_tasks` | OCR 任务 |

---

## 7. 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `TRANSPORT_AUTH_SECRET` | **必需** — JWT 认证密钥 | （无，未设置返回 503） |
| `TRANSPORT_AUTH_PASSWORD` | 登录密码 | （无） |
| `PG_PASSWORD` | PostgreSQL 密码 | （空） |
| `PG_HOST` | PostgreSQL 主机 | 127.0.0.1 |
| `PG_PORT` | PostgreSQL 端口 | 5432 |
| `PG_DATABASE` | 数据库名称 | transport |
| `PG_USER` | 数据库用户 | transport |
| `SERVER_PORT` | 后端端口 | 8000 |
| `BACKEND_BASE_URL` | 后端 URL（图片上传用） | http://localhost:8000 |
| `PADDLE_API_URL` | PaddleOCR API 地址 | https://paddleocr.aistudio-app.com/api/v2/ocr/jobs |
| `PADDLE_API_TOKEN` | PaddleOCR API Token | （硬编码默认值） |
| `PADDLE_MODEL` | PaddleOCR 模型名称 | PaddleOCR-VL-1.6 |
| `PADDLE_TIMEOUT_SEC` | PaddleOCR 任务超时（秒） | 900 |
| `PADDLE_POLL_INTERVAL_SEC` | PaddleOCR 轮询间隔（秒） | 4 |
| `BAIDU_API_KEY` | 百度 OCR API Key | （硬编码默认值） |
| `BAIDU_SECRET_KEY` | 百度 OCR Secret Key | （硬编码默认值） |

---

## 8. 功能模块说明

### 8.1 运输记录（records）

- 手动录入运输记录
- OCR 扫描自动识别
- 记录查询、筛选、分页
- 记录导出（CSV/XLS）
- 记录审核

### 8.2 过磅单审核（review）

- 上一条/下一条导航模式
- 审核备注
- 图片查看

### 8.3 运价管理（rates）

- 运价 CRUD
- 按线路、日期查询运价
- 分页显示

### 8.4 基础资料（collections）

- 开单公司、发货单位、收货单位、车牌号管理
- 分类筛选
- 分页显示

### 8.5 月度报表（report）

- 按月统计运输数据
- 按公司、收货单位分组
- 关键词搜索

### 8.6 数据质量（data-quality）

- 发件人≠开单公司检查
- 重复单号检查
- 基础资料完整性检查
- 未审核记录检查

### 8.7 图片管理（images）

- 图片上传、更新、删除
- 缩略图自动生成
- 图片导出（CSV/XLS/ZIP）

### 8.8 OCR 扫描（ocr）

- PaddleOCR 识别
- 百度 OCR 识别
- 自动提取运输记录字段
- OCR 任务管理

---

## 9. 代码规范

### 9.1 Lombok 使用

所有 Service 和 Controller 使用 `@RequiredArgsConstructor` 进行构造函数注入：

```java
@Service
@RequiredArgsConstructor
public class XxxService {
    private final XxxMapper xxxMapper;
}
```

### 9.2 注释规范

- 类级别：中文 Javadoc（`/** 类描述 */`）
- 方法级别：中文 Javadoc（`@param`、`@return`）
- 行内：中文 `//` 注释，解释"做什么"和"为什么"

### 9.3 分页实现

后端分页有两种模式：
1. 原生 SQL + `LIMIT #{limit} OFFSET #{offset}`
2. MyBatis-Plus QueryWrapper + `.last("LIMIT " + limit + " OFFSET " + offset)`

---

## 10. 注意事项

1. **API 成功判断**：使用 `response.data?.ok === true`，不是 HTTP 状态码
2. **认证过滤器**：`TRANSPORT_AUTH_SECRET` 未设置时返回 503
3. **导出接口**：返回 `text/csv` 或 `application/vnd.ms-excel`，带 `Content-Disposition` 头
4. **图片存储**：使用 AWS S3 兼容存储
5. **前端代理**：`/api` 请求代理到 `http://localhost:8000`
6. **naive-ui date-picker**：空值必须传 `null`，不能传空字符串 `""`
7. **后端没有 Maven Wrapper**：需直接使用 `mvn` 命令
8. **前端命令**：`pnpm typecheck` 和 `pnpm lint` 必须在 `frontend/` 目录下运行

---

## 11. 开发历史

### 2026-06-18 主要改动

1. **分页功能**：为 rates、collections、data-quality 添加前后端分页支持
2. **底部版权移除**：移除 "Copyright MIT © 2021 Soybean"
3. **顶部标签栏隐藏**：默认隐藏 Tab 栏
4. **司机功能移除**：前后端和数据库完全移除 driver 相关代码
5. **OCR 配置提取**：将 API 密钥、URL 等从硬编码移到 application.yml
6. **Lombok 重构**：所有 Service 和 Controller 使用 `@RequiredArgsConstructor`
7. **中文注释**：所有 Java 文件添加类级、方法级和行内中文注释

---

## 12. 四阶段全面修复（2026-06-18）

### 12.1 阶段一：安全与稳定性修复

| 修复项 | 文件 | 说明 |
|--------|------|------|
| 移除硬编码 API 密钥 | `application.yml` | PaddleOCR Token、百度 OCR API Key/Secret 默认值清空，必须通过环境变量设置 |
| 修复 searchTimer 内存泄漏 | `images/index.vue` | 添加 `onBeforeUnmount` 清理定时器 |
| 修复 createObjectURL 内存泄漏 | `records/index.vue`、`images/index.vue` | 添加 `revokeObjectURL` 释放内存 |
| 修复 7 处空 catch 块 | 5 个文件 | 添加 `console.error` 记录错误 |
| 优化 ImageService.imageRow() | `ImageService.java` | 从三次查询改为一次查询 + 缓存 |

### 12.2 阶段二：代码质量

| 改进项 | 文件 | 说明 |
|--------|------|------|
| 提取 useImageEditor composable | `hooks/business/image-editor.ts` | 183 行 Canvas 编辑器代码提取为可复用 composable |
| 重构 images/index.vue | `views/images/index.vue` | 删除 ~120 行重复编辑器代码 |
| 重构 review/index.vue | `views/review/index.vue` | 删除 ~140 行旧编辑器函数 |
| 创建 TypeScript 类型定义 | `service/api/types.ts` | 定义所有 API 接口类型（215+ 行） |
| 更新 business.ts | `service/api/business.ts` | 替换所有 `any` 为具体类型 |
| 实现 AbortController 请求取消 | 多个文件 | 避免竞态条件，组件卸载时取消请求 |
| 优化 RecordService N+1 查询 | `RecordService.java` | 从 N+1 次查询改为单次批量查询 |
| 修复 FreightRate 建表 SQL | `DatabaseInitializer.java` | 添加 `sender` 列 |

### 12.3 阶段三：工程化

| 改进项 | 文件 | 说明 |
|--------|------|------|
| Flyway 数据库迁移 | `pom.xml`、`application.yml` | 添加 Flyway 依赖，创建 V1 初始迁移脚本 |
| Docker 配置 | `docker-compose.yml`、`Dockerfile` | 后端、前端、PostgreSQL 容器化部署 |
| Nginx 配置 | `frontend/nginx.conf` | 前端静态资源服务 + API 代理 |
| Swagger/OpenAPI | `pom.xml` | 集成 springdoc-openapi，访问 /swagger-ui.html |
| 基础测试 | `TransportApplicationTests.java` | Spring Boot 上下文加载测试 |

### 12.4 阶段四：持续优化

| 改进项 | 文件 | 说明 |
|--------|------|------|
| 表单验证 | `rates/index.vue` | 运价表单添加必填项验证（开单公司、收货单位、单价、起始日期） |

---

## 13. 新增 Composable 说明

### 13.1 useImageEditor

**位置**：`frontend/src/hooks/business/image-editor.ts`

**功能**：图片编辑器核心逻辑，支持裁剪、旋转、拖拽等操作。

**API**：
```typescript
const {
  editorImg,           // 编辑器图片对象
  editorCrop,          // 裁剪区域 { x, y, w, h }
  editorRotation,      // 旋转角度
  dragging,            // 是否拖拽中
  openEditor,          // 打开编辑器 (base64: string) => void
  resetEditor,         // 重置编辑器
  startDrag,           // 开始拖拽
  onDrag,              // 拖拽中
  endDrag,             // 结束拖拽
  applyCrop,           // 应用裁剪
  rotate               // 旋转图片
} = useImageEditor(options);
```

**使用示例**：
```typescript
const { openEditor, resetEditor } = useImageEditor({
  onEdited: (base64) => { editedImage.value = base64; },
  getInitialBase64: () => originalImage.value
});
```

### 13.2 useAbortController

**位置**：`frontend/src/hooks/business/useAbortController.ts`

**功能**：管理 AbortController，用于取消 HTTP 请求。

**API**：
```typescript
const { createController, abort, getSignal } = useAbortController();

// 发起请求前创建 controller
const controller = createController();
await fetchRecords(params, controller.signal);

// 组件卸载时取消请求
onBeforeUnmount(() => abort());
```

---

## 14. Docker 部署指南

### 14.1 环境变量配置

创建 `.env` 文件：
```bash
PG_PASSWORD=your_password
TRANSPORT_AUTH_SECRET=your_secret_key
TRANSPORT_AUTH_PASSWORD=your_login_password
```

### 14.2 启动服务

```bash
# 构建并启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

### 14.3 访问地址

- 前端：http://localhost:9527
- 后端 API：http://localhost:8000
- Swagger UI：http://localhost:8000/swagger-ui.html

---

## 15. Flyway 数据库迁移

### 15.1 迁移文件位置

```
backend/src/main/resources/db/migration/
├── V1__init_schema.sql    # 初始建表脚本
└── V2__xxx.sql            # 后续迁移
```

### 15.2 迁移命令

```bash
# 自动迁移（应用启动时）
mvn spring-boot:run

# 手动迁移
mvn flyway:migrate
```

### 15.3 禁用 Flyway

```bash
# 设置环境变量
FLYWAY_ENABLED=false
```

---

## 16. 测试

### 16.1 后端测试

```bash
cd backend
mvn test
```

### 16.2 前端测试

```bash
cd frontend
pnpm typecheck    # TypeScript 类型检查
pnpm lint         # 代码规范检查
pnpm fmt          # 代码格式化
```

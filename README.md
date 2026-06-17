# 国邦运输管理系统 (Guobang Transport)

Spring Boot 3 + Vue 3 + PostgreSQL 运输管理系统

## 快速开始

### 1. 用 IDEA 打开项目

直接用 IntelliJ IDEA 打开 `guobang-spring-vue` 目录，IDEA 会自动识别为 Maven 项目。

### 2. 运行后端

在 IDEA 中找到 `TransportApplication` 运行配置，直接运行即可。

环境变量已配置好：
- `TRANSPORT_AUTH_PASSWORD`: 552300
- `TRANSPORT_AUTH_SECRET`: 已配置
- `PG_PASSWORD`: 552300

后端启动后监听端口: **8000**

### 3. 运行前端

```bash
cd frontend
npm install
npm run dev
```

前端启动后访问: http://localhost:5173

### 4. 登录

访问 http://localhost:5173/login，输入密码: `552300`

## 技术栈

- **后端**: Spring Boot 3.3.6, Java 17+, PostgreSQL, AWS S3 (JD Cloud OSS)
- **前端**: Vue 3.5, Naive UI 2.40, Vite 6.4, TypeScript

## 项目结构

```
guobang-spring-vue/
├── backend/           # Spring Boot 后端
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/guobang/transport/
│   │   │   └── resources/
│   │   └── test/
│   └── pom.xml
├── frontend/          # Vue 3 前端
│   ├── src/
│   │   ├── api/
│   │   ├── views/
│   │   └── ...
│   └── package.json
└── pom.xml            # 父 POM
```

## 功能模块

- 登录认证
- 运输记录管理
- 过磅单审核
- 图片管理
- OCR 扫描
- 运价维护
- 集合管理
- 月度报表
- 数据质量检查

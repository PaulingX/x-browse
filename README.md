# x-browse 多媒体浏览系统

基于 Spring Boot + Vue 3 的多媒体图片视频浏览系统，对接 Alist 存储引擎。

## 功能特性

- Alist 存储引擎管理（添加/修改/删除）
- 文件目录浏览，复用 Alist 引擎目录结构
- 图片双模式：大图预览 + 瀑布流无限滚动
- 视频在线播放，支持进度拖拽、全屏、静音
- 本地文件缓存，定时增量扫描
- 缩略图生成开关
- 用户登录 + 目录权限管控
- 只读浏览权限

## 快速开始

### Docker 部署（推荐）

```bash
cd docker

# 复制并修改环境变量
cp .env .env.bak  # 可选

# 启动
docker-compose up -d --build

# 访问
# http://localhost:2041
```

默认账户：`admin` / `admin123`

### 自定义端口

修改 `docker-compose.yml` 中的 `PORT` 环境变量，或直接改 `ports` 映射：

```yaml
ports:
  - "9090:8080"  # 外部使用 9090 端口
```

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| PORT | 对外端口 | 2041 |
| XBROWSE_PORT | 服务端口 | 8080 |
| XBROWSE_DATA_DIR | 数据目录 | /app/data |
| XBROWSE_CACHE_DIR | 缓存目录 | /app/data/cache |
| XBROWSE_THUMBNAIL_ENABLED | 缩略图开关 | true |
| XBROWSE_JWT_SECRET | JWT 密钥 | - |
| XBROWSE_JWT_EXPIRATION | JWT 过期时间（秒） | 604800 |
| XBROWSE_ADMIN_PASSWORD | 管理员密码 | admin123 |

### 开发环境

后端：
```bash
cd backend
mvn spring-boot:run
```

前端：
```bash
cd frontend
npm install
npm run dev
```

## 项目结构

```
x-browse/
├── backend/           # Spring Boot 后端
│   └── src/main/java/com/xbrowse/
│       ├── config/        # 配置（Security、Web、异常处理）
│       ├── controller/    # REST API
│       ├── dto/           # 数据传输对象
│       ├── entity/        # JPA 实体
│       ├── repository/    # 数据访问层
│       ├── security/      # JWT 认证
│       ├── service/       # 业务逻辑
│       └── util/          # Alist 客户端
├── frontend/          # Vue 3 + Vant 4 前端
│   └── src/
│       ├── api/           # Axios 封装
│       ├── router/        # 路由
│       ├── stores/        # Pinia 状态
│       ├── styles/        # 全局样式
│       └── views/         # 页面组件
├── docker/            # Docker 部署
│   ├── Dockerfile         # 单容器构建
│   └── docker-compose.yml
└── README.md
```

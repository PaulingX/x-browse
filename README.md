# x-browse 多媒体浏览系统

基于 **Spring Boot 3 + Vue 3 + Vant 4** 的多媒体图片/视频浏览系统，对接 [Alist](https://alist.nn.ci/) 作为存储引擎。

数据落在本地 SQLite，列表优先读库；图片列表使用本地缩略图，原图按需加载；视频支持 Range 流式播放。

## 功能特性

### 存储与目录
- **Alist 引擎管理**：添加 / 修改 / 删除，连接校验
- **浏览目录（同步根）**：管理员配置可浏览的 Alist 路径，不整库扫描引擎根目录
- **按目录独立同步**：
  - 间隔同步：分钟 / 小时 / 天 / 月
  - Cron 同步（6 位：秒 分 时 日 月 周）
  - 关闭自动同步（仅手动）
  - 列表展示上次/下次同步时间，支持单目录立即同步
- **用户目录权限**：非管理员仅可见分配的浏览目录

### 浏览与媒体
- 目录网格浏览、瀑布流（图片）
- **图片**
  - 列表优先显示本地缩略图（约 200px）
  - 查看器：先缩略图再换原图
  - 大图预览：连续滚动 / 单张滑动
- **视频**
  - 在线播放（HTML5 + Range 拖动进度）
  - 倍速、上一个/下一个、键盘快捷键
  - 同目录同名图片（如 `demo.mp4` + `demo.jpg`）作为视频封面
- 搜索（当前目录已同步数据）
- 中文路径支持（URL 编解码）

### 缓存与性能
- 同步时为图片生成列表缩略图：`data/cache/thumbnails/`
- 可选 proxy 原图磁盘缓存：`data/cache/proxy/`
- 响应头 `Cache-Control`，利于浏览器二次加载
- 前端列表预加载限流（并发/批量），减少弱网卡顿

### 账号
- JWT 登录
- 用户管理、改密、目录授权
- 默认管理员：`admin` / `admin123`（可通过环境变量修改）

## 快速开始

### Docker 部署（推荐）

```bash
cd docker

# 启动
docker-compose up -d --build

# 访问
# http://localhost:2041
```

默认账户：`admin` / `admin123`

### 自定义端口

修改 `docker-compose.yml` 中的 `ports` 或相关环境变量，例如：

```yaml
ports:
  - "9090:8080"  # 外部使用 9090
```

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| PORT | 对外端口（compose） | 2041 |
| XBROWSE_PORT | 服务端口 | 8080 |
| XBROWSE_DATA_DIR | 数据目录（含 SQLite） | /app/data 或 ./data |
| XBROWSE_CACHE_DIR | 缓存目录（缩略图/proxy） | /app/data/cache 或 ./data/cache |
| XBROWSE_THUMBNAIL_ENABLED | 是否生成缩略图 | true |
| XBROWSE_THUMBNAIL_MAX_WIDTH | 列表缩略图最大边长 | 200 |
| XBROWSE_IMAGE_CACHE_SECONDS | 浏览器缓存秒数 | 86400 |
| XBROWSE_PROXY_CACHE_ENABLED | 是否缓存 proxy 原图到磁盘 | true |
| XBROWSE_JWT_SECRET | JWT 密钥 | （需生产环境修改） |
| XBROWSE_JWT_EXPIRATION | JWT 过期时间（秒） | 604800 |
| XBROWSE_ADMIN_PASSWORD | 初始管理员密码 | admin123 |

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

前端开发默认走 Vite 代理到后端；生产构建后可由同一 Spring Boot 托管静态资源。

## 使用流程

1. 管理员登录 → **引擎管理** 添加 Alist（地址 + Token）
2. **浏览目录** 选择引擎下路径，配置同步策略并保存（保存后会同步该路径）
3. （可选）在 **用户管理** 中为普通用户分配可访问的浏览目录
4. 首页进入目录浏览图片/视频；热目录可点「同步」立即刷新

> 新目录首次同步会拉取列表并生成缩略图，时间与文件量相关；之后列表主要读本地缩略图，会快很多。

## 项目结构

```
x-browse/
├── backend/                 # Spring Boot 后端
│   └── src/main/java/com/xbrowse/
│       ├── config/          # Security、Web、异常处理、初始化
│       ├── controller/      # REST API
│       ├── dto/
│       ├── entity/          # 引擎、浏览目录、file_directory、dir_file、用户权限等
│       ├── repository/
│       ├── security/        # JWT
│       ├── service/         # 同步、浏览、缩略图缓存、用户等
│       └── util/            # AlistClient、PathUtils、MediaTypes、同步计划
├── frontend/                # Vue 3 + Vant 4
│   └── src/
│       ├── api/
│       ├── router/
│       ├── stores/
│       ├── styles/
│       ├── utils/           # 文件类型等工具
│       └── views/           # 首页、浏览、查看器、管理端
├── docker/
│   ├── Dockerfile
│   └── docker-compose.yml
└── README.md
```

## 主要数据概念

| 概念 | 说明 |
|------|------|
| Alist 引擎 | 连接配置（URL、Token） |
| 浏览目录 | 管理员配置的可浏览根路径 + **独立同步计划** |
| user_directory | 用户 ↔ 浏览目录 权限 |
| file_directory / dir_file | 同步下来的目录树与文件缓存 |
| 缩略图缓存 | 列表小图；可选 proxy 原图缓存 |

## API 概览

| 模块 | 前缀 | 说明 |
|------|------|------|
| 认证 | `/api/auth` | 登录、当前用户、改密 |
| 引擎 | `/api/engines` | 引擎 CRUD / 测试连接 |
| 浏览目录 | `/api/directories` | CRUD；`POST /{id}/sync` 立即同步 |
| 用户 | `/api/users` | 用户与目录权限（管理员） |
| 文件 | `/api/files` | list / search / proxy / stream / thumbnail / sync |

图片、视频流接口供 `<img>` / `<video>` 使用，鉴权策略见 Security 配置（proxy/stream/thumbnail 对标签场景开放访问）。

## 技术栈

- 后端：Java 17、Spring Boot 3.2、Spring Security、JPA、SQLite、JWT
- 前端：Vue 3、Vite、Vant 4、Pinia、Vue Router、Axios
- 图片处理：Thumbnailator
- 部署：Docker 单容器（后端托管前端静态资源）

## 说明与限制

- 仅只读浏览，不提供上传/删除远端文件
- 浏览器原生播放格式有限（如 mp4/webm 较稳；mkv/avi 等可能无法播放）
- 同步为全量目录树同步（按浏览根），大目录请合理设置同步间隔
- 生产环境请修改 `XBROWSE_JWT_SECRET` 与管理员密码

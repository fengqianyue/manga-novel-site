# ==================== 漫画小说平台 - 前端 Dockerfile ====================
# 构建: docker build -t manga-frontend manga-client/
# 运行: docker-compose up -d frontend

# ---------- 阶段1: Node.js 构建 ----------
FROM node:18-alpine AS builder

WORKDIR /build

# 复制依赖文件（利用缓存层）
COPY package.json package-lock.json ./

# 安装依赖
RUN npm ci --prefer-offline --no-audit --no-fund

# 复制源码
COPY . .

# 生产构建
RUN npm run build

# ---------- 阶段2: Nginx 静态服务 ----------
FROM nginx:alpine

# 用自定义配置替换默认 nginx.conf
COPY nginx.conf /etc/nginx/nginx.conf

# 从构建阶段复制打包产物
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s \
  CMD wget -qO- http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]

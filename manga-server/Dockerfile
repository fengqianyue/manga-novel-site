# ==================== 漫画小说平台 - 后端 Dockerfile ====================
# 构建: docker build -f manga-server/Dockerfile -t manga-backend .
# 运行: docker-compose up -d backend

# ---------- 阶段1: Maven 编译 ----------
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 先复制父 POM（利用 Docker 缓存层，依赖不变时无需重复下载）
COPY pom.xml .
COPY manga-server/pom.xml manga-server/

# 下载依赖（利用缓存：src 未变更时不重复下载）
RUN mvn dependency:go-offline -pl manga-server -B -q || true

# 复制全部源码
COPY manga-server/src manga-server/src/

# 编译打包（跳过测试，加快构建）
RUN mvn package -pl manga-server -DskipTests -B -q

# ---------- 阶段2: JRE 运行 ----------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /build/manga-server/target/*.jar app.jar

# 创建上传目录
RUN mkdir -p /app/uploads

EXPOSE 8088

# JVM 优化参数:
#   -Xms256m -Xmx512m  初始/最大堆内存（Docker 环境不宜过大）
#   -XX:+UseG1GC        使用 G1 垃圾回收器
#   --enable-preview    如果用了 preview 特性可开启
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]

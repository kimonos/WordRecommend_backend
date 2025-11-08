# 多階段建置 - 建置階段
FROM eclipse-temurin:22-jdk AS build
WORKDIR /app

# 安裝 Maven
RUN apt-get update && \
    apt-get install -y maven && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 複製 pom.xml 並下載依賴
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# 複製整個 src 目錄
COPY src ./src

# 建置應用程式
RUN mvn clean package -DskipTests

# 執行階段 - 使用較小的 JRE 映像
FROM eclipse-temurin:22-jre
WORKDIR /app

# 複製建置好的 JAR 檔案
COPY --from=build /app/target/*.jar app.jar

# 暴露應用程式端口
EXPOSE 8080

# 設定 JVM 參數
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# 啟動應用程式
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
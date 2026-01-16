# 代码沙箱（Sandbox）

本项目提供一个轻量级 Java 代码沙箱服务，支持在 Docker 容器中编译并运行用户代码，按用例输入执行并返回输出结果，适用于在线判题或代码执行类场景。

## 主要特性

- **标准输入模式**：用户代码通过 stdin 读取输入
- **多用例执行**：`inputList` 支持多组输入，逐条执行并收集输出
- **隔离运行**：Docker 容器执行，禁网、只读根文件系统
- **资源限制**：内存上限与超时控制
- **临时目录清理**：执行结束删除 `tmpCode` 目录

## 技术栈与环境

- JDK 8
- Maven
- Spring Boot
- Docker（远程 API 需开启，默认 `tcp://localhost:2375`）

## 目录结构

- `src/main/java/com/bin/sandbox`：核心沙箱逻辑
- `src/main/java/com/bin/sandbox/controller`：对外 API
- `src/main/resources/testCode`：测试代码样例
- `tmpCode`：运行时生成的临时目录（执行结束清理）

## 快速开始

1. 确保本机 Docker 已开启远程 API（默认 `tcp://localhost:2375`）。
2. 启动服务：

```bash
mvn -q -DskipTests spring-boot:run
```

默认端口：`8099`（见 `src/main/resources/application.yml`）。

## 接口说明

### 健康检查

```
GET /health
```

返回：`ok`

### 执行代码

```
POST /executeCode
Header: auth: secretKey
```

请求体示例：

```json
{
  "language": "java",
  "code": "import java.util.*; public class Main { public static void main(String[] args) { Scanner sc = new Scanner(System.in); int a = sc.nextInt(); int b = sc.nextInt(); System.out.println(a + b); } }",
  "inputList": ["1 2\n", "3 4\n"]
}
```

响应字段说明：

- `outputList`：每条输入对应的标准输出
- `message`：错误信息（若有）
- `status`：状态码（约定：`1` 成功，`3` 运行期错误/标准错误输出，`2` 系统异常）
- `judgeInfo`：耗时/内存等指标

## 运行与判题流程

1. 将代码写入 `tmpCode/<uuid>/Main.java`
2. 编译得到 `Main.class`
3. Docker 容器内运行，按 `inputList` 逐条写入 stdin
4. 收集 stdout/stderr，聚合到响应
5. 删除 `tmpCode` 临时目录

## 资源与限制

- 单次运行超时：5 秒
- Docker 内存限制：100MB
- RootFS 只读、网络禁用

## 常见问题

### 1) `Does not support hijacking` / `Socket Closed`

这是 Docker 客户端与服务端连接异常导致的拉取/执行失败：

- 确认 Docker 远程 API 可用（`tcp://localhost:2375`）
- 网络不稳定时建议先手动拉取镜像：`docker pull openjdk:8u342-jre-slim-buster`
- 尝试重启 Docker 服务


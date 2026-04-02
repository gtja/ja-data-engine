# JA-UAV-Data-Engine

基于 `/Users/wangyongzhen/ja-control-center` 的结构，整理出的 JDK 11 MQTT 项目底座。这里保留 MQTT 主架构和数据层基础结构，但不预置任何具体业务表、示例 SQL、业务实体或业务 Mapper。

## 模块结构

- `engine-api`：启动入口、Spring Boot 配置、MQTT 连接初始化
- `engine-biz`：MQTT 回调、消息分发、topic handler
- `engine-dao`：数据层包位、Mapper 扫描入口、后续实体/Mapper 放置位置
- `engine-common`：数据源常量、MQTT 公共模型

## 核心设计

- MQTT 主链路：`yaml mqtt.topic -> config 建连并订阅 -> callback 收消息 -> dispatcher 组装 headers + receiver -> 分发到 handler`
- 数据层主架构：
  - `MySQL + Doris` 双数据源配置已就位
  - `engine-dao` 模块已保留
  - MyBatis-Plus 和 Mapper 扫描已就位
  - 但不预置任何业务表/Mapper/实体
- 中间件基础能力：
  - `Redis` 已配置，可直接注入 `StringRedisTemplate` 或 `RedisTemplate<String, Object>`
  - `Kafka` 已配置，可直接注入 `KafkaTemplate<String, String>` 或使用 `@KafkaListener`
- 新增业务时只需要：
  - 加一个 handler 类
  - 在 handler 里声明要绑定的 topic
  - 如果消息体复杂，再补自己的 DTO

## 快速开始

1. 修改 [application-dev.yml](/Users/wangyongzhen/JA-UAV-Data-Engine/engine-api/src/main/resources/application-dev.yml) 中的 MQTT、MySQL、Doris 地址。
2. 修改 `Redis` 和 `Kafka` 地址。
3. 如果你要建表，执行 [project-schema.sql](/Users/wangyongzhen/JA-UAV-Data-Engine/sql/project-schema.sql) 或按你的业务继续扩展。
4. 启动 `engine-api` 模块中的 `JaUavDataEngineApplication`。

## 扩展方式

新增一个 topic 处理器的最小步骤：

1. 在 `engine-biz/.../mqtt/handler` 下新增一个 `@Component`
2. 实现 `MqttTopicHandler`
3. 在 [application-dev.yml](/Users/wangyongzhen/JA-UAV-Data-Engine/engine-api/src/main/resources/application-dev.yml) 的 `mqtt.topic` 里加上这个 topic
4. 返回你的 `topic()`，例如 `sys/+/+/thing/event/property/post`
5. 在 `handle(MqttMessageHeaders headers, CommonTopicReceiver<Map<String, Object>> receiver)` 里写你的业务逻辑

示例：

```java
@Component
public class DemoHandler implements MqttTopicHandler {

    @Override
    public String topic() {
        return "sys/+/+/thing/event/property/post";
    }

    @Override
    public void handle(MqttMessageHeaders headers, CommonTopicReceiver<Map<String, Object>> receiver) {
        String productKey = headers.getProductKey();
        String deviceId = headers.getDeviceId();
        Map<String, Object> body = receiver.getData();
    }
}
```

项目里保留了一个最小样例 handler：属性上报 handler。

## 数据层扩展方式

1. 在 `engine-dao/.../entity` 下新增你的实体类
2. 在 `engine-dao/.../mapper` 下新增你的 Mapper 接口
3. 默认走 MySQL；需要 Doris 时，在你的 service 或 mapper 调用侧加 `@DS("doris")`

## 直接注入

- Redis:
  - `StringRedisTemplate`
  - `RedisTemplate<String, Object>`
- Kafka:
  - `KafkaTemplate<String, String>`
  - `@KafkaListener(topics = "...")`
- MQTT:
  - `MqttMessageHeaders`
  - `CommonTopicReceiver<T>`

## 参考分析

- 分析文档见 [ja-control-center-analysis.md](/Users/wangyongzhen/JA-UAV-Data-Engine/docs/ja-control-center-analysis.md)
- 服务说明与线程模型见 [ja-uav-data-engine-service-design.md](/Users/wangyongzhen/JA-UAV-Data-Engine/docs/ja-uav-data-engine-service-design.md)

## 打包发布

项目只保留一套打包入口，目录在：

- `build/`

文件说明：

- [Dockerfile](/Users/wangyongzhen/JA-UAV-Data-Engine/build/Dockerfile)
- [run.sh](/Users/wangyongzhen/JA-UAV-Data-Engine/build/run.sh)
- [build-amd.sh](/Users/wangyongzhen/JA-UAV-Data-Engine/build/build-amd.sh)
- [build-arm.sh](/Users/wangyongzhen/JA-UAV-Data-Engine/build/build-arm.sh)
- [build-gongwang.sh](/Users/wangyongzhen/JA-UAV-Data-Engine/build/build-gongwang.sh)
- [build-gongwang-arm.sh](/Users/wangyongzhen/JA-UAV-Data-Engine/build/build-gongwang-arm.sh)

打包前先编译：

```bash
mvn -pl engine-api -am clean package -DskipTests
```

然后进入 `build` 目录执行对应脚本，例如：

```bash
cd /Users/wangyongzhen/JA-UAV-Data-Engine/build
./build-amd.sh
```

## MQTT 压测

项目根目录提供了压测脚本：

- `scripts/mqtt-load-test.sh`

默认会向：

- `sys/{productKey}/{deviceId}/thing/event/property/post`

持续发送模拟属性上报，用来压当前项目的 MQTT 入站链路。

示例：

```bash
./scripts/mqtt-load-test.sh \
  --device-count 1000 \
  --period-ms 1000 \
  --duration-seconds 300 \
  --product-key 00000000002
```

常用参数：

- `--broker`：MQTT broker 地址
- `--username`
- `--password`
- `--product-key`
- `--device-count`
- `--period-ms`
- `--duration-seconds`
- `--qos`
- `--sortie-interval-seconds`
- `--distance-per-tick`

查看全部参数：

```bash
./scripts/mqtt-load-test.sh --help
```

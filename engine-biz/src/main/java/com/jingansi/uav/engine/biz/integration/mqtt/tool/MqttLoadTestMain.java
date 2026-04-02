package com.jingansi.uav.engine.biz.integration.mqtt.tool;

import com.jingansi.uav.engine.common.constant.ProductKeyConstants;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MQTT 压测发布器。
 *
 * <p>默认用途：
 * - 模拟大量设备同时上报 `sys/productKey/deviceId/thing/event/property/post`
 * - 每台设备按固定周期上报累计飞行架次、累计时长、累计距离
 * - 用于验证当前服务的 MQTT 入站、分片队列和业务处理吞吐能力
 */
public final class MqttLoadTestMain {

    private static final String TOPIC_SUFFIX = "thing/event/property/post";
    private static final DateTimeFormatter REPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MqttLoadTestMain() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        if (config.help) {
            printUsage();
            return;
        }

        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicLong submittedCount = new AtomicLong(0);
        AtomicLong deliveredCount = new AtomicLong(0);
        AtomicLong failedCount = new AtomicLong(0);

        MqttAsyncClient client = new MqttAsyncClient(config.broker, config.clientId, new MemoryPersistence());
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                System.out.println("[mqtt-load-test] connected, reconnect=" + reconnect + ", serverURI=" + serverURI);
            }

            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("[mqtt-load-test] connection lost: " + (cause == null ? "unknown" : cause.getMessage()));
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                // 发布压测不消费消息。
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                deliveredCount.incrementAndGet();
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopped.set(true);
            try {
                if (client.isConnected()) {
                    client.disconnect().waitForCompletion(5000);
                }
            } catch (Exception ignored) {
            }
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }, "mqtt-load-test-shutdown"));

        connect(client, config);

        List<DeviceState> devices = createDevices(config);
        int sortieIntervalTicks = Math.max(1, config.sortieIntervalSeconds * 1000 / config.periodMs);
        long startMillis = System.currentTimeMillis();
        long endMillis = startMillis + TimeUnit.SECONDS.toMillis(config.durationSeconds);
        long nextLogMillis = startMillis + TimeUnit.SECONDS.toMillis(5);
        long tickNo = 0L;
        long nextTickMillis = startMillis;

        printConfig(config, devices.size());
        while (!stopped.get() && System.currentTimeMillis() < endMillis) {
            long now = System.currentTimeMillis();
            if (now < nextTickMillis) {
                TimeUnit.MILLISECONDS.sleep(nextTickMillis - now);
            }
            long publishTimestamp = System.currentTimeMillis();
            boolean increaseSortie = tickNo > 0 && tickNo % sortieIntervalTicks == 0;
            for (DeviceState device : devices) {
                publishOne(client, config, device, publishTimestamp, increaseSortie, submittedCount, failedCount);
            }
            tickNo++;
            nextTickMillis += config.periodMs;

            long currentMillis = System.currentTimeMillis();
            if (currentMillis >= nextLogMillis) {
                logProgress(startMillis, submittedCount.get(), deliveredCount.get(), failedCount.get());
                nextLogMillis = currentMillis + TimeUnit.SECONDS.toMillis(5);
            }
        }

        waitForOutstandingDeliveries(submittedCount, deliveredCount, failedCount, stopped);
        logProgress(startMillis, submittedCount.get(), deliveredCount.get(), failedCount.get());
        if (client.isConnected()) {
            client.disconnect().waitForCompletion(5000);
        }
        client.close();
    }

    /**
     * 建立 MQTT 连接。
     */
    private static void connect(MqttAsyncClient client, Config config) throws Exception {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{config.broker});
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setKeepAliveInterval(config.keepAliveSeconds);
        options.setConnectionTimeout(config.timeoutSeconds);
        options.setMaxInflight(config.maxInflight);
        if (!config.username.isBlank()) {
            options.setUserName(config.username);
        }
        if (!config.password.isBlank()) {
            options.setPassword(config.password.toCharArray());
        }
        client.connect(options).waitForCompletion(10000);
    }

    /**
     * 初始化所有模拟设备。
     */
    private static List<DeviceState> createDevices(Config config) {
        List<DeviceState> devices = new ArrayList<>(config.deviceCount);
        for (int index = 1; index <= config.deviceCount; index++) {
            String deviceId = config.devicePrefix + String.format(Locale.ROOT, "%06d", index);
            String topic = "sys/" + config.productKey + "/" + deviceId + "/" + TOPIC_SUFFIX;
            devices.add(new DeviceState(deviceId, topic, config.startSorties, 0L, BigDecimal.ZERO));
        }
        return devices;
    }

    /**
     * 发送单台设备的一条属性上报消息。
     */
    private static void publishOne(MqttAsyncClient client,
                                   Config config,
                                   DeviceState device,
                                   long publishTimestamp,
                                   boolean increaseSortie,
                                   AtomicLong submittedCount,
                                   AtomicLong failedCount) {
        if (increaseSortie) {
            device.totalFlightSorties++;
        }
        device.totalFlightTime += config.timeIncrementSeconds;
        device.totalFlightDistance = device.totalFlightDistance.add(config.distancePerTick);
        String payload = buildPayload(config, device, publishTimestamp);
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(config.qos);
        try {
            client.publish(device.topic, message);
            submittedCount.incrementAndGet();
        } catch (Exception ex) {
            failedCount.incrementAndGet();
            System.out.println("[mqtt-load-test] publish failed, deviceId=" + device.deviceId + ", error=" + ex.getMessage());
        }
    }

    /**
     * 生成和当前业务兼容的属性上报消息体。
     */
    private static String buildPayload(Config config, DeviceState device, long publishTimestamp) {
        return new StringBuilder(256)
                .append("{\"version\":\"1.0\"")
                .append(",\"timestamp\":").append(publishTimestamp)
                .append(",\"productKey\":\"").append(config.productKey).append("\"")
                .append(",\"deviceId\":\"").append(device.deviceId).append("\"")
                .append(",\"message\":\"load-test ")
                .append(LocalDateTime.now().format(REPORT_TIME_FORMATTER))
                .append("\"")
                .append(",\"data\":{")
                .append("\"totalFlightSorties\":").append(device.totalFlightSorties).append(',')
                .append("\"totalFlightTime\":").append(device.totalFlightTime).append(',')
                .append("\"totalFlightDistance\":").append(device.totalFlightDistance.toPlainString())
                .append("}}")
                .toString();
    }

    /**
     * 等待一小段时间，让 QoS 回执尽量完成。
     */
    private static void waitForOutstandingDeliveries(AtomicLong submittedCount,
                                                     AtomicLong deliveredCount,
                                                     AtomicLong failedCount,
                                                     AtomicBoolean stopped) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (!stopped.get() && System.currentTimeMillis() < deadline) {
            long submitted = submittedCount.get();
            long completed = deliveredCount.get() + failedCount.get();
            if (completed >= submitted) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(200);
        }
    }

    /**
     * 打印当前压测进度。
     */
    private static void logProgress(long startMillis, long submitted, long delivered, long failed) {
        long elapsedMillis = Math.max(1L, System.currentTimeMillis() - startMillis);
        double submittedQps = submitted * 1000D / elapsedMillis;
        double deliveredQps = delivered * 1000D / elapsedMillis;
        DecimalFormat format = new DecimalFormat("0.00");
        System.out.println("[mqtt-load-test] elapsedMs=" + elapsedMillis
                + ", submitted=" + submitted
                + ", delivered=" + delivered
                + ", failed=" + failed
                + ", submittedQps=" + format.format(submittedQps)
                + ", deliveredQps=" + format.format(deliveredQps));
    }

    /**
     * 打印当前压测配置。
     */
    private static void printConfig(Config config, int deviceCount) {
        System.out.println("[mqtt-load-test] start with config:"
                + " broker=" + config.broker
                + ", productKey=" + config.productKey
                + ", deviceCount=" + deviceCount
                + ", periodMs=" + config.periodMs
                + ", durationSeconds=" + config.durationSeconds
                + ", qos=" + config.qos
                + ", sortieIntervalSeconds=" + config.sortieIntervalSeconds
                + ", distancePerTick=" + config.distancePerTick.toPlainString());
    }

    private static void printUsage() {
        System.out.println("MQTT 压测脚本参数：");
        System.out.println("  --broker tcp://127.0.0.1:1883");
        System.out.println("  --username emqx_test");
        System.out.println("  --password emqx_test_password");
        System.out.println("  --product-key 00000000002");
        System.out.println("  --device-prefix stress-device-");
        System.out.println("  --device-count 1000");
        System.out.println("  --period-ms 1000");
        System.out.println("  --duration-seconds 60");
        System.out.println("  --qos 1");
        System.out.println("  --sortie-interval-seconds 300");
        System.out.println("  --distance-per-tick 15");
        System.out.println("  --client-id mqtt-load-test-001");
        System.out.println("  --max-inflight 20000");
        System.out.println("  --help");
    }

    /**
     * 命令行配置。
     */
    private static final class Config {

        private boolean help;
        private String broker = "tcp://47.111.155.82:31886";
        private String username = "emqx_test";
        private String password = "emqx_test_password";
        private String productKey = ProductKeyConstants.FLIGHT_STATISTICS_PRODUCT_KEY;
        private String devicePrefix = "stress-device-";
        private int deviceCount = 1000;
        private int periodMs = 1000;
        private int durationSeconds = 60;
        private int qos = 1;
        private int sortieIntervalSeconds = 300;
        private BigDecimal distancePerTick = new BigDecimal("15");
        private long timeIncrementSeconds = 1L;
        private long startSorties = 1L;
        private String clientId = "mqtt-load-test-" + System.currentTimeMillis();
        private int keepAliveSeconds = 60;
        private int timeoutSeconds = 30;
        private int maxInflight = 20000;

        private static Config parse(String[] args) {
            Config config = new Config();
            Map<String, String> arguments = CommandLine.parse(args);
            if (arguments.containsKey("help")) {
                config.help = true;
                return config;
            }
            if (arguments.containsKey("broker")) {
                config.broker = arguments.get("broker");
            }
            if (arguments.containsKey("username")) {
                config.username = arguments.get("username");
            }
            if (arguments.containsKey("password")) {
                config.password = arguments.get("password");
            }
            if (arguments.containsKey("product-key")) {
                config.productKey = arguments.get("product-key");
            }
            if (arguments.containsKey("device-prefix")) {
                config.devicePrefix = arguments.get("device-prefix");
            }
            if (arguments.containsKey("device-count")) {
                config.deviceCount = parsePositiveInt(arguments.get("device-count"), "device-count");
            }
            if (arguments.containsKey("period-ms")) {
                config.periodMs = parsePositiveInt(arguments.get("period-ms"), "period-ms");
            }
            if (arguments.containsKey("duration-seconds")) {
                config.durationSeconds = parsePositiveInt(arguments.get("duration-seconds"), "duration-seconds");
            }
            if (arguments.containsKey("qos")) {
                config.qos = parsePositiveInt(arguments.get("qos"), "qos");
            }
            if (arguments.containsKey("sortie-interval-seconds")) {
                config.sortieIntervalSeconds = parsePositiveInt(arguments.get("sortie-interval-seconds"), "sortie-interval-seconds");
            }
            if (arguments.containsKey("distance-per-tick")) {
                config.distancePerTick = new BigDecimal(arguments.get("distance-per-tick"));
            }
            if (arguments.containsKey("start-sorties")) {
                config.startSorties = parsePositiveLong(arguments.get("start-sorties"), "start-sorties");
            }
            if (arguments.containsKey("client-id")) {
                config.clientId = arguments.get("client-id");
            }
            if (arguments.containsKey("keep-alive-seconds")) {
                config.keepAliveSeconds = parsePositiveInt(arguments.get("keep-alive-seconds"), "keep-alive-seconds");
            }
            if (arguments.containsKey("timeout-seconds")) {
                config.timeoutSeconds = parsePositiveInt(arguments.get("timeout-seconds"), "timeout-seconds");
            }
            if (arguments.containsKey("max-inflight")) {
                config.maxInflight = parsePositiveInt(arguments.get("max-inflight"), "max-inflight");
            }
            config.timeIncrementSeconds = Math.max(1L, config.periodMs / 1000L);
            return config;
        }

        private static int parsePositiveInt(String value, String name) {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException(name + " must be greater than 0");
            }
            return parsed;
        }

        private static long parsePositiveLong(String value, String name) {
            long parsed = Long.parseLong(value);
            if (parsed < 1L) {
                throw new IllegalArgumentException(name + " must be greater than 0");
            }
            return parsed;
        }
    }

    /**
     * 单台模拟设备的累计状态。
     */
    private static final class DeviceState {

        private final String deviceId;
        private final String topic;
        private long totalFlightSorties;
        private long totalFlightTime;
        private BigDecimal totalFlightDistance;

        private DeviceState(String deviceId,
                            String topic,
                            long totalFlightSorties,
                            long totalFlightTime,
                            BigDecimal totalFlightDistance) {
            this.deviceId = deviceId;
            this.topic = topic;
            this.totalFlightSorties = totalFlightSorties;
            this.totalFlightTime = totalFlightTime;
            this.totalFlightDistance = totalFlightDistance;
        }
    }

    /**
     * 极简命令行解析。
     */
    private static final class CommandLine {

        private CommandLine() {
        }

        private static Map<String, String> parse(String[] args) {
            java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                if (!arg.startsWith("--")) {
                    throw new IllegalArgumentException("unsupported argument: " + arg);
                }
                String key = arg.substring(2);
                if ("help".equals(key)) {
                    result.put("help", "true");
                    continue;
                }
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException("missing value for argument: " + arg);
                }
                result.put(key, args[++index]);
            }
            return result;
        }
    }
}

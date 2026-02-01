# Flink Order Processing System / Flink 订单处理系统

## Overview / 概述

This project implements a comprehensive real-time order processing pipeline using the Apache Big Data stack. It is designed to handle high-throughput order data, perform real-time analytics, and store results in multiple storage systems for different use cases.

本项目基于 Apache 大数据生态栈实现了一个完整的实时订单处理流程。专为处理高吞吐量的订单数据、执行实时分析并将结果存储在不同的存储系统中而设计。

The system integrates the following components:
系统集成了以下组件：
*   **Apache Flume**: For log collection and data ingestion. / 用于日志收集和数据采集。
*   **Apache Kafka**: As a distributed message queue for buffering and decoupling. / 作为分布式消息队列进行缓冲和解耦。
*   **Apache Flink**: For stateful stream processing and windowed aggregations. / 用于有状态流处理和窗口聚合。
*   **Redis**: For low-latency storage of real-time dashboards (Key-Value). / 用于实时仪表盘的低延迟存储 (KV)。
*   **HBase**: For historical data storage and detailed query support (NoSQL). / 用于历史数据存储和详细查询支持 (NoSQL)。

---

## Data Flow & Architecture / 数据流与架构

1.  **Data Ingestion (Flume)**:
    *   Listens on port `10050` (Netcat source).
    *   Uses a `Replicating Channel Selector` to duplicate events.
    *   **Sink 1**: Writes raw events to HDFS `/user/test/flumebackup` for archival.
    *   **Sink 2**: Pushes events to Kafka topic `order` for real-time processing.

    **数据采集 (Flume)**:
    *   监听 `10050` 端口 (Netcat 源)。
    *   使用 `Replicating Channel Selector` 复制事件。
    *   **Sink 1**: 将原始事件写入 HDFS `/user/test/flumebackup` 进行归档。
    *   **Sink 2**: 将事件推送到 Kafka 主题 `order` 进行实时处理。

2.  **Stream Processing (Flink)**:
    *   **Source**: Consumes JSON strings from Kafka `order` topic.
    *   **ETL**: Parses JSON. Splits stream into `OrderInfo` (Main Stream) and `OrderDetail` (Side Output).
    *   **Watermark**: `Max(create_time, operate_time)` with 5s lateness tolerance.
    *   **Task 1 (Redis)**: Filters invalid orders (Status 1003/1005/1006). Aggregates total count to Redis key `totalcount`.
    *   **Task 2 (Redis)**: Calculates `sku_num * sku_price` from Side Output. Maintains global Top 3 items by revenue in Redis key `top3itemconsumption`.
    *   **Task 3 (HBase)**: Joins valid `OrderInfo` and `OrderDetail` using a 10s Tumbling Window. Aggregates `total_amount` and `sku_count`. Writes to HBase table `shtd_result:orderpositiveaggr`.

    **流处理 (Flink)**:
    *   **Source**: 消费 Kafka `order` 主题的 JSON 字符串。
    *   **ETL**: 解析 JSON。将流拆分为 `OrderInfo` (主流) 和 `OrderDetail` (侧输出流)。
    *   **水位线**: `Max(create_time, operate_time)`，允许 5s 延迟。
    *   **任务 1 (Redis)**: 过滤无效订单 (状态 1003/1005/1006)。聚合总数至 Redis 键 `totalcount`。
    *   **任务 2 (Redis)**: 从侧输出流计算 `sku_num * sku_price`。在 Redis 键 `top3itemconsumption` 中维护全球销售额前 3 的商品。
    *   **任务 3 (HBase)**: 使用 10s 滚动窗口连接有效的 `OrderInfo` 和 `OrderDetail`。聚合 `total_amount` 和 `sku_count`。写入 HBase 表 `shtd_result:orderpositiveaggr`。

---

## Data Formats / 数据格式

### Input JSON Examples / 输入 JSON 示例

**Order Info (订单信息):**
```json
{
  "id": 1,
  "create_time": "2023-10-27 10:00:00",
  "operate_time": "2023-10-27 10:00:05",
  "order_status": "1001",
  "final_total_amount": 100.50
}
```

**Order Detail (订单详情):**
```json
{
  "id": 101,
  "order_id": 1,
  "sku_id": 2001,
  "sku_num": 2,
  "sku_price": 50.25,
  "create_time": "2023-10-27 10:00:00"
}
```

---

## Prerequisites / 环境要求

*   **OS**: CentOS 7
*   **Java**: JDK 1.8
*   **Scala**: 2.11.12
*   **Flink**: 1.14.0
*   **Kafka**: 2.4.1 (Scala 2.11)
*   **HBase**: 2.2.3
*   **Redis**: 3.x+

## Getting Started / 快速开始

Please refer to [GUIDE.md](GUIDE.md) for detailed step-by-step instructions on compilation, deployment, and result verification.

请参阅 [GUIDE.md](GUIDE.md) 获取有关编译、部署和结果验证的详细分步说明。

---

### Project Structure / 项目结构

*   `src/main/scala`:
    *   `com.example.model`: Case classes for JSON parsing / 用于 JSON 解析的样例类
    *   `com.example.task`: Main Flink Job logic / Flink 主程序逻辑
*   `flume-conf.properties`: Flume agent configuration / Flume Agent 配置
*   `pom.xml`: Maven build configuration (Dependencies & Plugins) / Maven 构建配置 (依赖与插件)

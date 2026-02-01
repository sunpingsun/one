# Flink Order Processing System / Flink 订单处理系统

## Overview / 概述

This project implements a real-time order processing pipeline using Apache Flink, Kafka, Flume, Redis, and HBase. It is designed to handle real-time data ingestion, filtration, aggregation, and storage for a mall order system.

本项目基于 Apache Flink、Kafka、Flume、Redis 和 HBase 实现了一个实时订单处理流程。旨在处理商城订单系统的实时数据采集、过滤、聚合与存储。

## Features / 功能特性

*   **Real-time Ingestion**: Collects socket data via Flume and sinks to Kafka and HDFS.
    **实时采集**: 通过 Flume 采集 Socket 数据并下沉到 Kafka 和 HDFS。
*   **Order Statistics**: Calculates total valid order counts in real-time.
    **订单统计**: 实时计算有效订单总数。
*   **Top Items**: Identifies top 3 items by consumption amount.
    **热门商品**: 统计消费额前 3 的商品。
*   **Data Aggregation**: Joins order info and details streams to aggregate order totals and item counts into HBase.
    **数据聚合**: 双流 Join 订单信息与详情流，聚合订单总额与商品数并存入 HBase。

## Prerequisites / 环境要求

*   Flink 1.14.0
*   Scala 2.11.12
*   Kafka 2.4.1
*   Redis 6.x
*   HBase 2.2.3
*   Java 8

## Getting Started / 快速开始

Please refer to [GUIDE.md](GUIDE.md) for detailed instructions on how to build, deploy, and verify the application.

请参阅 [GUIDE.md](GUIDE.md) 获取有关如何构建、部署和验证应用程序的详细说明。

---

### Project Structure / 项目结构

*   `src/main/scala`: Scala source code / Scala 源代码
    *   `com.example.model`: Data models / 数据模型
    *   `com.example.task`: Flink job implementation / Flink 任务实现
*   `flume-conf.properties`: Flume agent configuration / Flume Agent 配置文件
*   `pom.xml`: Maven build configuration / Maven 构建配置

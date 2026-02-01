# Flink Order Processing Task Guide / Flink 订单处理任务指南

This guide details how to build, deploy, and verify the Flink Order Processing application.
本指南详细说明了如何构建、部署和验证 Flink 订单处理应用程序。

---

## 1. Build Project / 构建项目

Compile the Scala project to generate the Fat Jar.
编译 Scala 项目以生成 Fat Jar。

```bash
mvn clean package
```

The jar will be located at / Jar 包将位于:
`target/flink-order-processing-1.0-SNAPSHOT.jar`

---

## 2. Start Flume Agent (Task 1) / 启动 Flume Agent (任务 1)

Start the Flume agent to consume from port 10050 and sink to Kafka and HDFS.
启动 Flume Agent 以消费 10050 端口的数据，并将其发送到 Kafka 和 HDFS。

```bash
# Ensure you are in the directory containing flume-conf.properties
# 确保你位于包含 flume-conf.properties 的目录下
flume-ng agent \
  --name a1 \
  --conf conf \
  --conf-file flume-conf.properties \
  --property flume.root.logger=INFO,console
```

### Verification (Task 1) / 验证 (任务 1)

Check the backup file in HDFS.
检查 HDFS 中的备份文件。

```bash
# List files / 列出文件
hdfs dfs -ls /user/test/flumebackup

# View content of the first file (replace <filename> with actual name)
# 查看第一个文件的内容 (请将 <filename> 替换为实际文件名)
hdfs dfs -cat /user/test/flumebackup/<filename> | head -n 2
```

*Screenshot Instructions / 截图说明:*
Paste the command and the result (first 2 lines) into the release document.
将查看备份目录下的第一个文件的前2条数据的命令与结果截图粘贴至结果文档中。

**Troubleshooting:**
*   **Error**: `netcat` not found. **Solution**: Ensure `nc` is installed (`yum install nc`).
*   **Error**: Port in use. **Solution**: `netstat -tulpn | grep 10050` and kill the process.

---

## 3. Submit Flink Job (Task 2) / 提交 Flink 作业 (任务 2)

Submit the Flink job to the YARN cluster in Per-Job mode.
以 Per-Job 模式将 Flink 作业提交到 YARN 集群。

```bash
# Replace /path/to/jar with the actual path
# 请将 /path/to/jar 替换为实际路径
flink run -m yarn-cluster \
  -yjm 1024 -ytm 1024 \
  -c com.example.task.OrderStreamTask \
  ./target/flink-order-processing-1.0-SNAPSHOT.jar
```

**Verify Job Status / 验证作业状态:**
```bash
flink list -r
```
You should see a job named `OrderStreamTask` in `RUNNING` state.
你应该看到名为 `OrderStreamTask` 的作业处于 `RUNNING` 状态。

---

## 4. Verify Results & Screenshots / 验证结果与截图

### Task 2.1: Real-time Order Count (Redis) / 实时订单统计 (Redis)

Connect to Redis and check `totalcount`.
连接 Redis 并检查 `totalcount`。

```bash
redis-cli -h 192.168.12.41
```

Inside Redis CLI / 在 Redis CLI 中:
```redis
get totalcount
```

*Screenshot Instructions / 截图说明:*
Take a screenshot. Wait 1 minute. Take another screenshot. (Paste both screenshots into the document).
截图。等待 1 分钟。再次截图。(需两次截图，第一次截图和第二次截图间隔1分钟以上，第一次截图放前面，第二次截图放后面)。

**Expected Output / 预期输出:** An integer (e.g., `100`).

### Task 2.2: Top 3 Item Consumption (Redis) / 消费额前 3 商品 (Redis)

Inside Redis CLI / 在 Redis CLI 中:
```redis
get top3itemconsumption
```

*Screenshot Instructions / 截图说明:*
Take a screenshot. Wait 1 minute. Take another screenshot. (Paste both screenshots into the document).
截图。等待 1 分钟。再次截图。(需两次截图，第一次截图和第二次截图间隔1分钟以上，第一次截图放前面，第二次截图放后面)。

**Example Output / 示例输出:**
`[1:10020.2,42:4540.0,12:540]`

**Note / 注意:**
The values are formatted to avoid scientific notation.
数值已格式化，避免使用科学计数法。

### Task 2.3: HBase Aggregation / HBase 聚合统计

Connect to HBase Shell.
连接到 HBase Shell。

```bash
hbase shell
```

Inside HBase Shell, query the table:
在 HBase Shell 中查询表：

```hbase
scan 'shtd_result:orderpositiveaggr', {LIMIT => 5, COLUMNS => ['info:orderprice', 'info:orderdetailcount']}
```

*Screenshot Instructions / 截图说明:*
Take a screenshot of the result.
将执行结果截图粘贴至文档中。

**Troubleshooting:**
*   **Error**: `TableNotFoundException`. **Solution**: Ensure the namespace `shtd_result` and table `orderpositiveaggr` are created before running the job.
    *   `create_namespace 'shtd_result'`
    *   `create 'shtd_result:orderpositiveaggr', 'info'`

# Flink Order Processing Task Guide

## 1. Build Project
Compile the Scala project to generate the Fat Jar.

```bash
mvn clean package
```

The jar will be located at `target/flink-order-processing-1.0-SNAPSHOT.jar`.

## 2. Start Flume Agent (Task 1)
Start the Flume agent to consume from port 10050 and sink to Kafka and HDFS.

```bash
# Ensure you are in the directory containing flume-conf.properties
flume-ng agent \
  --name a1 \
  --conf conf \
  --conf-file flume-conf.properties \
  --property flume.root.logger=INFO,console
```

**Verification (Task 1):**
Check the backup file in HDFS.
```bash
# List files
hdfs dfs -ls /user/test/flumebackup

# View content of the first file (replace <filename> with actual name)
hdfs dfs -cat /user/test/flumebackup/<filename> | head -n 2
```

## 3. Submit Flink Job (Task 2)
Submit the Flink job to the YARN cluster in Per-Job mode.

```bash
# Replace /path/to/jar with the actual path
flink run -m yarn-cluster \
  -yjm 1024 -ytm 1024 \
  -c com.example.task.OrderStreamTask \
  ./target/flink-order-processing-1.0-SNAPSHOT.jar
```

## 4. Verify Results & Screenshots

### Task 2.1: Real-time Order Count (Redis)
Connect to Redis and check `totalcount`.

```bash
redis-cli -h 192.168.12.41
```

Inside Redis CLI:
```redis
get totalcount
```
*Screenshot Instructions:* Take a screenshot. Wait 1 minute. Take another screenshot.

### Task 2.2: Top 3 Item Consumption (Redis)
Inside Redis CLI:
```redis
get top3itemconsumption
```
*Screenshot Instructions:* Take a screenshot. Wait 1 minute. Take another screenshot.

### Task 2.3: HBase Aggregation
Connect to HBase Shell.

```bash
hbase shell
```

Inside HBase Shell, query the table:
```hbase
scan 'shtd_result:orderpositiveaggr', {LIMIT => 5, COLUMNS => ['info:orderprice', 'info:orderdetailcount']}
```
*Screenshot Instructions:* Take a screenshot of the result.

package com.example.task

import com.example.model.{OrderDetail, OrderInfo}
import com.google.gson.{Gson, JsonParser}
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.RichReduceFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.streaming.api.functions.co.CoGroupFunction
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.{Collector, OutputTag}
import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}
import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory, Put}
import org.apache.hadoop.hbase.util.Bytes
import redis.clients.jedis.Jedis

import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Properties
import scala.collection.JavaConverters._

object OrderStreamTask {
  // Side Output Tag for OrderDetail
  val orderDetailTag = new OutputTag[OrderDetail]("order-detail")
  val gson = new Gson()

  def main(args: Array[String]): Unit = {
    // 1. Setup Environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1) // For simplicity and deterministic behavior in exam context

    // 2. Configure Kafka Source
    val props = new Properties()
    props.setProperty("bootstrap.servers", "192.168.12.41:9092")
    props.setProperty("group.id", "flink-order-group")
    props.setProperty("auto.offset.reset", "latest")

    val kafkaSource = new FlinkKafkaConsumer[String]("order", new SimpleStringSchema(), props)
    val rawStream = env.addSource(kafkaSource)

    // 3. Process Stream: Parse and Split
    val orderStream = rawStream.process(new ProcessFunction[String, OrderInfo] {
      override def processElement(json: String, ctx: ProcessFunction[String, OrderInfo]#Context, out: Collector[OrderInfo]): Unit = {
        try {
          val jsonObject = JsonParser.parseString(json).getAsJsonObject
          if (jsonObject.has("order_status")) {
            val info = gson.fromJson(json, classOf[OrderInfo])
            out.collect(info)
          } else if (jsonObject.has("sku_id")) {
            val detail = gson.fromJson(json, classOf[OrderDetail])
            ctx.output(orderDetailTag, detail)
          }
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }
    })

    val orderDetailStream = orderStream.getSideOutput(orderDetailTag)

    // 4. Assign Watermarks
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    def parseTime(timeStr: String): Long = {
      try { dateFormat.parse(timeStr).getTime } catch { case _: Exception => 0L }
    }

    val infoStrategy = WatermarkStrategy.forBoundedOutOfOrderness[OrderInfo](Duration.ofSeconds(5))
      .withTimestampAssigner(new SerializableTimestampAssigner[OrderInfo] {
        override def extractTimestamp(element: OrderInfo, recordTimestamp: Long): Long = {
          val ct = parseTime(element.create_time)
          val ot = if (element.operate_time != null) parseTime(element.operate_time) else 0L
          Math.max(ct, ot)
        }
      })

    val detailStrategy = WatermarkStrategy.forBoundedOutOfOrderness[OrderDetail](Duration.ofSeconds(5))
      .withTimestampAssigner(new SerializableTimestampAssigner[OrderDetail] {
        override def extractTimestamp(element: OrderDetail, recordTimestamp: Long): Long = {
          parseTime(element.create_time)
        }
      })

    val infoWithWatermark = orderStream.assignTimestampsAndWatermarks(infoStrategy)
    val detailWithWatermark = orderDetailStream.assignTimestampsAndWatermarks(detailStrategy)

    // ---------------------------------------------------------
    // Task 2.1: Real-time Order Count (Filtered)
    // ---------------------------------------------------------
    infoWithWatermark
      .filter(order => !Set("1003", "1005", "1006").contains(order.order_status))
      .map(_ => 1)
      .keyBy(_ => "global")
      .reduce(_ + _)
      .addSink(new RedisTotalCountSink())

    // ---------------------------------------------------------
    // Task 2.2: Top 3 Item Consumption
    // ---------------------------------------------------------
    detailWithWatermark
      .map(d => (d.sku_id, d.sku_num * d.sku_price))
      .keyBy(_._1)
      .reduce((a, b) => (a._1, a._2 + b._2)) // Maintain running sum per item
      .keyBy(_ => "global_top3")
      .process(new Top3ProcessFunction())
      .addSink(new RedisTop3Sink())

    // ---------------------------------------------------------
    // Task 2.3: Join and HBase Aggregation
    // ---------------------------------------------------------
    val filteredInfo = infoWithWatermark.filter(order => !Set("1003", "1005", "1006").contains(order.order_status))

    filteredInfo.coGroup(detailWithWatermark)
      .where(_.id)
      .equalTo(_.order_id)
      .window(TumblingEventTimeWindows.of(Time.seconds(10))) // 10s Window for demo/exam
      .apply(new OrderCoGroupFunction())
      .addSink(new HBaseSink())

    env.execute("OrderStreamTask")
  }

  // ---------------------------------------------------------
  // Inner Classes / Functions
  // ---------------------------------------------------------

  // Redis Sink for Total Count
  class RedisTotalCountSink extends RichSinkFunction[Int] {
    var jedis: Jedis = _

    override def open(parameters: Configuration): Unit = {
      jedis = new Jedis("192.168.12.41", 6379)
      // jedis.auth("password") // If needed
    }

    override def invoke(value: Int, context: SinkFunction.Context): Unit = {
      jedis.set("totalcount", value.toString)
    }

    override def close(): Unit = {
      if (jedis != null) jedis.close()
    }
  }

  // ProcessFunction for Top 3 Logic
  class Top3ProcessFunction extends ProcessFunction[(Long, Double), String] {
    // MapState to keep latest sum for each item
    // Key: sku_id, Value: total_amount
    // We actually need a global map. Since we keyed by "global_top3", we can use MapState.
    lazy val itemState: MapState[Long, Double] = getRuntimeContext.getMapState(
      new MapStateDescriptor[Long, Double]("item-stats", classOf[Long], classOf[Double])
    )

    override def processElement(value: (Long, Double), ctx: ProcessFunction[(Long, Double), String]#Context, out: Collector[String]): Unit = {
      // Update state
      itemState.put(value._1, value._2)

      // Calculate Top 3
      // Convert MapState to List
      val entries = itemState.entries().asScala.toList
      val sorted = entries.sortWith((a, b) => a.getValue > b.getValue).take(3)

      // Format: [id:amount, id:amount]
      // Avoid scientific notation
      val resultStr = sorted.map(e => s"${e.getKey}:${BigDecimal(e.getValue).bigDecimal.toPlainString}").mkString(",")
      out.collect(s"[$resultStr]")
    }
  }

  // Redis Sink for Top 3
  class RedisTop3Sink extends RichSinkFunction[String] {
    var jedis: Jedis = _
    override def open(parameters: Configuration): Unit = {
      jedis = new Jedis("192.168.12.41", 6379)
    }
    override def invoke(value: String, context: SinkFunction.Context): Unit = {
      jedis.set("top3itemconsumption", value)
    }
    override def close(): Unit = {
      if (jedis != null) jedis.close()
    }
  }

  // CoGroup Function for Join
  // Output: (Order ID, Total Price, Item Count)
  case class OrderAggResult(id: Long, orderPrice: Double, detailCount: Int)

  class OrderCoGroupFunction extends CoGroupFunction[OrderInfo, OrderDetail, OrderAggResult] {
    override def coGroup(first: java.lang.Iterable[OrderInfo],
                         second: java.lang.Iterable[OrderDetail],
                         out: Collector[OrderAggResult]): Unit = {

      val orders = first.asScala.toList
      val details = second.asScala.toList

      // Should be 1 OrderInfo, but multiple OrderDetails
      if (orders.nonEmpty) {
        val order = orders.head
        val totalDetailCount = details.map(_.sku_num).sum

        // Note: Task asks to "Statistical order info final_total_amount" and "Order detail sku_num"
        // It says "Combine... orderprice, orderdetailcount".
        // orderprice = order.final_total_amount
        out.collect(OrderAggResult(order.id, order.final_total_amount, totalDetailCount))
      }
    }
  }

  // HBase Sink
  class HBaseSink extends RichSinkFunction[OrderAggResult] {
    var connection: Connection = _
    val tableNameStr = "shtd_result:orderpositiveaggr"
    val family = "info"

    override def open(parameters: Configuration): Unit = {
      val conf = HBaseConfiguration.create()
      conf.set("hbase.zookeeper.quorum", "192.168.12.41")
      conf.set("hbase.zookeeper.property.clientPort", "2181")
      connection = ConnectionFactory.createConnection(conf)
    }

    override def invoke(value: OrderAggResult, context: SinkFunction.Context): Unit = {
      val table = connection.getTable(TableName.valueOf(tableNameStr))
      try {
        val put = new Put(Bytes.toBytes(value.id.toString)) // RowKey: id
        // Column: orderprice
        // Avoid scientific notation
        put.addColumn(Bytes.toBytes(family), Bytes.toBytes("orderprice"), Bytes.toBytes(BigDecimal(value.orderPrice).bigDecimal.toPlainString))
        // Column: orderdetailcount
        put.addColumn(Bytes.toBytes(family), Bytes.toBytes("orderdetailcount"), Bytes.toBytes(value.detailCount.toString))

        table.put(put)
      } finally {
        table.close()
      }
    }

    override def close(): Unit = {
      if (connection != null) connection.close()
    }
  }
}

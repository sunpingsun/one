package com.example.model

/**
 * Order Information Case Class
 * @param id Order ID
 * @param create_time Creation time (String or Timestamp)
 * @param operate_time Operation time
 * @param order_status Status code (e.g., "1001")
 * @param final_total_amount Total amount
 */
case class OrderInfo(
    id: Long,
    create_time: String,
    operate_time: String,
    order_status: String,
    final_total_amount: Double
)

/**
 * Order Detail Case Class
 * @param id Detail ID (optional/inferred)
 * @param order_id Order ID
 * @param sku_id Item ID
 * @param sku_num Quantity
 * @param sku_price Price per unit
 * @param create_time Creation time
 */
case class OrderDetail(
    id: Long,
    order_id: Long,
    sku_id: Long,
    sku_num: Int,
    sku_price: Double,
    create_time: String
)

/**
 * Output for Redis Top3
 */
case class ItemConsumption(
    itemId: Long,
    amount: Double
)

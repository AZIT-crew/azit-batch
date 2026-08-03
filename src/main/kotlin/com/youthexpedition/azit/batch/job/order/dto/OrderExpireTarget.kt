package com.youthexpedition.azit.batch.job.order.dto

data class OrderExpireTarget(
    val orderId: Long,
    val memberId: Long,
    val usedPoints: Long,
)

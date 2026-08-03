package com.youthexpedition.azit.batch.job.order.processor

import com.youthexpedition.azit.batch.domain.Order
import com.youthexpedition.azit.batch.job.order.dto.OrderExpireTarget
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.stereotype.Component

@Component
class OrderExpireItemProcessor : ItemProcessor<Order, OrderExpireTarget> {
    override fun process(order: Order): OrderExpireTarget =
        OrderExpireTarget(
            orderId = order.id,
            memberId = order.memberId,
            usedPoints = order.usedPoints,
        )
}

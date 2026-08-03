package com.youthexpedition.azit.batch.job.order.writer

import com.youthexpedition.azit.batch.job.order.dto.OrderExpireTarget
import com.youthexpedition.azit.batch.repository.MemberRepository
import com.youthexpedition.azit.batch.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class OrderExpireItemWriter(
    private val orderRepository: OrderRepository,
    private val memberRepository: MemberRepository,
) : ItemWriter<OrderExpireTarget> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun write(chunk: Chunk<out OrderExpireTarget>) = chunk.items.forEach(::expire)

    private fun expire(target: OrderExpireTarget) {
        // 만료 처리 (writeCount에는 집계되지만 실제 처리가 아닌 건너뛴 건, 배치 실행 중 실제 입금 확인되어 PAID로 바뀐 주문을 보호하는 조건부 UPDATE)
        val expired = orderRepository.expireOrder(target.orderId)
        if (expired == 0) {
            log.warn("[ORDER_EXPIRE] orderId: {} 만료 대상이 아니어서 건너뜁니다. (실행 중 결제 확인 완료)", target.orderId)
            return
        }

        // 재고 복구
        orderRepository.findSkuQuantitiesByOrderId(target.orderId).forEach { item ->
            val restored = orderRepository.restoreStock(item.getSkuId(), item.getQuantity())
            if (restored == 0) {
                log.warn(
                    "[ORDER_EXPIRE] orderId: {} skuId: {} 재고 복구 대상을 찾지 못했습니다.",
                    target.orderId,
                    item.getSkuId(),
                )
            }
        }

        // 사용 포인트 환불 + 환불 이력 저장
        if (target.usedPoints > 0) {
            memberRepository.refundPoints(target.memberId, target.usedPoints)
            memberRepository.insertPointRefundHistory(
                target.memberId,
                target.usedPoints,
                target.orderId,
                LocalDateTime.now(),
            )
        }

        log.info("[ORDER_EXPIRE] orderId: {} 입금 기한 만료 처리를 완료했습니다.", target.orderId)
    }
}

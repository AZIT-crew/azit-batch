package com.youthexpedition.azit.batch.repository

import com.youthexpedition.azit.batch.domain.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OrderItemSkuQuantity {
    fun getSkuId(): Long

    fun getQuantity(): Int
}

interface OrderRepository : JpaRepository<Order, Long> {
    /**
     * 입금 기한 만료 처리. WHERE status = 'PENDING' 조건으로 그 사이 실제 입금 확인되어
     * PAID로 바뀐 주문을 보호한다. 영향 행이 0이면 후속 재고/포인트 처리를 진행하지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query(
        value = "UPDATE orders SET status = 'EXPIRED' WHERE id = :orderId AND status = 'PENDING'",
        nativeQuery = true,
    )
    fun expireOrder(
        @Param("orderId") orderId: Long,
    ): Int

    @Query(
        value = "SELECT sku_id AS skuId, quantity AS quantity FROM order_item WHERE order_id = :orderId",
        nativeQuery = true,
    )
    fun findSkuQuantitiesByOrderId(
        @Param("orderId") orderId: Long,
    ): List<OrderItemSkuQuantity>

    @Modifying(clearAutomatically = true)
    @Query(
        value = "UPDATE product_sku SET stock_quantity = stock_quantity + :quantity WHERE id = :skuId",
        nativeQuery = true,
    )
    fun restoreStock(
        @Param("skuId") skuId: Long,
        @Param("quantity") quantity: Int,
    ): Int
}

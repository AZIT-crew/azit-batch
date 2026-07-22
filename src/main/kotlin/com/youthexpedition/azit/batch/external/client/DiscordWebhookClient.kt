package com.youthexpedition.azit.batch.external.client

import com.youthexpedition.azit.batch.external.dto.DiscordWebhookMessageRequest
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.PostExchange

interface DiscordWebhookClient {
    @PostExchange
    fun send(
        @RequestBody request: DiscordWebhookMessageRequest,
    )
}

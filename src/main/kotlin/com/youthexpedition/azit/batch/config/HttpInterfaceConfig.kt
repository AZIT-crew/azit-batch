package com.youthexpedition.azit.batch.config

import com.youthexpedition.azit.batch.external.client.AppleAuthClient
import com.youthexpedition.azit.batch.external.client.DiscordWebhookClient
import com.youthexpedition.azit.batch.external.client.KakaoApiClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

/**
 * @HttpExchange HTTP Interface 클라이언트 프록시 등록.
 * RestClient 기반이므로 4xx/5xx 응답 시 RestClientResponseException(HttpClientErrorException 등)이 발생한다.
 */
@Configuration
class HttpInterfaceConfig(
    @Value("\${kakao.oauth.api-url}") private val kakaoApiUrl: String,
    @Value("\${apple.oauth.apple-url}") private val appleUrl: String,
    @Value("\${discord.webhook.notification-url}") private val discordWebhookUrl: String,
) {
    @Bean
    fun kakaoApiClient(): KakaoApiClient = createClient(kakaoApiUrl)

    @Bean
    fun appleAuthClient(): AppleAuthClient = createClient(appleUrl)

    @Bean
    fun discordWebhookClient(): DiscordWebhookClient = createClient(discordWebhookUrl)

    private inline fun <reified T : Any> createClient(baseUrl: String): T =
        HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(RestClient.builder().baseUrl(baseUrl).build()))
            .build()
            .createClient(T::class.java)
}

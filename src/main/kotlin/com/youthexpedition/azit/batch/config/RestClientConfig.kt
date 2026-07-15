package com.youthexpedition.azit.batch.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig(
    @Value("\${kakao.oauth.api-url}") private val kakaoApiUrl: String,
    @Value("\${apple.oauth.apple-url}") private val appleUrl: String,
) {
    @Bean
    fun kakaoRestClient(): RestClient = RestClient.builder().baseUrl(kakaoApiUrl).build()

    @Bean
    fun appleRestClient(): RestClient = RestClient.builder().baseUrl(appleUrl).build()
}

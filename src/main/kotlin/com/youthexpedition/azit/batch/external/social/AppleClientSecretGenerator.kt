package com.youthexpedition.azit.batch.external.social

import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date

/**
 * Apple API 인증용 client_secret(ES256 JWT) 생성.
 * AZIT_Server의 AppleJwtUtils.createClientSecret()과 동일한 방식.
 */
@Component
class AppleClientSecretGenerator(
    @Value("\${apple.oauth.team-id}") private val teamId: String,
    @Value("\${apple.oauth.client-id}") private val clientId: String,
    @Value("\${apple.oauth.key-id}") private val keyId: String,
    @Value("\${apple.oauth.key-path}") private val keyPath: String,
) {
    companion object {
        private const val AUDIENCE = "https://appleid.apple.com"
        private const val EXPIRATION_MINUTES = 5L
    }

    fun generate(): String {
        val now = Instant.now()
        return Jwts.builder()
            .header()
            .keyId(keyId)
            .add("alg", "ES256")
            .and()
            .issuer(teamId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(EXPIRATION_MINUTES, ChronoUnit.MINUTES)))
            .audience()
            .add(AUDIENCE)
            .and()
            .subject(clientId)
            .signWith(loadPrivateKey())
            .compact()
    }

    private fun loadPrivateKey(): PrivateKey =
        runCatching {
            val privateKeyPem = Files.readString(Paths.get(keyPath))
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace(Regex("\\s+"), "")
            val encoded = Base64.getDecoder().decode(privateKeyPem)
            KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(encoded))
        }.getOrElse { exception ->
            throw IllegalStateException("애플 시크릿 키 생성에 실패했습니다.", exception)
        }
}

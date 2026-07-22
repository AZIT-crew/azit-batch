package com.youthexpedition.azit.batch.external.dto

data class DiscordWebhookMessageRequest(
    val embeds: List<DiscordEmbed>,
)

data class DiscordEmbed(
    val title: String,
    val color: Int,
    val description: String? = null,
    val fields: List<DiscordEmbedField> = emptyList(),
    val footer: DiscordEmbedFooter? = null,
    val timestamp: String? = null,
)

data class DiscordEmbedField(
    val name: String,
    val value: String,
    val inline: Boolean = true,
)

data class DiscordEmbedFooter(
    val text: String,
)

package com.youthexpedition.azit.batch.job.listener

import com.youthexpedition.azit.batch.external.client.DiscordWebhookClient
import com.youthexpedition.azit.batch.external.dto.DiscordEmbed
import com.youthexpedition.azit.batch.external.dto.DiscordEmbedField
import com.youthexpedition.azit.batch.external.dto.DiscordEmbedFooter
import com.youthexpedition.azit.batch.external.dto.DiscordWebhookMessageRequest
import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.JobExecutionListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 모든 Job에 공통으로 붙는 실행 결과 알림 리스너. 잡별 JobBuilder에서 .listener(...)로 등록한다.
 * 디스코드 전송 실패가 배치 자체를 실패시키면 안 되므로 예외를 삼키고 로그만 남긴다.
 */
@Component
class DiscordNotificationJobListener(
    private val discordWebhookClient: DiscordWebhookClient,
    private val environment: Environment,
) : JobExecutionListener {
    companion object {
        private const val SUCCESS_COLOR = 0x00FF00
        private const val FAILURE_COLOR = 0xFF0000

        // 임베드 description 한도(4096자)에 여유를 두고 스택트레이스를 자른다.
        private const val ERROR_DETAIL_MAX_LENGTH = 3000
    }

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterJob(jobExecution: JobExecution) {
        runCatching {
            discordWebhookClient.send(DiscordWebhookMessageRequest(embeds = listOf(buildEmbed(jobExecution))))
        }.onFailure { log.error("[DISCORD] 배치 실행 결과 알림 전송 실패", it) }
    }

    private fun buildEmbed(jobExecution: JobExecution): DiscordEmbed {
        val profile = environment.activeProfiles.firstOrNull() ?: "unknown"
        val jobName = jobExecution.jobInstance.jobName
        val isSuccess = jobExecution.status == BatchStatus.COMPLETED
        val readCount = jobExecution.stepExecutions.sumOf { it.readCount }
        val writeCount = jobExecution.stepExecutions.sumOf { it.writeCount }
        val skipCount = jobExecution.stepExecutions.sumOf { it.skipCount }
        val durationSeconds =
            jobExecution.startTime?.let { start ->
                Duration.between(start, jobExecution.endTime ?: LocalDateTime.now()).seconds
            }
        val endTime = jobExecution.endTime ?: LocalDateTime.now()

        return DiscordEmbed(
            title = "⚙️ AZIT Batch 실행 결과",
            color = if (isSuccess) SUCCESS_COLOR else FAILURE_COLOR,
            description = if (isSuccess) null else buildErrorDescription(jobExecution),
            fields =
                listOf(
                    DiscordEmbedField("실행 환경", "`$profile`"),
                    DiscordEmbedField("배치 잡", "`$jobName`"),
                    DiscordEmbedField("결과", if (isSuccess) "✅ 성공" else "❌ 실패"),
                    DiscordEmbedField("대상", "${readCount}건"),
                    DiscordEmbedField("성공", "${writeCount}건"),
                    DiscordEmbedField("스킵", "${skipCount}건"),
                    DiscordEmbedField("소요시간", durationSeconds?.let { "${it}초" } ?: "알 수 없음"),
                ),
            timestamp = endTime.atZone(ZoneId.systemDefault()).toInstant().toString(),
        )
    }

    private fun buildErrorDescription(jobExecution: JobExecution): String? =
        jobExecution.failureExceptions.firstOrNull()?.let { exception ->
            val stackTrace = exception.stackTraceToString()
            val isTruncated = stackTrace.length > ERROR_DETAIL_MAX_LENGTH
            buildString {
                appendLine("**오류**: ${exception.message}")
                appendLine("```")
                append(stackTrace.take(ERROR_DETAIL_MAX_LENGTH))
                if (isTruncated) append("\n...(생략, 전체 스택트레이스는 서버 로그 참고)")
                appendLine()
                appendLine("```")
            }
        }
}

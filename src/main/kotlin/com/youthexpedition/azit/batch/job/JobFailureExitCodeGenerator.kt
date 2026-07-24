package com.youthexpedition.azit.batch.job

import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.JobExecutionListener
import org.springframework.boot.ExitCodeGenerator
import org.springframework.stereotype.Component

/**
 * 잡이 FAILED로 종료되면 프로세스 종료 코드를 1로 만들어, OS cron / 모니터링이 실패를 감지할 수 있게 한다.
 * main()의 SpringApplication.exit(context)가 이 ExitCodeGenerator 빈을 수집한다.
 *
 * 잡별 리스너로 등록해야 afterJob이 호출되므로, 각 JobConfig의 JobBuilder에 listener로 추가한다.
 */
@Component
class JobFailureExitCodeGenerator :
    JobExecutionListener,
    ExitCodeGenerator {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var exitCode = 0

    override fun afterJob(jobExecution: JobExecution) {
        if (jobExecution.status == BatchStatus.FAILED) {
            log.error("[BATCH] 잡이 실패 상태로 종료되었습니다. status={}", jobExecution.status)
            exitCode = 1
        }
    }

    override fun getExitCode(): Int = exitCode
}

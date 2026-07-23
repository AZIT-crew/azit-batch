package com.youthexpedition.azit.batch

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import kotlin.system.exitProcess

@SpringBootApplication
class AzitBatchApplication

fun main(args: Array<String>) {
    // 배치 잡은 시작 시 실행 후 종료된다. 잡 실패 시 non-zero 코드로 나가야 cron/모니터링이 실패를 감지한다.
    // SpringApplication.exit()가 등록된 ExitCodeGenerator(JobFailureExitCodeGenerator)를 수집한다.
    val context = runApplication<AzitBatchApplication>(*args)
    exitProcess(SpringApplication.exit(context))
}

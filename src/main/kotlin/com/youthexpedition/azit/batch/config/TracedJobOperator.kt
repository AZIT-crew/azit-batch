package com.youthexpedition.azit.batch.config

import com.newrelic.api.agent.NewRelic
import com.newrelic.api.agent.Trace
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.batch.core.launch.JobOperator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class TracedJobOperator(
    @Qualifier("jobOperator") private val delegate: JobOperator,
) : JobOperator by delegate {
    @Trace(dispatcher = true)
    override fun start(
        job: Job,
        jobParameters: JobParameters,
    ): JobExecution {
        NewRelic.setTransactionName("Batch", job.name)
        return delegate.start(job, jobParameters)
    }
}

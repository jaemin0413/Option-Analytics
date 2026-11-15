package com.option.domain.collection.batch

import com.option.domain.collection.entity.BackfillTarget
import com.option.domain.collection.repository.BackfillTargetBatchRepository
import com.option.domain.collection.repository.BackfillTargetRepository
import com.option.domain.collection.service.BackfillChunk
import com.option.domain.collection.service.BackfillCollector
import com.option.domain.market.service.UniverseService
import com.option.global.apiPayLoad.error.CollectionErrorCode
import com.option.global.apiPayLoad.exception.BusinessException
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Sort
import org.springframework.transaction.PlatformTransactionManager

// 백필용 잡, 계약 묶음 목록을 정하고 100개씩 청크로 넣음
// 청크의 행과 진행상황을 트랜잭션으로 저장, 재시도시에도 커밋한 청크는 넣지 않음
@Configuration
class BackfillBatchConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val backfillCollector: BackfillCollector,
    private val backfillTargetRepository: BackfillTargetRepository,
    private val backfillTargetBatchRepository: BackfillTargetBatchRepository,
    private val universeService: UniverseService,
    private val backfillRun: BackfillRun,
) {
    @Bean
    fun backfillBatchJob(): Job = JobBuilder(JOB_NAME, jobRepository)
        .start(backfillTargetStep())
        .next(backfillBarStep())
        .build()

    // 종목 하나씩 계약목록을 받아 저장
    @Bean
    fun backfillTargetStep(): Step = StepBuilder("backfillTargetStep", jobRepository)
        .tasklet({ _, chunkContext ->
            val state = chunkContext.stepContext.stepExecution.executionContext
            if (!state.containsKey(SYMBOLS)) {
                val universe = universeService.symbols()
                if (universe.isEmpty()) throw BusinessException(CollectionErrorCode.BACKFILL_NO_SYMBOLS)
                backfillTargetRepository.deleteAllInBatch()
                state.putString(SYMBOLS, universe.joinToString(","))
            }
            val index = state.getInt(NEXT_SYMBOL, 0)
            val symbols = state.getString(SYMBOLS).split(",").filter { it.isNotBlank() }
            if (index >= symbols.size) return@tasklet RepeatStatus.FINISHED

            val underlying = symbols[index]
            val contracts = backfillCollector.listContracts(backfillRun.context(), underlying, backfillRun.asOf())
            backfillTargetBatchRepository.insertAll(
                contracts.chunked(GROUP_SIZE).mapIndexed { i, group ->
                    BackfillTarget(underlying = underlying, groupIndex = i, contracts = group.joinToString(","))
                },
            )
            state.putInt(NEXT_SYMBOL, index + 1)
            RepeatStatus.CONTINUABLE
        }, transactionManager)
        .build()

    @Bean
    fun backfillBarStep(): Step = StepBuilder("backfillBarStep", jobRepository)
        .chunk<BackfillTarget, BackfillChunk>(CHUNK_GROUPS, transactionManager)
        .reader(
            RepositoryItemReaderBuilder<BackfillTarget>()
                .name("backfillTargetReader")
                .repository(backfillTargetRepository)
                .methodName("findAll")
                .sorts(mapOf("id" to Sort.Direction.ASC))
                .pageSize(CHUNK_GROUPS)
                .build(),
        )
        .processor { target: BackfillTarget -> backfillCollector.fetch(backfillRun.context(), target, backfillRun.end()) }
        .writer { chunk -> backfillCollector.write(backfillRun.context(), chunk.items) }
        .build()

    companion object {
        const val JOB_NAME = "backfill"
        const val GROUP_SIZE = 100
        const val CHUNK_GROUPS = 100
        private const val NEXT_SYMBOL = "nextSymbol"
        private const val SYMBOLS = "symbols"
    }
}

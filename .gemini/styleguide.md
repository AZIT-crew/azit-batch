# Azit Batch 프로젝트 코딩 및 아키텍처 스타일 가이드

이 문서는 Azit Batch 프로젝트의 코드 생성 및 리뷰를 위한 가이드라인입니다. Gemini는 모든 코드 제안 시 아래 규칙을 준수해야 합니다.

## 1. 기술 스택

- **Kotlin 2.3.21** + **JVM 21**
- **Spring Boot 4.1.0**
- **Spring Batch** (청크 기반 배치 처리)
- **Spring Data JPA** + **MySQL**
- **Spring Cloud OpenFeign** (외부 API 연동)
- **Jakarta** (javax 패키지 사용 금지)
- **Virtual Threads** 활성화 (`spring.threads.virtual.enabled: true`)

## 2. 아키텍처 원칙: Spring Batch 레이어 구조

### 패키지 구조
배치 잡(Job) 단위로 패키지를 구성합니다.

```
com.youthexpedition.azit.batch
├── job
│   └── {도메인명}               # 잡 단위 패키지 (예: member, crew, notification)
│       ├── {JobName}JobConfig.kt   # Job / Step 빈 정의
│       ├── reader
│       │   └── {JobName}ItemReader.kt
│       ├── processor
│       │   └── {JobName}ItemProcessor.kt
│       └── writer
│           └── {JobName}ItemWriter.kt
├── external                    # 외부 API 연동 (Feign 클라이언트)
│   ├── dto                     # 외부 API 요청/응답 DTO
│   └── feign                   # 포트 인터페이스 + Feign 어댑터
│       ├── SocialAuthPort.kt   # 소셜 연동 포트 인터페이스
│       ├── AppleAuthAdapter.kt # SocialAuthPort 구현체
│       └── KakaoAuthAdapter.kt # SocialAuthPort 구현체
├── domain                      # JPA 엔티티 (배치 전용 또는 메인 서버와 공유)
├── repository                  # Spring Data JPA Repository
└── config                      # 전역 배치 설정 (DataSource, JobLauncher 등)
```

### 계층별 규칙

- **JobConfig**: `@Configuration`으로 Job과 Step을 빈으로 등록합니다. 비즈니스 로직을 포함하지 않습니다.
- **ItemReader**: 데이터 읽기 전담. `JpaPagingItemReader` 또는 `JdbcPagingItemReader` 사용을 권장합니다.
- **ItemProcessor**: 단일 아이템 변환 및 비즈니스 로직 전담. 부수 효과(DB 쓰기, 외부 API 호출)를 포함하지 않습니다.
- **ItemWriter**: 데이터 쓰기 전담. `JpaItemWriter` 또는 커스텀 구현을 사용합니다.
- **Domain**: JPA 엔티티 및 비즈니스 핵심 모델. 배치 처리 흐름에 의존하지 않습니다.
- **External**: 외부 API(소셜 로그인 등) 연동을 담당합니다. Feign 어댑터와 요청/응답 DTO만 포함하며, 비즈니스 로직을 포함하지 않습니다.

## 3. Kotlin 코드 스타일

Java 프로젝트의 Lombok 역할을 Kotlin 언어 기능으로 대체합니다.

### 데이터 클래스
- DTO, Command, 읽기 전용 모델은 `data class`를 사용합니다.
- 엔티티는 `data class` 사용을 지양합니다 (JPA 프록시 문제). 일반 `class`로 선언합니다.

```kotlin
// DTO - data class 사용
data class MemberBatchDto(
    val memberId: Long,
    val nickname: String,
)

// JPA 엔티티 - 일반 class 사용
@Entity
@Table(name = "member")
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    var nickname: String,
)
```

### Null 안전성
- `!!` (non-null assertion) 사용을 지양합니다. `?: throw IllegalStateException(...)` 또는 `requireNotNull()`을 사용합니다.
- 외부 시스템(DB, API)으로부터 전달받는 값은 nullable 타입으로 명시적으로 처리합니다.

### 컬렉션 처리
- `for` 루프 대신 `map`, `filter`, `forEach` 등 Kotlin 컬렉션 함수를 활용합니다.
- 대용량 데이터 스트리밍이 필요한 경우 `Sequence`를 사용합니다.

### 함수 정의
- 단일 표현식 함수는 `=` 문법을 사용합니다.

```kotlin
// 권장
fun isExpired(now: LocalDateTime): Boolean = expiresAt.isBefore(now)

// 지양 (불필요하게 장황함)
fun isExpired(now: LocalDateTime): Boolean {
    return expiresAt.isBefore(now)
}
```

## 4. Spring Batch 작성 규칙

### Chunk 기반 처리 원칙
- 대용량 데이터는 반드시 청크(Chunk) 기반으로 처리합니다. `Tasklet`은 단순 단일 작업에만 허용합니다.
- 청크 사이즈는 상수로 분리하여 가독성을 높입니다.

```kotlin
@Configuration
class MemberStatusJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val memberItemReader: MemberItemReader,
    private val memberItemProcessor: MemberItemProcessor,
    private val memberItemWriter: MemberItemWriter,
) {
    companion object {
        const val JOB_NAME = "memberStatusJob"
        const val CHUNK_SIZE = 100
    }

    @Bean
    fun memberStatusJob(): Job = JobBuilder(JOB_NAME, jobRepository)
        .start(memberStatusStep())
        .build()

    @Bean
    fun memberStatusStep(): Step = StepBuilder("memberStatusStep", jobRepository)
        .chunk<InputType, OutputType>(CHUNK_SIZE, transactionManager)
        .reader(memberItemReader.reader())
        .processor(memberItemProcessor)
        .writer(memberItemWriter)
        .build()
}
```

### JobParameter 사용
- 잡 실행 시 외부에서 주입되는 값은 `@Value("#{jobParameters['key']}")`로 받습니다.
- 실행 날짜(`baseDate`)는 항상 JobParameter로 주입받아 멱등성을 보장합니다.

```kotlin
@Bean
@StepScope
fun memberItemReader(
    @Value("#{jobParameters['baseDate']}") baseDate: String,
): JpaPagingItemReader<Member> { ... }
```

### 멱등성 보장
- 동일한 JobParameter로 재실행해도 결과가 동일하도록 설계합니다.
- `JobInstanceAlreadyCompleteException`을 방지하기 위해 실행 시각 등을 JobParameter에 포함하는 방법을 고려합니다.

## 5. External 패키지 (Feign 클라이언트) 작성 규칙

### 구조 원칙
- `external/feign` 패키지에는 포트 인터페이스, Feign 클라이언트 인터페이스(`@FeignClient`), 어댑터 클래스가 모두 위치합니다.
- `external/dto` 패키지에는 외부 API 요청/응답 DTO만 위치합니다. 내부 도메인 모델과 혼용하지 않습니다.

### 포트 인터페이스 (SocialAuthPort)
- 잡 로직(Processor 등)은 구체 어댑터가 아닌 `SocialAuthPort` 인터페이스에만 의존합니다.
- 포트는 소셜 제공자(Provider)에 무관한 추상 메서드만 정의합니다.

```kotlin
interface SocialAuthPort {
    fun revokeToken(accessToken: String)
    fun supports(provider: SocialProvider): Boolean
}
```

### 어댑터 구현
- 각 소셜 제공자 어댑터는 `SocialAuthPort`를 구현하고, `supports()`로 자신이 처리할 수 있는 Provider를 선언합니다.
- Feign 클라이언트를 잡 로직에 직접 주입하지 않고 반드시 어댑터를 통해서만 호출합니다.

```kotlin
@Component
class KakaoAuthAdapter(
    private val kakaoAuthClient: KakaoAuthClient,
) : SocialAuthPort {

    override fun supports(provider: SocialProvider): Boolean =
        provider == SocialProvider.KAKAO

    override fun revokeToken(accessToken: String) {
        kakaoAuthClient.unlinkUser("Bearer $accessToken")
    }
}
```

### 어댑터 선택
- 여러 어댑터 중 적절한 구현체를 선택하는 책임은 잡 로직이 아닌 별도 팩토리 또는 `List<SocialAuthPort>` 주입 방식으로 처리합니다.

```kotlin
@Component
class SocialAuthPortRouter(
    private val adapters: List<SocialAuthPort>,
) {
    fun getAdapter(provider: SocialProvider): SocialAuthPort =
        adapters.find { it.supports(provider) }
            ?: throw IllegalArgumentException("지원하지 않는 소셜 제공자: $provider")
}
```

### Feign 예외 처리
- Feign 호출 실패 시 `FeignException`을 catch하여 비즈니스 예외로 변환하거나, 배치 스킵 대상으로 전파합니다.
- 소셜 API 오류로 인해 배치 전체가 실패하지 않도록 `faultTolerant().skip(FeignException::class.java)` 구성을 고려합니다.

### DTO 명명
- 요청: `{Provider}{목적}Request` (예: `KakaoTokenRevokeRequest`)
- 응답: `{Provider}{목적}Response` (예: `ApplePublicKeyResponse`)

## 6. 명명 규칙 (Naming)

서버 프로젝트의 명명 규칙을 준수합니다.

### 허용하는 표준 약어
- `DTO`, `VO`, `ID`, `API`, `JPA`
- 예: `MemberBatchDTO`, `CrewId` (O)

### 지양하는 모호한 줄임말 (풀네임 권장)
- `req` → `Request`, `res` → `Response`
- `cnt` → `Count`, `svc` → `Service`
- `proc` → `Processor`

### 잡/스텝 이름 규칙
- Job 이름: `{도메인}{목적}Job` (예: `memberStatusUpdateJob`, `notificationSendJob`)
- Step 이름: `{도메인}{목적}Step`
- Bean 상수명: `JOB_NAME`, `STEP_NAME`

## 7. 예외 처리

### 배치 스킵 & 재시도
- 일시적 오류(네트워크, DB 타임아웃)는 `faultTolerant().retry()`를 구성합니다.
- 비즈니스 규칙 위반으로 인한 스킵은 `faultTolerant().skip()`을 구성하고, `SkipListener`로 스킵된 항목을 반드시 로깅합니다.

```kotlin
.faultTolerant()
.skip(IllegalArgumentException::class.java)
.skipLimit(10)
.listener(skipLoggingListener)
```

### 예외 전파
- `ItemProcessor`에서 처리 불가능한 데이터는 예외를 던져 스킵 또는 잡 실패로 처리합니다. 조용히 `null`을 반환하는 방식은 의도를 명확히 할 때만 사용합니다 (`null` 반환 시 Writer에서 해당 아이템 제외됨).

## 8. 테스트 코드

### 단위 테스트 (기본)
- `ItemProcessor`의 비즈니스 로직은 단위 테스트로 검증합니다.
- 외부 의존성(DB, Feign)은 Mockito 또는 MockK로 모킹합니다.
- 테스트 메서드명은 `Given_When_Then` 구조를 따르는 한글 이름을 허용합니다.

### 통합 테스트 (선택적)
- `@SpringBatchTest` 기반 통합 테스트는 외부 시스템 연동이 불필요한 잡에만 작성합니다.
- 소셜 로그인 연동(Kakao, Apple 등) Feign 클라이언트처럼 실서버 없이는 의미 있는 검증이 어려운 경우, **수동 테스트로 대체**합니다.

### 수동 테스트 지원
- 수동 테스트를 위해 `application-local.yml`에서 잡을 개별 실행할 수 있도록 `spring.batch.job.name` 속성을 활용합니다.
- `CommandLineRunner` 또는 `JobLauncher`를 통해 로컬 환경에서 단일 잡을 실행할 수 있는 구조를 유지합니다.

```kotlin
// 단위 테스트 예시
@Test
fun `만료된_회원_토큰_무효화_처리_테스트`() {
    // given
    val expiredMember = Member(id = 1L, tokenExpiresAt = LocalDateTime.now().minusDays(1))

    // when
    val result = processor.process(expiredMember)

    // then
    assertThat(result?.isTokenRevoked).isTrue()
}
```

## 9. 로깅

- `private val log = LoggerFactory.getLogger(javaClass)`를 각 클래스에 선언합니다.
- 배치 처리 시작/종료, 처리 건수, 스킵 건수는 반드시 `INFO` 레벨로 로깅합니다.
- 민감 정보(개인정보, 토큰)는 로그에 포함하지 않습니다.

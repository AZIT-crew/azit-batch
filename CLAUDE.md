# Azit Batch 프로젝트 코드 생성 가이드

이 파일은 Claude가 Azit Batch 프로젝트에서 코드를 생성하거나 리뷰할 때 항상 준수해야 하는 규칙을 정의합니다.

---

## 1. 아키텍처: Spring Batch 레이어 구조

배치 잡(Job) 단위로 패키지를 구성하며, 외부 API 연동은 포트-어댑터 패턴을 따릅니다.

### 패키지 구조

```
com.youthexpedition.azit.batch
├── job
│   └── {도메인명}/                    # member, crew, notification ...
│       ├── {JobName}JobConfig.kt      # Job / Step 빈 정의
│       ├── reader/
│       │   └── {JobName}ItemReader.kt
│       ├── processor/
│       │   └── {JobName}ItemProcessor.kt
│       └── writer/
│           └── {JobName}ItemWriter.kt
├── external/                          # 외부 API 연동
│   ├── dto/                           # 외부 API 요청/응답 DTO
│   └── feign/
│       ├── SocialAuthPort.kt          # 소셜 연동 포트 인터페이스
│       ├── AppleAuthAdapter.kt        # SocialAuthPort 구현체
│       └── KakaoAuthAdapter.kt        # SocialAuthPort 구현체
├── domain/                            # JPA 엔티티 및 Enum
├── repository/                        # Spring Data JPA Repository
└── config/                            # 전역 설정 (DataSource, JobLauncher 등)
```

### 계층별 책임

- **JobConfig**: `@Configuration`으로 Job과 Step만 빈으로 등록합니다. 비즈니스 로직을 포함하지 않습니다.
- **ItemReader**: 데이터 읽기 전담. `JpaPagingItemReader` 또는 `JdbcPagingItemReader`를 사용합니다.
- **ItemProcessor**: 단일 아이템 변환 및 비즈니스 로직 전담. DB 쓰기, 외부 API 호출 등 부수 효과를 포함하지 않습니다.
- **ItemWriter**: 데이터 쓰기 전담.
- **SocialAuthPort**: 소셜 제공자 무관한 추상 계약. 잡 로직은 구체 어댑터가 아닌 이 인터페이스에만 의존합니다.
- **{Provider}AuthAdapter**: `SocialAuthPort` 구현체. Feign 클라이언트를 감싸고 외부 응답 DTO를 내부 형태로 변환합니다.

### 의존 방향

```
JobProcessor → SocialAuthPort ← KakaoAuthAdapter
                               ← AppleAuthAdapter
```

---

## 2. Kotlin 코드 스타일

### 데이터 클래스

- DTO, 읽기 전용 모델은 `data class`를 사용합니다.
- JPA 엔티티는 `data class` 사용을 금지합니다 (프록시 문제). 일반 `class`로 선언합니다.

```kotlin
// DTO
data class MemberRevokeDto(
    val memberId: Long,
    val socialProvider: SocialProvider,
    val accessToken: String,
)

// JPA 엔티티
@Entity
@Table(name = "member")
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    var status: MemberStatus,
)
```

### Null 안전성

- `!!` 사용을 금지합니다. `requireNotNull()` 또는 `?: throw IllegalStateException()`을 사용합니다.
- 외부 시스템(DB, Feign)에서 전달받는 값은 nullable 타입으로 명시적으로 처리합니다.

### 함수 정의

- 단일 표현식 함수는 `=` 문법을 사용합니다.

```kotlin
// ✅
fun isExpired(now: LocalDateTime): Boolean = expiresAt.isBefore(now)

// ❌
fun isExpired(now: LocalDateTime): Boolean {
    return expiresAt.isBefore(now)
}
```

---

## 3. Spring Batch 작성 규칙

### Chunk 기반 처리

- 대용량 데이터는 반드시 청크 기반으로 처리합니다. `Tasklet`은 단순 단일 작업에만 허용합니다.
- 청크 사이즈는 `companion object`에 상수로 분리합니다.

```kotlin
@Configuration
class MemberRevokeJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val memberItemReader: MemberRevokeItemReader,
    private val memberItemProcessor: MemberRevokeItemProcessor,
    private val memberItemWriter: MemberRevokeItemWriter,
) {
    companion object {
        const val JOB_NAME = "memberRevokeJob"
        const val CHUNK_SIZE = 100
    }

    @Bean
    fun memberRevokeJob(): Job = JobBuilder(JOB_NAME, jobRepository)
        .start(memberRevokeStep())
        .build()

    @Bean
    fun memberRevokeStep(): Step = StepBuilder("memberRevokeStep", jobRepository)
        .chunk<Member, Member>(CHUNK_SIZE, transactionManager)
        .reader(memberItemReader.reader())
        .processor(memberItemProcessor)
        .writer(memberItemWriter)
        .build()
}
```

### JobParameter

- 실행 날짜(`baseDate`)는 항상 JobParameter로 주입받아 멱등성을 보장합니다.
- `@StepScope`와 함께 `@Value("#{jobParameters['key']}")`로 주입합니다.

```kotlin
@Bean
@StepScope
fun memberRevokeItemReader(
    @Value("#{jobParameters['baseDate']}") baseDate: String,
): JpaPagingItemReader<Member> { ... }
```

### 멱등성

- 동일한 JobParameter로 재실행해도 결과가 동일하도록 설계합니다.

---

## 4. External 패키지 (Feign) 작성 규칙

### 포트 인터페이스

- 잡 로직은 `SocialAuthPort` 인터페이스에만 의존합니다. 구체 어댑터를 직접 주입하지 않습니다.

```kotlin
interface SocialAuthPort {
    fun revokeToken(accessToken: String)
    fun supports(provider: SocialProvider): Boolean
}
```

### 어댑터 구현

- 각 어댑터는 `SocialAuthPort`를 구현하고 `supports()`로 처리 가능한 Provider를 선언합니다.
- Feign 클라이언트(`@FeignClient`)는 어댑터 내부에서만 사용합니다.

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

### 어댑터 라우팅

- Provider에 따른 어댑터 선택은 `List<SocialAuthPort>` 주입 방식으로 처리합니다. 잡 로직에 `if/when (provider == KAKAO)` 분기를 두지 않습니다.

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

### DTO 명명

- 요청: `{Provider}{목적}Request` (예: `KakaoTokenRevokeRequest`)
- 응답: `{Provider}{목적}Response` (예: `ApplePublicKeyResponse`)

### Feign 예외 처리

- `FeignException`을 catch하여 배치 스킵 대상으로 전파하거나 로깅 후 처리합니다.
- 소셜 API 오류로 배치 전체가 실패하지 않도록 `faultTolerant().skip(FeignException::class.java)` 구성을 고려합니다.

---

## 5. 명명 규칙

### 허용하는 표준 약어

| 약어 | 사용 예 |
|------|---------|
| `DTO` | `MemberRevokeDTO` |
| `ID` | `MemberId` |
| `API` | `KakaoAPI` |
| `JPA` | `JpaPagingItemReader` |

### 지양하는 줄임말 → 풀네임으로 대체

| ❌ 지양 | ✅ 사용 |
|--------|--------|
| `req` | `Request` |
| `res` | `Response` |
| `cnt` | `Count` |
| `svc` | `Service` |
| `proc` | `Processor` |

### 잡/스텝 이름 규칙

- Job: `{도메인}{목적}Job` (예: `memberRevokeJob`, `notificationSendJob`)
- Step: `{도메인}{목적}Step`
- 상수: `companion object { const val JOB_NAME = "..." }`

---

## 6. 예외 처리

### 배치 스킵 & 재시도

```kotlin
.faultTolerant()
.skip(IllegalArgumentException::class.java)
.skip(FeignException::class.java)
.skipLimit(10)
.listener(skipLoggingListener)
```

### ItemProcessor의 null 반환

- `null`을 반환하면 Writer에서 해당 아이템이 제외됩니다. 의도적으로 사용하는 경우에만 허용하며, 이유를 주석으로 명시합니다.

---

## 7. 테스트 코드

### 단위 테스트 (기본)

- `ItemProcessor`의 비즈니스 로직은 단위 테스트로 검증합니다.
- 외부 의존성(DB, Feign)은 MockK 또는 Mockito로 모킹합니다.
- 테스트 메서드명: 영어 `메서드명_상황_기대결과` 구조를 따릅니다.

```kotlin
@Test
fun process_returnsNull_whenMemberAlreadyWithdrawn() {
    // given
    val member = Member(id = 1L, status = MemberStatus.WITHDRAWN)

    // when
    val result = processor.process(member)

    // then
    assertThat(result).isNull()
}
```

### 통합 테스트 (선택적)

- 외부 API(Feign) 연동이 없는 잡에 한해 `@SpringBatchTest`로 통합 테스트를 작성합니다.
- 소셜 로그인 연동처럼 실서버 없이 의미 있는 검증이 어려운 경우 **수동 테스트로 대체**합니다.

### 수동 테스트 지원

- `application-local.yml`의 `spring.batch.job.name` 속성으로 로컬에서 단일 잡을 실행할 수 있는 구조를 유지합니다.

---

## 8. 로깅

- `private val log = LoggerFactory.getLogger(javaClass)`를 각 클래스에 선언합니다.
- 배치 처리 시작/종료, 처리 건수, 스킵 건수는 `INFO` 레벨로 로깅합니다.
- 민감 정보(개인정보, 토큰)는 로그에 포함하지 않습니다.

---

## 9. 기술 스택 준수

- **Kotlin 2.3.21** + **JVM 21**: Kotlin 관용 문법(data class, 확장 함수, 컬렉션 함수) 적극 활용
- **Spring Boot 4.1.0**
- **Jakarta**: `javax.*` 패키지 대신 `jakarta.*` 패키지 사용
- **Virtual Threads**: `spring.threads.virtual.enabled: true` 활성화 상태 유지

---

## 10. 빠른 체크리스트

코드 생성 또는 PR 리뷰 시 아래 항목을 확인합니다.

- [ ] JPA 엔티티에 `data class`를 사용하지 않았는가?
- [ ] `!!` (non-null assertion) 사용이 없는가?
- [ ] `javax.*` 대신 `jakarta.*`를 사용하고 있는가?
- [ ] `req`, `res`, `svc` 등 모호한 줄임말을 사용하지 않았는가?
- [ ] 잡 로직이 `SocialAuthPort` 인터페이스를 통해 어댑터를 호출하는가?
- [ ] Feign 클라이언트를 잡 로직에 직접 주입하지 않았는가?
- [ ] `baseDate` 등 실행 기준값을 JobParameter로 주입받는가?
- [ ] 청크 사이즈가 `companion object` 상수로 분리되어 있는가?
- [ ] `ItemProcessor`에 DB 쓰기 또는 외부 API 호출이 포함되어 있지 않은가?
- [ ] 핵심 비즈니스 로직(Processor)에 단위 테스트가 작성되어 있는가?

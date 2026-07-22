plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "com.youthexpedition"
version = "0.0.1-SNAPSHOT"
description = "azit-batch"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework:spring-web") // RestClient (소셜 revoke API 호출)
    implementation("io.jsonwebtoken:jjwt-api:0.12.3") // Apple client_secret(ES256 JWT) 생성
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
    implementation(platform("software.amazon.awssdk:bom:2.29.9"))
    implementation("software.amazon.awssdk:s3") // 프로필 이미지 삭제
    runtimeOnly("com.mysql:mysql-connector-j")
    // Batch 잡 실행을 뉴렐릭 백그라운드 트랜잭션으로 표시하기 위한 @Trace 어노테이션
    // (계측 자체는 Dockerfile의 -javaagent가 수행하며, 이 API는 agent 미부착 시 no-op으로 동작)
    implementation("com.newrelic.agent.java:newrelic-api:9.1.0")
    testImplementation("org.springframework.boot:spring-boot-starter-batch-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

ktlint {
    version.set("1.7.1")
    outputToConsole.set(true)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

detekt {
    config.setFrom(files("$rootDir/detekt.yml"))
    buildUponDefaultConfig = true
    source.setFrom(files("src/main/kotlin", "src/test/kotlin"))
}

// detekt 1.23.x는 Kotlin 2.0.21로 컴파일되어 프로젝트 Kotlin(2.3.21)과 클래스패스가 충돌하므로
// detekt 실행 클래스패스의 Kotlin 버전을 detekt가 지원하는 버전으로 고정한다.
configurations.matching { it.name.startsWith("detekt") }.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.21")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// plain jar(원본 jar) 생성을 꺼서 build/libs에 실행 가능한 bootJar 산출물만 남긴다.
// Dockerfile이 "-plain" 제외 글롭 없이 build/libs/*.jar 하나만 신뢰하고 복사할 수 있게 하기 위함
tasks.named("jar") {
    enabled = false
}

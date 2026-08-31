plugins {
    id("java")
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "ru.yandex.practicum"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")

    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")
    implementation("io.micrometer:micrometer-core")

    implementation("io.micrometer:micrometer-registry-prometheus")

    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.0")
    }
}

tasks.bootJar {
    archiveFileName.set("gateway.jar")
}
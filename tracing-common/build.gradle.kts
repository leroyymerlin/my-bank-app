plugins {
    id("java")
    id("io.spring.dependency-management") version "1.1.6"
}

group = "ru.yandex.practicum"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.7")
    }
}

dependencies {
    implementation("org.springframework.kafka:spring-kafka")
    implementation("io.zipkin.brave:brave")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("org.slf4j:slf4j-api")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

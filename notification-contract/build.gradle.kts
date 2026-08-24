plugins {
    id("java")
    id("io.spring.dependency-management") version "1.1.6"
}

group = "ru.yandex.practicum"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.0")
}

tasks.test {
    useJUnitPlatform()
}

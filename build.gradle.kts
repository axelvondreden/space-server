plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "de.axl"
version = "0.0.3"

application {
    mainClass.set("io.ktor.server.tomcat.jakarta.EngineMain")

    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=false")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.tomcat.jakarta)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.thymeleaf)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.h2)
    implementation(libs.logback.classic)
    implementation(libs.bcrypt)
    implementation(libs.thymeleaf.time)
    implementation(libs.pdfbox)
    implementation(libs.pdfbox.tools)
    implementation("net.coobird:thumbnailator:0.4.20")
}

ktor {
    fatJar {
        archiveFileName.set("space-server.jar")
    }
}

val createVersionProperties by tasks.registering(WriteProperties::class) {
    val filePath = sourceSets.main.map {
        it.output.resourcesDir!!.resolve("de/axl/version.properties")
    }
    destinationFile = filePath

    property("version", project.version.toString())
}

tasks.classes {
    dependsOn(createVersionProperties)
}
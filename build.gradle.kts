import de.marcphilipp.gradle.nexus.NexusPublishPlugin
import java.time.Duration

plugins {
    kotlin("jvm") version Versions.kotlin apply false
    id("io.codearte.nexus-staging") version Versions.`nexus-staging`
    id("de.marcphilipp.nexus-publish") version Versions.`nexus-publish`
    jacoco
    base
}

allprojects {
    repositories {
        mavenCentral()
        jcenter()
    }
}

subprojects {
    group = "org.taymyr.lagom"
    version = "1.3.0-SNAPSHOT"

    apply<JacocoPlugin>()
    apply<NexusPublishPlugin>()

    jacoco {
        toolVersion = Versions.jacoco
    }

    nexusPublishing {
        repositories {
            sonatype()
        }
        clientTimeout.set(Duration.parse("PT10M")) // 10 minutes
    }
}

val jacocoAggregateMerge by tasks.creating(JacocoMerge::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    executionData(
        project(":lagom-openapi-core").buildDir.absolutePath + "/jacoco/test.exec",
        project(":java:lagom-openapi-java-impl").buildDir.absolutePath + "/jacoco/test.exec",
        project(":scala:lagom-openapi-scala-impl").buildDir.absolutePath + "/jacoco/scalaTest.exec"
    )
    dependsOn(
        ":lagom-openapi-core:test",
        ":java:lagom-openapi-java-impl:test",
        ":scala:lagom-openapi-scala-impl:test"
    )
}

@Suppress("UnstableApiUsage")
val jacocoAggregateReport by tasks.creating(JacocoReport::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    executionData(jacocoAggregateMerge.destinationFile)
    reports {
        xml.isEnabled = true
    }
    additionalClassDirs(files(subprojects.flatMap { project ->
        listOf("scala", "kotlin").map { project.buildDir.path + "/classes/$it/main" }
    }))
    additionalSourceDirs(files(subprojects.flatMap { project ->
        listOf("scala", "kotlin").map { project.file("src/main/$it").absolutePath }
    }))
    dependsOn(jacocoAggregateMerge)
}

tasks.check { finalizedBy(jacocoAggregateReport) }

nexusStaging {
    packageGroup = "org.taymyr"
    username = ossrhUsername
    password = ossrhPassword
    numberOfRetries = 360 // 1 hour if 10 seconds delay
    delayBetweenRetriesInMillis = 10000 // 10 seconds
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}

tasks.closeRepository {
    mustRunAfter(subprojects.map { it.tasks.getByName("publishToSonatype") }.toTypedArray())
}
tasks.closeAndReleaseRepository {
    mustRunAfter(subprojects.map { it.tasks.getByName("publishToSonatype") }.toTypedArray())
}
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    java
    jacoco
    id("io.qameta.allure") version "2.12.0"
    id("io.freefair.lombok") version "8.13"
    id("com.diffplug.spotless") version "8.8.0"
    id("com.github.ben-manes.versions") version "0.54.0"
}

group = "io.bookwright"
version = providers.gradleProperty("projectVersion").get()

object Versions {
    const val JUNIT = "5.13.4"
    const val GUICE = "7.0.0"
    const val RETROFIT = "3.0.0"
    const val OKHTTP = "5.1.0"
    const val JACKSON = "2.19.2"
    const val ALLURE = "2.29.1"
    const val ALLURE_CLI = "2.39.0"
    const val PLAYWRIGHT = "1.53.0"
    const val OWNER = "1.0.12"
    const val AWAITILITY = "4.3.0"
    const val JDBI = "3.49.5"
    const val MYSQL = "9.3.0"
    const val HIKARI = "6.3.0"
    const val JSCH = "2.27.0"
    const val ASSERTJ = "3.27.3"
    const val LOGBACK = "1.5.18"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:${Versions.JACKSON}"))
    implementation(platform("com.squareup.okhttp3:okhttp-bom:${Versions.OKHTTP}"))
    implementation(platform("io.qameta.allure:allure-bom:${Versions.ALLURE}"))

    implementation("com.google.inject:guice:${Versions.GUICE}")
    implementation("com.squareup.retrofit2:retrofit:${Versions.RETROFIT}")
    implementation("com.squareup.retrofit2:converter-jackson:${Versions.RETROFIT}")
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("io.qameta.allure:allure-junit5")
    implementation("io.qameta.allure:allure-assertj")
    implementation("com.microsoft.playwright:playwright:${Versions.PLAYWRIGHT}")
    implementation("org.aeonbits.owner:owner:${Versions.OWNER}")
    implementation("org.awaitility:awaitility:${Versions.AWAITILITY}")
    implementation("org.jdbi:jdbi3-core:${Versions.JDBI}")
    implementation("org.jdbi:jdbi3-sqlobject:${Versions.JDBI}")
    implementation("com.mysql:mysql-connector-j:${Versions.MYSQL}")
    implementation("com.zaxxer:HikariCP:${Versions.HIKARI}")
    implementation("com.github.mwiede:jsch:${Versions.JSCH}")
    implementation("org.assertj:assertj-core:${Versions.ASSERTJ}")
    implementation("ch.qos.logback:logback-classic:${Versions.LOGBACK}")

    implementation(platform("org.junit:junit-bom:${Versions.JUNIT}"))
    implementation("org.junit.jupiter:junit-jupiter")
    implementation("org.junit.platform:junit-platform-launcher")

    testImplementation("com.squareup.okhttp3:mockwebserver")
}

allure {
    version = Versions.ALLURE_CLI
    adapter {
        autoconfigure = true
        frameworks {
            junit5 {
                adapterVersion = Versions.ALLURE
            }
        }
    }
}

spotless {
    java {
        target("src/**/*.java", "local-app/src/**/*.java")
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("projectFiles") {
        target(
            "*.kts",
            "*.properties",
            ".github/**/*.yml",
            "docker/**/*.yml",
            "scripts/**/*.sh",
            "test-environment/profile",
            "test-environment/**/*.yml",
            "test-environment/**/*.yaml",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

fun Test.configureBookwrightTestRuntime() {
    dependsOn("validateVersion")
    useJUnitPlatform()
    listOf(
        "STAND",
        "SEMAPHORE_PROFILE",
        "SEMAPHORE_SCHEDULE_TIMEZONE",
        "SEMAPHORE_ENCRYPTION_ROTATION_PHASE",
        "SEMAPHORE_UPGRADE_PHASE",
        "junit.jupiter.execution.parallel.enabled",
        "DB_PASSWORD",
        "SSH_PASSWORD",
        "test.seed",
    ).forEach { key ->
        (System.getProperty(key) ?: System.getenv(key))?.let { systemProperty(key, it) }
    }
    mapOf(
        "TEST_REPOSITORY" to "git.fixtures.repository",
        "TEST_BRANCH" to "git.fixtures.branch",
    ).forEach { (environmentName, configKey) ->
        (System.getProperty(configKey) ?: System.getenv(environmentName))
            ?.takeIf(String::isNotBlank)
            ?.let { systemProperty(configKey, it) }
    }
    mapOf(
        "bookwright.test.ssl.trustStore" to "javax.net.ssl.trustStore",
        "bookwright.test.ssl.trustStorePassword" to "javax.net.ssl.trustStorePassword",
    ).forEach { (source, target) ->
        System.getProperty(source)?.let { systemProperty(target, it) }
    }
    val configPrefixes = listOf("api.", "ui.", "db.", "ssh.", "runner.", "teardown.", "local.booking.", "local.user.")
    System.getProperties().stringPropertyNames()
        .filter { key -> configPrefixes.any(key::startsWith) }
        .forEach { key -> systemProperty(key, System.getProperty(key)) }
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = System.getProperty("verbose") != null
    }
}

tasks.withType<Test>().configureEach {
    configureBookwrightTestRuntime()
}

tasks.test {
    useJUnitPlatform {
        val includeTags = System.getProperty("includeTags")
        val excludeTags = System.getProperty("excludeTags")
        if (includeTags.isNullOrBlank()) {
            excludeTags("external", "external-managed", "external-managed-setup")
        } else {
            includeTags(*includeTags.split(",").toTypedArray())
        }
        if (!excludeTags.isNullOrBlank()) excludeTags(*excludeTags.split(",").toTypedArray())
    }
}

val frameworkTest = tasks.register<Test>("frameworkTest") {
    group = "verification"
    description = "Runs deterministic self-tests for the framework infrastructure."
    filter {
        includeTestsMatching("io.bookwright.api.*")
        includeTestsMatching("io.bookwright.config.*")
        includeTestsMatching("io.bookwright.junit.*")
        includeTestsMatching("io.bookwright.teardown.*")
        includeTestsMatching("io.bookwright.ui.*")
        includeTestsMatching("io.bookwright.util.*")
        includeTestsMatching("io.bookwright.tests.framework.*")
    }
}

tasks.register<Test>("apiTest") {
    group = "verification"
    description = "Runs Semaphore API product scenarios."
    filter { includeTestsMatching("io.bookwright.tests.semaphore.*") }
}

val apiCoverageObservations = layout.buildDirectory.file("api-coverage/observations.tsv")
val apiCoverageReport = layout.buildDirectory.file("api-coverage/report.json")

val semaphoreApiCoverageCapture = tasks.register<Test>("semaphoreApiCoverageCapture") {
    group = "verification"
    description = "Runs Semaphore API scenarios while recording exercised HTTP operations."
    filter { includeTestsMatching("io.bookwright.tests.semaphore.*") }
    outputs.upToDateWhen { false }
    systemProperty(
        "bookwright.api.coverage.file",
        apiCoverageObservations.get().asFile.absolutePath,
    )
    systemProperty(
        "STAND",
        System.getProperty("STAND") ?: System.getenv("STAND") ?: "semaphore",
    )
    doFirst { delete(apiCoverageObservations) }
}

fun JavaExec.configureSemaphoreApiCoverageReport() {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "io.bookwright.api.coverage.ApiCoverageReporter"
    systemProperty("api.coverage.observations", apiCoverageObservations.get().asFile.absolutePath)
    systemProperty("api.coverage.report", apiCoverageReport.get().asFile.absolutePath)
    systemProperty(
        "STAND",
        System.getProperty("STAND") ?: System.getenv("STAND") ?: "semaphore",
    )
    listOf("api.base.url", "ui.base.url", "api.coverage.spec").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
    mapOf(
        "bookwright.test.ssl.trustStore" to "javax.net.ssl.trustStore",
        "bookwright.test.ssl.trustStorePassword" to "javax.net.ssl.trustStorePassword",
    ).forEach { (source, target) ->
        System.getProperty(source)?.let { systemProperty(target, it) }
    }
}

tasks.register<JavaExec>("semaphoreApiCoverage") {
    description = "Runs Semaphore API tests and prints documented endpoint coverage."
    dependsOn(semaphoreApiCoverageCapture)
    configureSemaphoreApiCoverageReport()
}

tasks.register<JavaExec>("semaphoreApiCoverageReport") {
    description = "Prints documented endpoint coverage from the latest recorded API run."
    dependsOn("testClasses")
    configureSemaphoreApiCoverageReport()
}

tasks.register<Test>("externalTest") {
    group = "verification"
    description = "Runs read-only API checks against a user-managed Semaphore instance."
    useJUnitPlatform { includeTags("external") }
    filter { includeTestsMatching("io.bookwright.tests.external.*") }
    systemProperty("STAND", "external")
    maxParallelForks = 1

    val externalConfig =
        mapOf(
            "API_BASE_URL" to "api.base.url",
            "API_USERNAME" to "api.username",
            "API_PASSWORD" to "api.password",
        )

    externalConfig.forEach { (environmentName, configKey) ->
        System.getenv(environmentName)
            ?.takeIf(String::isNotBlank)
            ?.let { value -> environment(configKey, value) }
    }

    doFirst {
        val missing =
            externalConfig.filter { (environmentName, configKey) ->
                System.getenv(environmentName).isNullOrBlank() &&
                    System.getProperty(configKey).isNullOrBlank()
            }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "externalTest requires " +
                    missing.entries.joinToString { (environmentName, configKey) ->
                        "$environmentName or -D$configKey"
                    },
            )
        }

        val baseUrl = System.getProperty("api.base.url") ?: System.getenv("API_BASE_URL")
        if (baseUrl == null || !baseUrl.matches(Regex("https?://.+/api/"))) {
            throw GradleException(
                "External API base URL must use http(s) and end with /api/: $baseUrl",
            )
        }
    }
}

fun Test.configureManagedExternalTest() {
    val externalConnectionConfig =
        mapOf(
            "API_BASE_URL" to "api.base.url",
            "API_USERNAME" to "api.username",
            "API_PASSWORD" to "api.password",
        )
    val managedConfig =
        listOf(
            "EXTERNAL_MUTATIONS_ALLOWED",
        )
    val managedOverrides =
        listOf(
            "EXTERNAL_MANAGED_FIXTURE_REPOSITORY",
            "EXTERNAL_MANAGED_FIXTURE_BRANCH",
        )

    group = "verification"
    filter { includeTestsMatching("io.bookwright.tests.external.*") }
    systemProperty("STAND", "external")
    maxParallelForks = 1
    externalConnectionConfig.forEach { (environmentName, configKey) ->
        System.getenv(environmentName)
            ?.takeIf(String::isNotBlank)
            ?.let { value -> environment(configKey, value) }
    }
    (managedConfig + managedOverrides).forEach { key ->
        System.getProperty(key)?.let { value -> systemProperty(key, value) }
    }

    doFirst {
        val missingConnection =
            externalConnectionConfig.filter { (environmentName, configKey) ->
                System.getenv(environmentName).isNullOrBlank() &&
                    System.getProperty(configKey).isNullOrBlank()
            }
        val missingManaged =
            managedConfig.filter { key ->
                System.getenv(key).isNullOrBlank() && System.getProperty(key).isNullOrBlank()
            }
        if (missingConnection.isNotEmpty() || missingManaged.isNotEmpty()) {
            val requiredConnection =
                missingConnection.entries.map { (environmentName, configKey) ->
                    "$environmentName or -D$configKey"
                }
            val requiredManaged = missingManaged.map { key -> "$key or -D$key" }
            throw GradleException(
                "$name requires " +
                    (requiredConnection + requiredManaged).joinToString(),
            )
        }

        val mutationsAllowed =
            System.getProperty("EXTERNAL_MUTATIONS_ALLOWED")
                ?: System.getenv("EXTERNAL_MUTATIONS_ALLOWED")
        if (mutationsAllowed != "true") {
            throw GradleException(
                "$name changes the target stand; set EXTERNAL_MUTATIONS_ALLOWED=true explicitly",
            )
        }

        val baseUrl = System.getProperty("api.base.url") ?: System.getenv("API_BASE_URL")
        if (baseUrl == null || !baseUrl.matches(Regex("https?://.+/api/"))) {
            throw GradleException(
                "External API base URL must use http(s) and end with /api/: $baseUrl",
            )
        }
    }
}

val externalManagedSetup =
    tasks.register<Test>("externalManagedSetup") {
        description = "Creates the persistent managed-external project fixture when it is absent."
        useJUnitPlatform { includeTags("external-managed-setup") }
        configureManagedExternalTest()
    }

tasks.register<Test>("externalManagedTest") {
    description = "Runs task checks against the persistent managed-external project fixture."
    useJUnitPlatform { includeTags("external-managed") }
    configureManagedExternalTest()
    mustRunAfter(externalManagedSetup)
}

tasks.register<Test>("upgradeTest") {
    group = "verification"
    description = "Runs the seed or verify phase of the Semaphore release-upgrade scenario."
    filter { includeTestsMatching("io.bookwright.tests.semaphore.UpgradeCompatibilityTest") }
}

tasks.register<Test>("encryptionRotationTest") {
    group = "verification"
    description = "Runs one phase of the Semaphore database-encryption key rotation scenario."
    filter { includeTestsMatching("io.bookwright.tests.semaphore.EncryptionKeyRotationTest") }
}

tasks.register<Test>("uiTest") {
    group = "verification"
    description = "Runs Playwright product scenarios."
    filter { includeTestsMatching("io.bookwright.tests.ui.*") }
}

tasks.register<Test>("totpTest") {
    group = "verification"
    description = "Runs the Semaphore API and browser TOTP lifecycle scenarios."
    filter {
        includeTestsMatching("io.bookwright.tests.semaphore.SemaphoreTotpAuthenticationTest")
        includeTestsMatching("io.bookwright.tests.ui.semaphore.SemaphoreTotpLoginTest")
    }
}

tasks.register<JavaExec>("playwrightInstallChromium") {
    group = "verification"
    description = "Installs Chromium and its Linux dependencies for Playwright product tests."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.microsoft.playwright.CLI"
    args("install", "--with-deps", "chromium")
}

tasks.register<Test>("dbTest") {
    group = "verification"
    description = "Runs database scenarios through the SSH tunnel."
    filter { includeTestsMatching("io.bookwright.tests.db.*") }
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs cross-layer API-to-database scenarios against the integrated local system."
    filter { includeTestsMatching("io.bookwright.tests.integration.*") }
}

val frameworkCoverageClasses = sourceSets.main.get().output.asFileTree.matching {
    include(
        "io/bookwright/api/**",
        "io/bookwright/config/**",
        "io/bookwright/junit/**",
        "io/bookwright/teardown/**",
        "io/bookwright/ui/**",
        "io/bookwright/util/**",
    )
    // Product contracts and page objects are exercised by target suites, not framework self-tests.
    exclude(
        "io/bookwright/api/model/**",
        "io/bookwright/api/semaphore/**",
        "io/bookwright/ui/*Page*",
    )
}

val frameworkJacocoReport = tasks.register<JacocoReport>("frameworkJacocoReport") {
    group = "verification"
    description = "Generates JaCoCo coverage for framework self-tests."
    dependsOn(frameworkTest)
    executionData(layout.buildDirectory.file("jacoco/frameworkTest.exec"))
    sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
    classDirectories.setFrom(frameworkCoverageClasses)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.register<JacocoCoverageVerification>("frameworkJacocoVerification") {
    group = "verification"
    description = "Enforces the minimum framework self-test coverage."
    dependsOn(frameworkJacocoReport)
    executionData(layout.buildDirectory.file("jacoco/frameworkTest.exec"))
    sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
    classDirectories.setFrom(frameworkCoverageClasses)
    violationRules {
        rule {
            limit { minimum = "0.60".toBigDecimal() }
        }
    }
}

tasks.register("qualityGate") {
    group = "verification"
    description = "Runs deterministic local quality checks without external product systems."
    dependsOn("spotlessCheck", "frameworkJacocoVerification", "validateVersion", "validateChangelogStyle")
}

tasks.register("validateVersion") {
    group = "verification"
    description = "Checks that projectVersion is valid SemVer and matches a release tag when present."

    doLast {
        val projectVersion = project.version.toString()
        val semVer = Regex(
            """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*))*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$"""
        )
        check(semVer.matches(projectVersion)) {
            "projectVersion '$projectVersion' is not valid Semantic Versioning"
        }

        val releaseTag = System.getenv("GITHUB_REF_NAME")
            ?.takeIf { System.getenv("GITHUB_REF_TYPE") == "tag" }
        if (releaseTag != null) {
            check(releaseTag == "v$projectVersion") {
                "Release tag '$releaseTag' does not match projectVersion '$projectVersion' (expected v$projectVersion)"
            }
        }
    }
}

tasks.register("validateChangelogStyle") {
    group = "verification"
    description = "Rejects changelog bullets that repeat their section heading."

    doLast {
        val repeatedPrefixes = mapOf(
            "Added" to "Added ",
            "Changed" to "Changed ",
            "Deprecated" to "Deprecated ",
            "Removed" to "Removed ",
            "Fixed" to "Fixed ",
            "Security" to "Security ",
        )
        var section: String? = null
        val violations = mutableListOf<String>()
        file("CHANGELOG.md").readLines().forEachIndexed { index, line ->
            if (line.startsWith("### ")) section = line.removePrefix("### ").trim()
            val repeated = repeatedPrefixes[section]
            if (repeated != null && line.startsWith("- $repeated")) {
                violations += "CHANGELOG.md:${index + 1}: '$section' bullet repeats '$repeated'"
            }
        }
        check(violations.isEmpty()) { violations.joinToString(System.lineSeparator()) }
    }
}

tasks.register("printVersion") {
    group = "help"
    description = "Prints the current bookwright version."
    doLast {
        println(project.version)
    }
}

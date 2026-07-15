import groovy.json.JsonSlurper
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

val mavenCentralRepoBase = "https://repo1.maven.org/maven2/studio/forgenav"
val stagingApiBase = "https://ossrh-staging-api.central.sonatype.com"

/** Root KMP modules (metadata / umbrella POMs). */
val publishableLibraryArtifactIds = listOf(
    "forgenv-core",
    "forgenv-compose",
    "forgenv-syncforge",
    "forgenv-testing",
)

/**
 * Full set of module coordinates that must resolve on repo1.maven.org after a complete
 * multiplatform publish (matches Central Portal component set for 1.1.0).
 */
val mavenCentralRequiredArtifactIds = listOf(
    // forgenv-core
    "forgenv-core",
    "forgenv-core-android",
    "forgenv-core-jvm",
    "forgenv-core-iosarm64",
    "forgenv-core-iosx64",
    "forgenv-core-iossimulatorarm64",
    // forgenv-compose
    "forgenv-compose",
    "forgenv-compose-android",
    "forgenv-compose-iosarm64",
    "forgenv-compose-iosx64",
    "forgenv-compose-iossimulatorarm64",
    // forgenv-syncforge
    "forgenv-syncforge",
    "forgenv-syncforge-android",
    "forgenv-syncforge-jvm",
    "forgenv-syncforge-iosarm64",
    "forgenv-syncforge-iosx64",
    "forgenv-syncforge-iossimulatorarm64",
    // forgenv-testing (1.1.0+)
    "forgenv-testing",
    "forgenv-testing-android",
    "forgenv-testing-jvm",
    "forgenv-testing-iosarm64",
    "forgenv-testing-iosx64",
    "forgenv-testing-iossimulatorarm64",
)

fun Project.readGradlePropertiesVersion(propertiesFile: java.io.File): String {
    val props = java.util.Properties()
    propertiesFile.inputStream().use { props.load(it) }
    return props.getProperty("forgenav.version")
        ?: error("forgenav.version missing in ${propertiesFile.path}")
}

fun pomUrl(artifact: String, version: String): String =
    "$mavenCentralRepoBase/$artifact/$version/$artifact-$version.pom"

fun pomHttpStatus(url: String): Int =
    try {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.responseCode
    } catch (_: Exception) {
        -1
    }

fun verifyMavenCentralArtifacts(
    logger: org.gradle.api.logging.Logger,
    artifacts: List<String>,
    version: String,
    retries: Int,
    sleepSec: Int,
) {
    var missing = artifacts.toList()
    repeat(retries) { attempt ->
        missing = missing.filter { artifact ->
            val url = pomUrl(artifact, version)
            val code = pomHttpStatus(url)
            logger.lifecycle("  HEAD $url → $code")
            code != 200
        }
        if (missing.isEmpty()) {
            logger.lifecycle("All ${artifacts.size} checked artifacts are on Maven Central.")
            return
        }
        if (attempt < retries - 1) {
            logger.lifecycle(
                "Waiting ${sleepSec}s for Central sync (attempt ${attempt + 1}/$retries)...",
            )
            Thread.sleep(sleepSec * 1000L)
        }
    }
    throw GradleException(
        "Missing on Maven Central (root POMs only checked): ${missing.joinToString()}. " +
            "Platform variants may still sync; verify portal Deployments if root modules are present.",
    )
}

fun mavenCentralStagingConnectTimeoutMs(): Int =
    System.getenv("MAVEN_CENTRAL_STAGING_CONNECT_TIMEOUT_MS")?.toIntOrNull() ?: 60_000

fun mavenCentralStagingReadTimeoutMs(): Int =
    System.getenv("MAVEN_CENTRAL_STAGING_READ_TIMEOUT_MS")?.toIntOrNull() ?: 600_000

fun mavenCentralStagingMaxRetries(): Int =
    System.getenv("MAVEN_CENTRAL_STAGING_MAX_RETRIES")?.toIntOrNull() ?: 5

fun mavenCentralStagingRetrySleepMs(attempt: Int): Long =
    (System.getenv("MAVEN_CENTRAL_STAGING_RETRY_SLEEP_SEC")?.toIntOrNull() ?: 15) *
        (attempt + 1) * 1000L

fun isRetryableStagingError(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is java.net.SocketTimeoutException ||
            current is java.net.ConnectException ||
            current is java.io.IOException ||
            current is java.net.http.HttpTimeoutException
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

class MavenCentralStagingClient(
    private val username: String,
    private val password: String,
    private val logger: org.gradle.api.logging.Logger,
    private val connectTimeoutMs: Int = mavenCentralStagingConnectTimeoutMs(),
    private val readTimeoutMs: Int = mavenCentralStagingReadTimeoutMs(),
    private val maxRetries: Int = mavenCentralStagingMaxRetries(),
) {
    private val authHeader: String =
        "Bearer ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"

    private val httpClient: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs.toLong()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    private fun executeOnce(method: String, path: String): String {
        val request =
            HttpRequest.newBuilder()
                .uri(URI("$stagingApiBase$path"))
                .timeout(Duration.ofMillis(readTimeoutMs.toLong()))
                .header("Authorization", authHeader)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val body = response.body()?.trim().orEmpty()
        val code = response.statusCode()
        if (code !in 200..299) {
            throw GradleException("Staging API $method $path failed: HTTP $code $body")
        }
        return body
    }

    fun request(method: String, path: String): String {
        var lastError: Throwable? = null
        for (attempt in 0 until maxRetries) {
            try {
                return executeOnce(method, path)
            } catch (error: Throwable) {
                lastError = error
                if (attempt < maxRetries - 1 && isRetryableStagingError(error)) {
                    val sleepMs = mavenCentralStagingRetrySleepMs(attempt)
                    logger.lifecycle(
                        "Staging API $method $path failed (${error.javaClass.simpleName}: ${error.message}) " +
                            "— retry ${attempt + 2}/$maxRetries in ${sleepMs / 1000}s",
                    )
                    Thread.sleep(sleepMs)
                } else {
                    break
                }
            }
        }
        throw GradleException(
            "Staging API $method $path failed after $maxRetries attempt(s)",
            lastError,
        )
    }

    fun requestOrEmpty(method: String, path: String): String? =
        try {
            request(method, path)
        } catch (error: GradleException) {
            if (error.message?.contains("HTTP 404") == true) null else throw error
        }
}

@Suppress("UNCHECKED_CAST")
fun promoteOrphanedStagingUploads(
    logger: org.gradle.api.logging.Logger,
    client: MavenCentralStagingClient,
    namespace: String,
    dropInvalidOnFailure: Boolean,
    dropAllOpenStaging: Boolean,
) {
    logger.lifecycle("Searching for staging repositories (any IP)...")
    val raw = client.request("GET", "/manual/search/repositories?ip=any&profile_id=$namespace")
    val data = JsonSlurper().parseText(raw) as Map<String, Any?>
    val repos = data["repositories"] as? List<Map<String, Any?>> ?: emptyList()
    if (repos.isEmpty()) {
        logger.lifecycle("No staging repositories found.")
        return
    }

    var promoted = 0
    var dropped = 0
    var skipped = 0
    var failed = 0

    for (repo in repos) {
        val key = repo["key"]?.toString() ?: continue
        val state = repo["state"]?.toString()
        val portalId = repo["portal_deployment_id"]
        logger.lifecycle("  repo=$key state=$state portal_deployment_id=$portalId")

        if (dropAllOpenStaging && portalId == null && state == "open") {
            runCatching {
                val body = client.request("DELETE", "/manual/drop/repository/$key")
                logger.lifecycle("  -> dropped open staging repo $key: $body")
                dropped++
            }.onFailure {
                logger.error("  -> failed to drop open repo $key: ${it.message}")
                failed++
            }
            continue
        }

        if (portalId != null) {
            skipped++
            continue
        }

        runCatching {
            val body = client.request("POST", "/manual/upload/repository/$key")
            logger.lifecycle("  -> promoted $key: $body")
            promoted++
        }.onFailure { error ->
            logger.error("  -> failed to promote $key: ${error.message}")
            if (dropInvalidOnFailure &&
                (error.message?.contains("HTTP 400") == true ||
                    error.message?.contains("HTTP 422") == true)
            ) {
                runCatching {
                    val dropBody = client.request("DELETE", "/manual/drop/repository/$key")
                    logger.lifecycle("  -> dropped invalid staging repo $key: $dropBody")
                    dropped++
                    return@onFailure
                }.onFailure { dropError ->
                    logger.error("  -> failed to drop $key: ${dropError.message}")
                }
            }
            failed++
        }
    }

    logger.lifecycle("Summary: promoted=$promoted dropped=$dropped skipped=$skipped failed=$failed")
    if (failed > 0) {
        throw GradleException("Maven Central staging finalize had $failed failure(s)")
    }
}

tasks.register("finalizeMavenCentralStaging") {
    group = "publishing"
    description =
        "Transfers OSSRH staging uploads to Central Portal (same flow as SyncForge)."
    doLast {
        val namespace = providers.gradleProperty("mavenCentralNamespace").orNull
            ?: "studio.forgenav"
        val username = System.getenv("MAVEN_CENTRAL_USERNAME")
            ?: error("MAVEN_CENTRAL_USERNAME is required")
        val password = System.getenv("MAVEN_CENTRAL_PASSWORD")
            ?: error("MAVEN_CENTRAL_PASSWORD is required")
        val client = MavenCentralStagingClient(username, password, logger)
        val finalizeCurrentIp = System.getenv("FINALIZE_CURRENT_IP")?.toBooleanStrictOrNull() ?: true
        val dropInvalid = System.getenv("DROP_INVALID_ON_FAILURE")?.toBooleanStrictOrNull() ?: true
        val dropAllOpen = System.getenv("DROP_ALL_OPEN_STAGING")?.toBooleanStrictOrNull() ?: false

        if (finalizeCurrentIp) {
            logger.lifecycle("Finalizing staging upload for namespace $namespace (current CI IP)...")
            runCatching {
                val body = client.request("POST", "/manual/upload/defaultRepository/$namespace")
                logger.lifecycle("Current-IP finalize response: $body")
            }.onFailure { error ->
                if (error.message?.contains("HTTP 404") == true) {
                    logger.lifecycle("No default repository for current IP (404) — searching any IP.")
                } else {
                    throw error
                }
            }
        }

        promoteOrphanedStagingUploads(
            logger = logger,
            client = client,
            namespace = namespace,
            dropInvalidOnFailure = dropInvalid,
            dropAllOpenStaging = dropAllOpen,
        )
    }
}

fun Project.resolveVerifyMavenCentralVersion(): String {
    val raw = providers.gradleProperty("verifyMavenCentralVersion").orNull?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: providers.gradleProperty("verifyMavenCentralTag").orNull?.trim()
            ?.takeIf { it.isNotEmpty() }
        ?: readGradlePropertiesVersion(rootProject.file("gradle.properties"))
    return raw.removePrefix("v")
}

tasks.register("verifyMavenCentralArtifacts") {
    group = "verification"
    description =
        "Checks all required ForgeNav module POMs on repo1.maven.org " +
            "(use -PverifyMavenCentralVersion=1.1.0 or -PverifyMavenCentralTag=v1.1.0)."
    doLast {
        val version = resolveVerifyMavenCentralVersion()
        val retries = providers.environmentVariable("MAVEN_CENTRAL_VERIFY_RETRIES")
            .orNull?.toIntOrNull() ?: 12
        val sleepSec = providers.environmentVariable("MAVEN_CENTRAL_VERIFY_SLEEP_SEC")
            .orNull?.toIntOrNull() ?: 30
        logger.lifecycle(
            "Verifying ${mavenCentralRequiredArtifactIds.size} Maven Central POMs " +
                "for studio.forgenav:*:$version",
        )
        verifyMavenCentralArtifacts(
            logger = logger,
            artifacts = mavenCentralRequiredArtifactIds,
            version = version,
            retries = retries,
            sleepSec = sleepSec,
        )
    }
}

tasks.register("verifyMavenCentralRootArtifacts") {
    group = "verification"
    description = "Checks only root module POMs (core/compose/syncforge) on Maven Central."
    doLast {
        val version = resolveVerifyMavenCentralVersion()
        val retries = providers.environmentVariable("MAVEN_CENTRAL_VERIFY_RETRIES")
            .orNull?.toIntOrNull() ?: 12
        val sleepSec = providers.environmentVariable("MAVEN_CENTRAL_VERIFY_SLEEP_SEC")
            .orNull?.toIntOrNull() ?: 30
        verifyMavenCentralArtifacts(
            logger = logger,
            artifacts = publishableLibraryArtifactIds,
            version = version,
            retries = retries,
            sleepSec = sleepSec,
        )
    }
}

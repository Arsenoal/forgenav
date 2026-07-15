# Maven Central publish

**Repository:** [github.com/Arsenoal/forgenav](https://github.com/Arsenoal/forgenav)  
**Group ID:** `studio.forgenav` (namespace verified via DNS on `forgenav.studio`)  
**Current release on Central:** **`1.0.0`** (tag `v1.0.0`, verified on repo1.maven.org)  
**Publish workflow:** [.github/workflows/publish-release.yml](../.github/workflows/publish-release.yml) (`v*` tag push or manual dispatch)  
**Verify workflow:** [.github/workflows/verify-maven-central.yml](../.github/workflows/verify-maven-central.yml) (manual)

## Published modules

| Coordinate | Description |
|------------|-------------|
| `studio.forgenav:forgenv-core` | Navigation + MVI + sync ports |
| `studio.forgenav:forgenv-compose` | Compose NavHost, transitions, sync UI |
| `studio.forgenav:forgenv-syncforge` | SyncForge adapters + local loop |

KMP also publishes platform variants (e.g. `-android`, `-jvm`, Apple targets) from the same modules.

Browse: [repo1.maven.org/maven2/studio/forgenav/](https://repo1.maven.org/maven2/studio/forgenav/)

## Consumer

```kotlin
dependencies {
    implementation("studio.forgenav:forgenv-core:1.0.0")
    implementation("studio.forgenav:forgenv-compose:1.0.0")
    // optional:
    implementation("studio.forgenav:forgenv-syncforge:1.0.0")
}
```

## 1. One-time setup (completed for 1.0.0)

1. ~~Register domain `forgenav.studio`.~~
2. ~~Sonatype Central → namespace `studio.forgenav` → DNS TXT verify.~~
3. Portal **API token** (username + password) — keep in GitHub Actions secrets.
4. **GPG** signing key — keep in GitHub Actions secrets (can share with SyncForge).

## 2. GitHub Actions secrets

**Arsenoal/forgenav** → Settings → Secrets and variables → Actions:

| Secret | Description |
|--------|-------------|
| `MAVEN_CENTRAL_USERNAME` | Sonatype Portal token username |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype Portal token password |
| `SIGNING_IN_MEMORY_KEY_B64` | **Recommended** — base64 of armored private key (single line) |
| `SIGNING_IN_MEMORY_KEY` | Alternative — full armored private key |
| `SIGNING_IN_MEMORY_KEY_ID` | Optional — **8 hex chars** only (e.g. `1DF1CDEB`) |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | Optional passphrase |

Generate base64 secret:

```bash
gpg --armor --export-secret-keys YOUR_KEY_ID | base64 -w0   # Linux
gpg --armor --export-secret-keys YOUR_KEY_ID | base64       # macOS
```

You can reuse the same secrets as **Arsenoal/syncforge** if the key/token are shared.

## 3. Local dry-run

`~/.gradle/gradle.properties` (never commit):

```properties
mavenCentralUsername=<portal-token-username>
mavenCentralPassword=<portal-token-password>
signing.inMemoryKey=<armored-private-key>
signing.inMemoryKeyId=<optional-8-hex>
signing.inMemoryKeyPassword=<optional>
```

```bash
# Local Maven only
./gradlew publishAllToMavenLocal

# Pre-publish validation
./gradlew verifyReleaseSignOff

# Staging upload (credentials required)
./gradlew verifyPublishSigning publishAllToMavenCentral \
  -PmavenCentralPublishing=true \
  -PsignAllPublications=true

# Promote staging → Central Portal deployments
MAVEN_CENTRAL_USERNAME=... MAVEN_CENTRAL_PASSWORD=... \
  ./gradlew finalizeMavenCentralStaging -PmavenCentralNamespace=studio.forgenav
```

Then in [Central Portal → Deployments](https://central.sonatype.com/): **Publish** VALIDATED deployments.

## 4. Release (GitHub Actions)

1. Ensure `main` is green (CI).
2. Bump `forgenav.version` + `CHANGELOG.md` if needed; commit; tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

3. **Actions → Publish Release → Run workflow** → enter tag `v1.0.0`.
4. After the job succeeds: Central Portal → **Publish** deployments.
5. Optional: create a GitHub Release for the tag in the UI.
6. After sync (~15–60 min), validate all artifacts:

**GitHub (manual job):** Actions → **Verify Maven Central** → Run workflow → tag `v1.0.0`  
(or version `1.0.0`). Retries until POMs appear on `repo1.maven.org`.

**Locally:**

```bash
./gradlew verifyMavenCentralArtifacts -PverifyMavenCentralVersion=1.0.0
# or
./gradlew verifyMavenCentralArtifacts -PverifyMavenCentralTag=v1.0.0
```

## 5. POM metadata (repo)

| Property | Value |
|----------|-------|
| `forgenav.group` | `studio.forgenav` |
| `forgenav.pom.url` | `https://github.com/Arsenoal/forgenav` |
| License | Apache 2.0 |

Version for a release is set from the git tag in CI (`v1.0.0` → `forgenav.version=1.0.0`).

# Release process

**Current release:** `1.2.0` (transactional back stack / N-BS-12).  
**Previous:** `1.1.0` (Nav3 Phase A) remains available on Maven Central.

## Preconditions

- [x] Namespace `studio.forgenav` verified on Central (domain `forgenav.studio`)
- [x] GitHub secrets configured (see [MAVEN_PUBLISH.md](MAVEN_PUBLISH.md))
- [x] `CHANGELOG.md` updated for the version
- [x] Docs (README, MAVEN_PUBLISH, REQUIREMENTS, NAV3_PARITY, CONTRIBUTING) pin **1.2.0**
- [ ] `main` CI green (check Actions before tagging if uncertain)

## Steps

1. On `main`, set version if not already:

```properties
forgenav.version=1.2.0
```

2. Commit and push `main` (changelog + version + docs).

3. Tag and push (this **automatically** starts **Publish Release**):

```bash
git tag v1.2.0
git push origin v1.2.0
```

Or re-run manually: GitHub → **Actions** → **Publish Release** → **Run workflow** → tag `v1.2.0`.

4. Job runs on **macOS**: tests, compile, signed upload of all library publications, portal transfer.

5. [Central Portal](https://central.sonatype.com/) → **Deployments** → **Publish** all VALIDATED rows for this release (if not auto-published).

6. Create **GitHub Release** for `v1.2.0` (optional but recommended) with notes from [CHANGELOG.md](../CHANGELOG.md#120---2026-07-15).

7. After Central sync, run **Actions → Verify Maven Central** (manual) with tag `v1.2.0`, or:

```bash
./gradlew verifyMavenCentralArtifacts -PverifyMavenCentralVersion=1.2.0
```

## Coordinates (1.2.0)

```text
studio.forgenav:forgenv-core:1.2.0
studio.forgenav:forgenv-compose:1.2.0
studio.forgenav:forgenv-syncforge:1.2.0
studio.forgenav:forgenv-testing:1.2.0
```

## Notes for 1.2.0

- Behavioral fix for multi-step stack mutations (single `StateFlow` emission); public navigate API unchanged.
- See [BACKSTACK_TRANSACTIONS.md](BACKSTACK_TRANSACTIONS.md) and [CHANGELOG.md](../CHANGELOG.md#120---2026-07-15).

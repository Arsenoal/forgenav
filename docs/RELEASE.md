# Release process

## Preconditions

- [ ] Namespace `studio.forgenav` verified on Central (domain `forgenav.studio`)
- [ ] GitHub secrets configured (see [MAVEN_PUBLISH.md](MAVEN_PUBLISH.md))
- [ ] `main` CI green
- [ ] `CHANGELOG.md` updated for the version

## Steps

1. On `main`, set version if not already:

```properties
forgenav.version=1.0.0
```

2. Commit and push `main`.

3. Tag and push:

```bash
git tag v1.0.0
git push origin v1.0.0
```

4. GitHub → **Actions** → **Publish Release** → **Run workflow** → tag `v1.0.0`.

5. Job runs on **macOS**: tests, compile, signed upload to staging, portal transfer.

6. [Central Portal](https://central.sonatype.com/) → **Deployments** → **Publish** all VALIDATED rows for this release.

7. Create **GitHub Release** for `v1.0.0` (optional but recommended).

8. After Central sync:

```bash
./gradlew verifyMavenCentralArtifacts -PverifyMavenCentralVersion=1.0.0
```

## Coordinates

```text
studio.forgenav:forgenv-core:1.0.0
studio.forgenav:forgenv-compose:1.0.0
studio.forgenav:forgenv-syncforge:1.0.0
```

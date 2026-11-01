# Contributing

## Branches

| Branch | Purpose | Publishes |
|---|---|---|
| `dev` | Integration; feature branches merge here | Nothing |
| `staging` | Release candidates, promoted from `dev` | Pre-release, e.g. `v1.2.0-rc.1` |
| `master` | Production, promoted from `staging` | Release, e.g. `v1.2.0` |

1. Branch from `dev`, for example `feat/barcode-history`.
2. Open a PR into `dev` and squash-merge it.
3. To cut a release candidate, open a PR from `dev` into `staging` and use a **merge commit**.
4. To release, open a PR from `staging` into `master` and use a **merge commit**.

Promotions need a merge commit so semantic-release can still see the individual commits. Coolify
deploys the backend from `master`, so a promotion to `master` also ships any server changes.

## Commit messages

PR titles and commits follow [Conventional Commits](https://www.conventionalcommits.org). The type
decides the next version:

| Commit | Example | Version |
|---|---|---|
| `fix:` | `fix(sync): retry after a dropped connection` | Patch |
| `feat:` | `feat(scan): read Polish labels` | Minor |
| `feat!:` or a `BREAKING CHANGE:` footer | `feat!: require Android 15` | Major |
| `chore:`, `docs:`, `refactor:`, `test:`, `ci:` | `docs: explain sync` | No release |

## Before opening a PR

```sh
./gradlew testDebugUnitTest verifyRoborazziDebug lintDebug
```

UI changes need new screenshots: run `recordRoborazziDebug` and commit the PNGs.

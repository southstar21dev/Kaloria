# Contributing

Contributions are welcome. Kaloria is licensed under the [GNU GPL v3.0](LICENSE) with an
additional permission for Google ML Kit ([NOTICE.md](NOTICE.md)). By opening a pull request, you
agree that:

- your contribution is your own work (or you have the right to submit it), and
- it is licensed under the same terms as the project, including that additional permission, and
  you also allow the maintainer to relicense it together with the rest of Kaloria.

The second point keeps the maintainer able to change the project's license later without tracking
down every contributor.

## Branches

| Branch | Purpose |
|---|---|
| `dev` | Integration; feature branches merge here |
| `staging` | Testing before production, promoted from `dev` |
| `master` | Production, promoted from `staging`; each promotion can create a version tag |

1. Branch from `dev`, for example `feat/barcode-history`.
2. Open a PR into `dev` and squash-merge it.
3. To test a set of changes, open a PR from `dev` into `staging` and use a **merge commit**.
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
./gradlew spotlessApply                                   # format (ktlint, rules in .editorconfig)
./gradlew testDebugUnitTest :core:model:test lintDebug detekt
```

`:core:model` is a plain Kotlin module, so its tests run under `test`, not `testDebugUnitTest`.
detekt reports only findings that aren't in `config/detekt/baseline.xml`.

## Screenshot tests

CI is the source of truth for screenshots: baselines are rendered on Linux, and macOS draws the
glass surfaces differently, so `verifyRoborazziDebug` is expected to fail on a Mac.

After a UI change, run the **Update screenshots** workflow on your branch:

```sh
gh workflow run update-screenshots.yml --ref your-branch
```

It records and commits the new baselines, then starts CI. Review the changed PNGs in the PR.

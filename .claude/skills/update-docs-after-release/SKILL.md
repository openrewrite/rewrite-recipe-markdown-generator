---
name: update-docs-after-release
description: Bring docs.openrewrite.org and docs.moderne.io up to date after a release train, and publish the OpenRewrite changelog. Checks that the full update-docs.yml runs in rewrite-docs and moderne-docs went through (starting them if they did not), reviews the commits they pushed, then runs the generator locally to open the changelog pull request in rewrite-docs and the recipeDescriptors.yml pull request here. Use after a Code Genome Project release train, or when asked to update the recipe docs, publish the changelog, or update recipeDescriptors.yml.
---

# Update the docs after a release train

The Code Genome Project releases the recipe modules as a nightly train, and once their packages are on npmjs, PyPI and
NuGet it starts `update-docs.yml` in [rewrite-docs](https://github.com/openrewrite/rewrite-docs) and
[moderne-docs](https://github.com/moderneinc/moderne-docs) with `latest-versions-only` false. Those regenerate the full
recipe catalog and push an `[Auto] Update docs` commit straight to the default branch. No workflow publishes the
changelog or updates `src/main/resources/recipeDescriptors.yml`, so those take a local run and two pull requests.

The changelog covers every change since `recipeDescriptors.yml` was last committed, so this does not have to follow
every train; skipping one only makes the next changelog longer.

Work from this repository, with `rewrite-docs` and `moderne-docs` checked out beside it, and stop to ask whenever a
check below does not come out as described.

## 1. Find the train

```shell
gh release view --repo moderneinc/moderne-recipe-bom --json tagName,publishedAt
gh release view --repo openrewrite/rewrite --json tagName,publishedAt
```

The changelog is named after the rewrite version: 8.94.0 gives `8-94-0-Release.md`.

## 2. Check the docs workflows ran in full

For each of `openrewrite/rewrite-docs` and `moderneinc/moderne-docs`:

```shell
gh run list --repo openrewrite/rewrite-docs --workflow update-docs.yml --event workflow_dispatch --limit 5 \
  --json databaseId,status,conclusion,createdAt,url
```

- **A successful run started after the bom's release**: nothing to start; review its commit in step 3.
- **A run still queued or in progress**: wait for it with `gh run watch <id> --repo <repo>`. Never start another
  meanwhile; the workflow's concurrency group cancels the run in progress.
- **No run since the release**: the Code Genome Project waits for the public registries before it starts them, so
  check that they have the train's versions:
  ```shell
  npm view @openrewrite/rewrite version
  curl -s https://pypi.org/pypi/openrewrite/json | jq -r .info.version
  curl -s https://api.nuget.org/v3-flatcontainer/openrewrite.csharp.tool/index.json | jq -r '.versions[-1]'
  ```
  While any of them is behind rewrite's release, a run would fail installing it, so wait. Once all three have it and
  still nothing has started, start both:
  ```shell
  gh workflow run update-docs.yml --repo openrewrite/rewrite-docs -f latest-versions-only=false
  gh workflow run update-docs.yml --repo moderneinc/moderne-docs -f latest-versions-only=false
  ```
- **A failed run**: read `gh run view <id> --repo <repo> --log-failed`. A failure fetching a package is the registries
  above; a failure in the generator is fixed here, with a pull request, before starting the workflow again.

## 3. Review what the workflows pushed

In each docs checkout, pull and find the commit the full run pushed. The nightly scheduled runs push small
`[Auto] Update docs` commits too, which only touch the latest versions; the full run's touches the recipe pages.

```shell
git -C ../rewrite-docs pull -q
git -C ../rewrite-docs log --since=<publishedAt> --grep='^\[Auto\] Update docs' --format='%h %ad %s' --shortstat
```

- **Deleted pages are recipe removals.** List them with `git show --diff-filter=D --name-only --format= <sha>`. A few
  deletions match recipes removed upstream; hundreds mean a language failed to load in the workflow, which still
  passes when a language loads only some of its recipes. Start the workflow again rather than leave the pages gone.
- **The site built.** `gh run list --repo <repo> --commit <sha>` shows the pages deployment for the commit; a missing
  recipe category fails it, and the fix goes in this repository.
- **New pages render.** Open a few pages of recipes added in this train on docs.openrewrite.org and docs.moderne.io;
  wrap special elements in their descriptions in backticks in the recipe's own repository where they break the page.

## 4. Generate the changelog

Install every prerequisite in the [README](../../../README.md#prerequisites): Java 21, Node.js, Python with `pip`,
the .NET SDK, Go with `rewrite-go-rpc` on `PATH`, and `codegenomeUsername` and `codegenomePassword` in
`~/.gradle/gradle.properties`. A loader whose toolchain is missing only warns, so the build passes with recipes
missing from the changelog. Two catches:

- Homebrew's Python refuses `pip install` (PEP 668), so put a virtual environment first on `PATH`, on Python 3.12 as
  the workflows use: `python3.12 -m venv /tmp/rwdocs-venv && export PATH="/tmp/rwdocs-venv/bin:$PATH"`.
- `go install github.com/openrewrite/rewrite/rewrite-go/cmd/rpc@latest` installs a binary named `rpc`; link it as
  `rewrite-go-rpc` with `ln -sf "$(go env GOPATH)/bin/rpc" "$(go env GOPATH)/bin/rewrite-go-rpc"`, and put
  `$(go env GOPATH)/bin` on `PATH`.

Generate from the descriptors on `main`. A run rewrites `recipeDescriptors.yml`, so restore it before any rerun, or
the second run compares against the first and the changelog comes out empty.

```shell
git switch main && git pull -q
git restore --source=origin/main src/main/resources/recipeDescriptors.yml
./gradlew run
```

Then make sure no module lost recipes it had before:

```shell
count() { awk '/^[^ #]/{a=$1} /^    [^ -]/{c[a]++} END{for(k in c) print k, c[k]}' | LC_ALL=C sort; }
LC_ALL=C join -a1 -a2 -e0 -o 0,1.2,2.2 \
  <(git show origin/main:src/main/resources/recipeDescriptors.yml | count) \
  <(count < src/main/resources/recipeDescriptors.yml) |
  awk '$3 < $2 { print $1, $2, "->", $3 }'
```

Every line printed is a module with fewer recipes than before. A recipe or two removed on purpose shows in the
changelog's removed recipes; a module down to zero, or most of the way, means its loader failed: fix the toolchain,
restore the descriptors and run again.

## 5. Open the changelog pull request in rewrite-docs

```shell
changelog=$(basename build/docs/*-Release.md .md)
git -C ../rewrite-docs switch master && git -C ../rewrite-docs pull -q
git -C ../rewrite-docs switch -c "add-$changelog"
mv "build/docs/$changelog.md" ../rewrite-docs/docs/changelog/
```

Add `'changelog/<changelog>',` as the first of the `items` of the `Changelog` category in `../rewrite-docs/sidebars.ts`,
above the previous release's. Skim the changelog for anything that should not be public, then commit both files and
open a pull request titled `Add the <version> changelog`, such as `Add the 8.94.0 changelog`.

## 6. Open the descriptors pull request here

```shell
git switch -c "update-recipe-descriptors-<version>"
git commit -m "Update recipeDescriptors.yml for <version>" src/main/resources/recipeDescriptors.yml
git push -u origin HEAD
gh pr create --fill --base main
```

Merge it once the changelog pull request is merged, so the next changelog starts where this one ends.

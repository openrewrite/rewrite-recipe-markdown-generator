![Logo](https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss.png)

## What is this?

This project generates the recipe documentation for [docs.openrewrite.org](https://docs.openrewrite.org) and [docs.moderne.io](https://docs.moderne.io):

* **OpenRewrite docs** (`build/docs/`): open-source recipes only, plus the changelog.
* **Moderne docs** (`build/moderne-docs/`): all recipes, including proprietary ones and the whole Go catalog.

## How docs are published

Nobody needs to run this by hand. The `update-docs.yml` workflows in [rewrite-docs](https://github.com/openrewrite/rewrite-docs/blob/master/.github/workflows/update-docs.yml) and [moderne-docs](https://github.com/moderneinc/moderne-docs/blob/main/.github/workflows/update-docs.yml) check out this repository, run it and push the result. Their nightly runs refresh only the latest versions; the Code Genome Project starts full runs after each release train, which regenerate the catalog and, in rewrite-docs, the changelog.

In this repository, [Generate Latest Versions](.github/workflows/ci.yml) and [Generate Per Language](.github/workflows/generate-per-language.yml) catch breakage without a full run, the latter by generating one recipe module per language.

## Running locally

Only needed for troubleshooting.

### Prerequisites

* **Java 21**
* **Code Genome Project credentials**: `codegenomeUsername` and `codegenomePassword` in `~/.gradle/gradle.properties`, since OpenRewrite and Moderne artifacts are no longer published to Maven Central.
* For non-Java recipes, which are loaded over RPC:
  * **Node.js** for TypeScript recipes, with the proprietary `@openrewrite/recipes-code-quality` and `@openrewrite/recipes-date-libraries` installed from the Code Genome Project's npm registry using the same credentials
  * **Python 3.10+ with `pip`** for Python recipes
  * **.NET SDK** for C# recipes
  * **Go 1.25+ and `rewrite-go-rpc`** for Go recipes: `go install github.com/openrewrite/rewrite/rewrite-go/cmd/rpc@latest`, then `ln -s "$(go env GOPATH)/bin/rpc" "$(go env GOPATH)/bin/rewrite-go-rpc"` with `$(go env GOPATH)/bin` on `PATH`.

A run fails when a language it covers loads no recipes, which is what a missing toolchain looks like; leave that language's modules out with `-PrecipeArtifacts` to work without it.

### Commands

```shell
./gradlew run                                                       # everything
./gradlew run -PrecipeArtifacts=rewrite-circleci,rewrite-python     # only these modules, much faster
./gradlew run -PlatestVersionsOnly=true                             # only the latest versions files
./gradlew run --args="--help"                                       # all options
```

To preview the changelog, diff against a copy of its baseline from rewrite-docs, as the run overwrites the file it is given:

```shell
cp ../rewrite-docs/docs/changelog/recipeDescriptors.yml /tmp/
./gradlew run -PrecipeDescriptors=/tmp/recipeDescriptors.yml
```

A run restricted with `-PrecipeArtifacts` writes no changelog, since it sees only part of the catalog.

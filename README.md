![Logo](https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss.png)
## What is this?

This project generates recipe documentation in markdown format for all recipes on the classpath. It produces two separate sets of output:

- **OpenRewrite docs** (`build/docs/`) - Open-source recipes only, for [docs.openrewrite.org](https://docs.openrewrite.org)
- **Moderne docs** (`build/moderne-docs/`) - All recipes including proprietary, for [docs.moderne.io](https://docs.moderne.io)

Proprietary recipes (those with a `Proprietary` license or loaded via TypeScript/Python) are written only to the Moderne docs output. Open-source recipes are written to both.

### Changelog

This project also builds up a CHANGELOG to track what has changed over time. The way this works is that, every time
this project is run, it looks in the `/src/main/resources` directory for a `recipeDescriptors.yml` file.
It will then parse that file and compare it to the latest information obtained. If there are differences,
they will be outlined in a CHANGELOG that will be created in `build/docs`. After the CHANGELOG is built,
the latest information will be stored in the descriptors file for future use.

Once you have the CHANGELOG created, you can copy it over to the [changelog section](https://docs.openrewrite.org/changelog/)
in the OpenRewrite docs.

## Usage

Quickstart:

### Generate all docs
```shell
./gradlew run
```

This writes OpenRewrite docs to `build/docs/` and Moderne docs to `build/moderne-docs/`.

### Create only latest versions files
```shell
./gradlew run -PlatestVersionsOnly=true
cp -r build/docs/*.md ../rewrite-docs/docs/reference/
cp -r build/docs/*.js ../rewrite-docs/src/plugins/
cp -r build/moderne-docs/*.md ../moderne-docs/docs/user-documentation/recipes/
cp -r build/moderne-docs/*.js ../moderne-docs/src/plugins/
```

### Create Markdown files in a specific directory
```shell
./gradlew run --args="desired/output/path"
```

### Print additional options
```shell
./gradlew run --args="--help"
```

### Update rewrite-docs
Assumes you have `rewrite-docs` checked out in the same directory as `rewrite-recipe-markdown-generator`.

```shell
./gradlew run
rm -rf ../rewrite-docs/docs/recipes/
mv build/docs/*-Release.md ../rewrite-docs/docs/changelog/
cp -r build/docs/recipes ../rewrite-docs/docs/recipes
cp -r build/docs/*.md ../rewrite-docs/docs/reference/
cp -r build/docs/*.js ../rewrite-docs/src/plugins/
```

#### Manual step

Update `../rewrite-docs/sidebars.ts` to include a link to the new changelog.

### Update moderne-docs
Assumes you have `moderne-docs` checked out in the same directory as `rewrite-recipe-markdown-generator`.

```shell
./gradlew run
rm -rf ../moderne-docs/docs/user-documentation/recipes/recipe-catalog/
cp -r build/moderne-docs/recipe-catalog ../moderne-docs/docs/user-documentation/recipes/recipe-catalog
cp -r build/moderne-docs/lists/* ../moderne-docs/docs/user-documentation/recipes/
```

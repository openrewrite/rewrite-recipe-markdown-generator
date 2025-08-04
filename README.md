![Logo](https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss.png)
## What is this?

This project implements a utility that generates OpenRewrite recipe documentation in markdown format for all recipes on the classpath.

### Changelog

This project also builds up a CHANGELOG to track what has changed over time. The way this works is that, every time
this project is run, it looks in the `/src/main/resources` directory for a `recipeDescriptors.yml` file.
It will then parse that file and compare it to the latest information obtained. If there are differences,
they will be outlined in a CHANGELOG that will be created in the resources directory. After the CHANGELOG is built,
the latest information will be stored in the descriptors file for future use.

Once you have the CHANGELOG created, you can copy it over to the [changelog section](https://docs.openrewrite.org/changelog/)
in the OpenRewrite docs.

## Usage

Quickstart:

### Create Markdown files in `build/docs`
```shell
./gradlew run
```

### Create only latest versions files in `build/docs`
```shell
./gradlew run -PlatestVersionsOnly=true
cp -r build/docs/*.md ../rewrite-docs/docs/reference/
cp -r build/docs/*.js ../rewrite-docs/src/plugins/
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
cp -r build/docs/recipes ../rewrite-docs/docs/recipes
cp -r build/docs/*.md ../rewrite-docs/docs/reference/
cp -r build/docs/*.js ../rewrite-docs/src/plugins/
cp src/main/resources/8-*-Release.md ../rewrite-docs/docs/changelog/
```

#### Manual step

Update `../rewrite-docs/sidebars.ts` to include a link to the new changelog.

![Logo](https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss.png)
## What is this?

This project implements a utility that generates OpenRewrite recipe documentation in markdown format for all recipes on the classpath.

### Changelog

This project also builds up a CHANGELOG to track what has changed over time. The way this works is that, every time
this project is run, it looks in the `/src/main/resources` directory for either a `recipeDescriptors.yml` or a
`snapshotRecipeDescriptors.yml` file (depending on whether this is being run for an OpenRewrite release or a weekly 
snapshot). It will then parse that file and compare it to the latest information obtained. If there are differences,
they will be outlined in a CHANGELOG that will be created in the resources directory. After the CHANGELOG is built,
the latest information will be stored in the descriptors file for future use.

By default, the assumption is that this is being run for an OpenRewrite release. If you'd like to run it for a snapshot
release, you'll need to modify the `build.gradle.kts` file to change `rewriteVersion` to whatever version you want
(such as `latest.integration`) and `deployType` to `snapshot`.

Once you have the CHANGELOG created, you can copy it over to the [changelog section](https://docs.openrewrite.org/changelog/)
in the OpenRewrite docs.

## Usage

Quickstart:

```sh
# Will place generated docs into build/docs
./gradlew run

# Will place generated docs into the specified directory
./gradlew run --args="desired/output/path"

# or, generally:
./gradlew run --args="--help"
```

## Known issues

Recipes that do not have an organization such as `org.openrewrite.DeleteSourceFiles` 
(as compared to `org.openrewrite.circleci.InstallOrb`) result in the `SUMMARY_snippet` categorizing them incorrectly. 
See [here](https://moderneinc.slack.com/archives/C01VADFPJQZ/p1669756621584369) for more information.
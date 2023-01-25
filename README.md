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
in the OpenRewrite docs. When doing a full release, make sure you remove the old snapshot releases.

A few important things to note:

* The snapshot descriptors are not updated when a full release is run. You should make sure to run the snapshot code when you do a full release so that it can be kept up-to-date.
* When doing snapshot releases, you generally don't want to copy over all of the files to the rewrite-docs (as it will show snapshot versions instead of full versions). Rather, please only copy over the exact files you need.
* When copying over the files from the previous step, make sure you update the `SUMMARY.md`. If you don't, those new files won't appear.

### Automated Recipe Docs

When you run this project for either a snapshot or a full release, all documentation will be updated in the 
`build/docs` directory. There are two key pieces to that: the `reference` folder and the `SUMMARY_snippet.md` file. 

If you wish to update the [docs](https://docs.openrewrite.org/reference/recipes), you should replace [this directory](https://github.com/openrewrite/rewrite-docs/tree/master/reference/recipes)
with the `reference/recipe` folder generated here. After that, you should update the [SUMMARY.md](https://github.com/openrewrite/rewrite-docs/blob/master/SUMMARY.md?plain=1#L53-L1066)
with the snippet generated in the `SUMMARY_snippet.md` file.

Please note that for snapshot releases, you should not copy over all the generated files. This is because we want
the docs to generally only show full release information. Rather, you should copy over the specific new docs you want
(as well as the updates to the `SUMMARY_snippet.md`).

Also note that this does not cover _all_ of the documentation that needs to be updated with a release. There are still
many docs such as the [latest versions](https://docs.openrewrite.org/reference/latest-versions-of-every-openrewrite-module) doc
that needs to be updated manually.

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
## Known Issues

The `SUMMARY_snippet.md` file generated does not entirely match the one in the `rewrite-docs` repository. This is 
because GitBook makes some strange changes at time automatically. This hasn't been investigated, yet.
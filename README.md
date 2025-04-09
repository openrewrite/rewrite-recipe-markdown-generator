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

### Diff log

It's possible to configure the markdown generator to create a diff log.
That doc shows all of the recipes created since a particular version of OpenRewrite. Companies may want to see the work
we've done for them since they've signed on. In order to make this log, please follow these steps:

1. Determine what version you want to start from. For instance, OpenRewrite version `7.24.0`.
2. Once you have that, find the version for all artifacts that correspond to that release. For example, if OpenRewrite is version `7.24.0`, then `rewrite-circleci` would be `1.8.0` and `rewrite-spring` would be `4.22.1`.
3. Update the `build.gradle.kts` file to include all of those old versions. You can see an example of this and some key notes in the `old-build.gradle-example.kts` file provided in the `src` directory.
4. Run the markdown generator with `snapshot` specified as the deploy type and all of the versions specified. This will update the `snapshotRecipeDescriptors.yml` file with all of the details from that version.
5. Copy the `snapshotRecipeDescriptors.yml` file into a new file for safe keeping. This file will be used as a backup so you don't need to do the above steps every time you wish to regenerate the log.
6. Revert all changes to the `build.gradle.kts` file you made above.
7. Specify `diff` as the deploy type in the `build.gradle.kts` file, specify the `diffFileName` as the name of the company you're making this for, specify `latest.integration` for the `rewriteVersion`, and copy over the snapshot descriptors file you made earlier into the `diffRecipeDescriptors.yml` file.
8. Run the markdown generator with the above configurations. You should now see a file called `diffFileName.md` that shows all of the recipes created between the old version and the latest snapshot.
9. If you need to re-run or change the diff file, copy over the snapshot descriptors file you saved into the `diffRecipeDescriptors.yml` file and re-run the generator.

Note: It's possible that with old versions of Rewrite that the markdown generator might not compile. I added a comment in the code that shows what one of the lines need to change to with some older versions.

### Automated Recipe Docs

When you run this project for either a snapshot or a full release, all documentation will be updated in the 
`build/docs` directory.

Note that this does not cover _all_ of the documentation that needs to be updated with a release. There are still
others docs that needs to be updated manually, in particular around versions used in popular recipe guides.

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

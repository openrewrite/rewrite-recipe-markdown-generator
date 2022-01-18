![Logo](https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss.png)
### What is this?

This project implements a utility that generates OpenRewrite recipe documentation in markdown format for all recipes on the classpath.

### Usage

Quickstart:

```sh
# Will place generated docs into build/docs
./gradlew run

# Will place generated docs into the specified directory
./gradlew run --args="desired/output/path"

# or, generally:
./gradlew run --args="--help"
```

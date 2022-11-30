## New Recipes
* [org.openrewrite.java.spring.boot2.MigrateSpringBoot_2_0](https://docs.openrewrite.org/reference/recipes/java/spring/boot2/migratespringboot_2_0): Migrate applications built on Spring Boot 1.5 to the latest Spring Boot 2.0 release. This recipe will modify an application's build files, make changes to deprecated/preferred APIs, and migrate configuration settings that have changes between versions. This recipe will also chain additional framework migrations (Spring Framework, Spring Data, etc) that are required as part of the migration to Spring Boot 2.0.

## Removed Recipes
* **org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_0**: Upgrade to Spring Boot 2.0 from prior 1.x version. 

## Changed Recipes
* [org.openrewrite.maven.RemoveRedundantDependencyVersions](https://docs.openrewrite.org/reference/recipes/maven/removeredundantdependencyversions) was changed:
  * Old Options:
    * `artifactPattern: { type: String, required: false }`
    * `groupPattern: { type: String, required: false }`
    * `onlyIfVersionsMatch: { type: Boolean, required: false }`
  * New Options:
    * `artifactPattern: { type: String, required: false }`
    * `except: { type: List, required: false }`
    * `groupPattern: { type: String, required: false }`
    * `onlyIfVersionsMatch: { type: Boolean, required: false }`
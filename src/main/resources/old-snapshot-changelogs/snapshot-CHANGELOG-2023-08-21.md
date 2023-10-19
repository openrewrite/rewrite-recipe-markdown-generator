# Snapshot (2023-08-21)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.hibernate.TypeAnnotationParameter](https://docs.openrewrite.org/reference/recipes/hibernate/typeannotationparameter): Hibernate 6.x has 'type' parameter of type String replaced with 'value' of type class. 
* [org.openrewrite.java.migrate.apache.commons.lang.ApacheCommonsStringUtilsRecipes](https://docs.openrewrite.org/reference/recipes/java/migrate/apache/commons/lang/apachecommonsstringutilsrecipes): Refaster template recipes for `org.openrewrite.java.migrate.apache.commons.lang.ApacheCommonsStringUtils`. 
* [org.openrewrite.java.migrate.plexus.PlexusFileUtilsRecipes](https://docs.openrewrite.org/reference/recipes/java/migrate/plexus/plexusfileutilsrecipes): Refaster template recipes for `org.openrewrite.java.migrate.plexus.PlexusFileUtils`. 
* [org.openrewrite.java.security.search.FindSensitiveApiEndpoints](https://docs.openrewrite.org/reference/recipes/java/security/search/findsensitiveapiendpoints): Find data models exposed by REST APIs that contain sensitive information like PII and secrets. 
* [org.openrewrite.java.spring.batch.RemoveDefaultBatchConfigurer](https://docs.openrewrite.org/reference/recipes/java/spring/batch/removedefaultbatchconfigurer): Remove `extends DefaultBatchConfigurer` and `@Override` from associated methods. 
* [org.openrewrite.java.spring.boot2.HeadersConfigurerLambdaDsl](https://docs.openrewrite.org/reference/recipes/java/spring/boot2/headersconfigurerlambdadsl): Converts `HeadersConfigurer` chained call from Spring Security pre 5.2.x into new lambda DSL style calls and removes `and()` methods. 
* [org.openrewrite.java.spring.http.SimplifyMediaTypeParseCalls](https://docs.openrewrite.org/reference/recipes/java/spring/http/simplifymediatypeparsecalls): Replaces `MediaType.parseMediaType('application/json')` and `MediaType.valueOf('application/json') with `MediaType.APPLICATION_JSON`. 
* [org.openrewrite.java.spring.security5.UpgradeSpringSecurity_5_7](https://docs.openrewrite.org/reference/recipes/java/spring/security5/upgradespringsecurity_5_7): Migrate applications to the latest Spring Security 5.7 release. This recipe will modify an application's build files, make changes to deprecated/preferred APIs, and migrate configuration settings that have changes between versions. 
* [org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_1](https://docs.openrewrite.org/reference/recipes/java/spring/security6/upgradespringsecurity_6_1): Migrate applications to the latest Spring Security 6.1 release. This recipe will modify an application's build files, make changes to deprecated/preferred APIs, and migrate configuration settings that have changes between versions. 
* [org.openrewrite.jenkins.github.AddTeamToCodeowners](https://docs.openrewrite.org/reference/recipes/jenkins/github/addteamtocodeowners): Adds the `{artifactId}-plugin-developers` team to all files in `.github/CODEOWNERS` if absent. 
* [org.openrewrite.kotlin.cleanup.RemoveTrailingSemicolon](https://docs.openrewrite.org/reference/recipes/kotlin/cleanup/removetrailingsemicolon): Some Java programmers may mistakenly add semicolons at the end when writing Kotlin code, but in reality, they are not necessary. 
* [org.openrewrite.maven.search.EffectiveDependencies](https://docs.openrewrite.org/reference/recipes/maven/search/effectivedependencies): Emit the data of binary dependency relationships. 
* [org.openrewrite.maven.search.EffectiveManagedDependencies](https://docs.openrewrite.org/reference/recipes/maven/search/effectivemanageddependencies): Emit the data of binary dependency relationships. 
* [org.openrewrite.maven.search.FindMavenSettings](https://docs.openrewrite.org/reference/recipes/maven/search/findmavensettings): List the effective maven settings file for the current project. 


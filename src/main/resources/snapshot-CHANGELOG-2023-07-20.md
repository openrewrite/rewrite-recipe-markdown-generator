# Snapshot (2023-07-20)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.github.AddCronTrigger](https://docs.openrewrite.org/reference/recipes/github/addcrontrigger): The `schedule` [event](https://docs.github.com/en/actions/reference/events-that-trigger-workflows#scheduled-events) allows you to trigger a workflow at a scheduled time. 
* [org.openrewrite.gradle.plugins.ChangePlugin](https://docs.openrewrite.org/reference/recipes/gradle/plugins/changeplugin): Changes the selected Gradle plugin to the new plugin. 
* [org.openrewrite.hibernate.MigrateToHibernate62](https://docs.openrewrite.org/reference/recipes/hibernate/migratetohibernate62): This recipe will apply changes commonly needed when migrating to Hibernate 6.2.x. 
* [org.openrewrite.hibernate.MigrateToHypersistenceUtilsHibernate6.0](https://docs.openrewrite.org/reference/recipes/hibernate/migratetohypersistenceutilshibernate6/0): This recipe will migrate any existing dependencies on `com.vladmihalcea:hibernate-types` to `io.hypersistence:hypersistence-utils-hibernate-60`.  This migration will include the adjustment from `com.vladmihalcea` to `io.hypersistence.utils` package name. 
* [org.openrewrite.hibernate.MigrateToHypersistenceUtilsHibernate6.2](https://docs.openrewrite.org/reference/recipes/hibernate/migratetohypersistenceutilshibernate6/2): This recipe will migrate any existing dependencies on `io.hypersistence:hypersistence-utils-hibernate-60` to `io.hypersistence:hypersistence-utils-hibernate-62`. 
* [org.openrewrite.java.logging.slf4j.ChangeLogLevel](https://docs.openrewrite.org/reference/recipes/java/logging/slf4j/changeloglevel): Change the log level of slf4j log statements. 
* [org.openrewrite.java.micronaut.AddHttpRequestTypeParameter](https://docs.openrewrite.org/reference/recipes/java/micronaut/addhttprequesttypeparameter): Add an `HttpRequest` type parameter to a class `implements` statement for interfaces that have been generically parameterized where they previously specified `HttpRequest` explicitly. 
* [org.openrewrite.java.micronaut.AddTestResourcesClientDependencyIfNeeded](https://docs.openrewrite.org/reference/recipes/java/micronaut/addtestresourcesclientdependencyifneeded): This recipe adds the Test Resources Client dependency to pom.xml if test.resources.client.enabled property is true. 
* [org.openrewrite.java.micronaut.RemoveAnnotationProcessorPath](https://docs.openrewrite.org/reference/recipes/java/micronaut/removeannotationprocessorpath): Remove the Maven annotation processor path that matches the given groupId and artifactId. 
* [org.openrewrite.java.micronaut.RemoveUnnecessaryDependencies](https://docs.openrewrite.org/reference/recipes/java/micronaut/removeunnecessarydependencies): This recipe will remove dependencies that are no longer explicitly needed. 
* [org.openrewrite.java.micronaut.UpdateMavenAnnotationProcessors](https://docs.openrewrite.org/reference/recipes/java/micronaut/updatemavenannotationprocessors): This recipe will update the version of Maven-configured annotation processors from Micronaut Core. 
* [org.openrewrite.java.spring.framework.UpgradeSpringFrameworkDependencies](https://docs.openrewrite.org/reference/recipes/java/spring/framework/upgradespringframeworkdependencies): Upgrade spring-framework 5.x Maven dependencies using a Node Semver advanced range selector. 
* [org.openrewrite.java.testing.hamcrest.FlattenAllOf](https://docs.openrewrite.org/reference/recipes/java/testing/hamcrest/flattenallof): Convert Hamcrest `allOf(Matcher...)` to individual `assertThat` statements for easier migration. 
* [org.openrewrite.jenkins.migrate.hudson.UtilGetPastTimeStringToGetTimeSpanString](https://docs.openrewrite.org/reference/recipes/jenkins/migrate/hudson/utilgetpasttimestringtogettimespanstring): `hudson.Util.getPastTimeString` has been [deprecated](https://github.com/jenkinsci/jenkins/pull/4174) since the [2.204.1 LTS release](https://www.jenkins.io/changelog-stable/#v2.204.1) on 2019-12-18. 
* [org.openrewrite.staticanalysis.ReplaceWeekYearWithYear](https://docs.openrewrite.org/reference/recipes/staticanalysis/replaceweekyearwithyear): For most dates Week Year (YYYY) and Year (yyyy) yield the same results. However, on the last week of December and first week of January Week Year could produce unexpected results. 
* [org.openrewrite.staticanalysis.SimplifyBooleanExpression](https://docs.openrewrite.org/reference/recipes/staticanalysis/simplifybooleanexpression): Checks for over-complicated boolean expressions. Finds code like `if (b == true)`, `b || true`, `!false`, etc. 
* [org.openrewrite.staticanalysis.SimplifyBooleanReturn](https://docs.openrewrite.org/reference/recipes/staticanalysis/simplifybooleanreturn): Simplifies Boolean expressions by removing redundancies, e.g.: `a && true` simplifies to `a`. 
* [org.openrewrite.staticanalysis.UnnecessaryParentheses](https://docs.openrewrite.org/reference/recipes/staticanalysis/unnecessaryparentheses): Removes unnecessary parentheses from code where extra parentheses pairs are redundant. 

## Changed Recipes

* [org.openrewrite.gradle.UpgradeDependencyVersion](https://docs.openrewrite.org/reference/recipes/gradle/upgradedependencyversion) was changed:
  * Old Options:
    * `artifactId: { type: String, required: true }`
    * `groupId: { type: String, required: true }`
    * `newVersion: { type: String, required: true }`
    * `versionPattern: { type: String, required: false }`
  * New Options:
    * `artifactId: { type: String, required: true }`
    * `groupId: { type: String, required: true }`
    * `newVersion: { type: String, required: false }`
    * `versionPattern: { type: String, required: false }`
* [org.openrewrite.gradle.plugins.AddGradleEnterpriseGradlePlugin](https://docs.openrewrite.org/reference/recipes/gradle/plugins/addgradleenterprisegradleplugin) was changed:
  * Old Options:
    * `allowUntrustedServer: { type: Boolean, required: false }`
    * `captureTaskInputFiles: { type: Boolean, required: false }`
    * `publishCriteria: { type: PublishCriteria, required: false }`
    * `server: { type: String, required: false }`
    * `uploadInBackground: { type: Boolean, required: false }`
    * `version: { type: String, required: true }`
  * New Options:
    * `allowUntrustedServer: { type: Boolean, required: false }`
    * `captureTaskInputFiles: { type: Boolean, required: false }`
    * `publishCriteria: { type: PublishCriteria, required: false }`
    * `server: { type: String, required: false }`
    * `uploadInBackground: { type: Boolean, required: false }`
    * `version: { type: String, required: false }`
* [org.openrewrite.gradle.plugins.AddSettingsPlugin](https://docs.openrewrite.org/reference/recipes/gradle/plugins/addsettingsplugin) was changed:
  * Old Options:
    * `pluginId: { type: String, required: true }`
    * `version: { type: String, required: true }`
    * `versionPattern: { type: String, required: false }`
  * New Options:
    * `pluginId: { type: String, required: true }`
    * `version: { type: String, required: false }`
    * `versionPattern: { type: String, required: false }`
* [org.openrewrite.gradle.plugins.UpgradePluginVersion](https://docs.openrewrite.org/reference/recipes/gradle/plugins/upgradepluginversion) was changed:
  * Old Options:
    * `newVersion: { type: String, required: true }`
    * `pluginIdPattern: { type: String, required: true }`
    * `versionPattern: { type: String, required: false }`
  * New Options:
    * `newVersion: { type: String, required: false }`
    * `pluginIdPattern: { type: String, required: true }`
    * `versionPattern: { type: String, required: false }`
* [org.openrewrite.java.RemoveImplements](https://docs.openrewrite.org/reference/recipes/java/removeimplements) was changed:
  * Old Options:
    * `filter: { type: String, required: true }`
    * `interfaceType: { type: String, required: true }`
  * New Options:
    * `filter: { type: String, required: false }`
    * `interfaceType: { type: String, required: true }`
* [org.openrewrite.java.ReorderMethodArguments](https://docs.openrewrite.org/reference/recipes/java/reordermethodarguments) was changed:
  * Old Options:
    * `ignoreDefinition: { type: Boolean, required: false }`
    * `methodPattern: { type: String, required: true }`
    * `newParameterNames: { type: String[], required: true }`
    * `oldParameterNames: { type: String[], required: false }`
  * New Options:
    * `ignoreDefinition: { type: Boolean, required: false }`
    * `matchOverrides: { type: Boolean, required: false }`
    * `methodPattern: { type: String, required: true }`
    * `newParameterNames: { type: String[], required: true }`
    * `oldParameterNames: { type: String[], required: false }`
* [org.openrewrite.java.SimplifyMethodChain](https://docs.openrewrite.org/reference/recipes/java/simplifymethodchain) was changed:
  * Old Options:
    * `methodPatternChain: { type: List, required: true }`
    * `newMethodName: { type: String, required: true }`
  * New Options:
    * `matchOverrides: { type: Boolean, required: false }`
    * `methodPatternChain: { type: List, required: true }`
    * `newMethodName: { type: String, required: true }`
* [org.openrewrite.java.search.ResultOfMethodCallIgnored](https://docs.openrewrite.org/reference/recipes/java/search/resultofmethodcallignored) was changed:
  * Old Options:
    * `methodPattern: { type: String, required: true }`
  * New Options:
    * `matchOverrides: { type: Boolean, required: false }`
    * `methodPattern: { type: String, required: true }`
* [org.openrewrite.java.spring.amqp.UseTlsAmqpConnectionString](https://docs.openrewrite.org/reference/recipes/java/spring/amqp/usetlsamqpconnectionstring) was changed:
  * Old Options:
    * `oldPort: { type: Integer, required: true }`
    * `pathExpressions: { type: List, required: false }`
    * `port: { type: Integer, required: true }`
    * `propertyKey: { type: String, required: true }`
    * `tlsPropertyKey: { type: String, required: true }`
  * New Options:
    * `oldPort: { type: Integer, required: true }`
    * `pathExpressions: { type: List, required: false }`
    * `port: { type: Integer, required: true }`
    * `propertyKey: { type: String, required: false }`
    * `tlsPropertyKey: { type: String, required: false }`
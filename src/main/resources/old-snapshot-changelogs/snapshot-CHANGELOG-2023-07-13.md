# Snapshot (2023-07-13)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Artifacts
* rewrite-jenkins

## New Recipes

* [org.openrewrite.gradle.plugins.AddGradleEnterpriseGradlePlugin](https://docs.openrewrite.org/reference/recipes/gradle/plugins/addgradleenterprisegradleplugin): Add the Gradle Enterprise Gradle plugin to settings.gradle files. 
* [org.openrewrite.java.logging.slf4j.ChangeLogLevel](https://docs.openrewrite.org/reference/recipes/java/logging/slf4j/changeloglevel): Change the log level of slf4j log statements. 
* [org.openrewrite.java.micronaut.AddAnnotationProcessorPath](https://docs.openrewrite.org/reference/recipes/java/micronaut/addannotationprocessorpath): Add the groupId, artifactId, version, and exclusions of a Maven annotation processor path. 
* [org.openrewrite.java.micronaut.RemoveWithJansiLogbackConfiguration](https://docs.openrewrite.org/reference/recipes/java/micronaut/removewithjansilogbackconfiguration): This recipe will remove the withJansi configuration tag from logback.xml. 
* [org.openrewrite.java.micronaut.UpdateBlockingTaskExecutors](https://docs.openrewrite.org/reference/recipes/java/micronaut/updateblockingtaskexecutors): This recipe will any usage of TaskExecutors.IO to TaskExecutors.BLOCKING in order to be compatible with virtual threads. 
* [org.openrewrite.java.micronaut.UpdateMicronautData](https://docs.openrewrite.org/reference/recipes/java/micronaut/updatemicronautdata): This recipe will make the necessary updates for using Micronaut Data with Micronaut Framework 4. 
* [org.openrewrite.java.migrate.javax.AddScopeToInjectedClass](https://docs.openrewrite.org/reference/recipes/java/migrate/javax/addscopetoinjectedclass): Finds member variables annotated with `@Inject' and applies `@Dependent` scope annotation to the variable's type. 
* [org.openrewrite.java.migrate.lang.var.UseVarForGenericMethodInvocations](https://docs.openrewrite.org/reference/recipes/java/migrate/lang/var/usevarforgenericmethodinvocations): Apply `var` to variables initialized by invocations of Generic Methods. This recipe ignores generic factory methods without parameters, because open rewrite cannot handle them correctly ATM. 
* [org.openrewrite.java.migrate.lang.var.UseVarForGenericsConstructors](https://docs.openrewrite.org/reference/recipes/java/migrate/lang/var/usevarforgenericsconstructors): Apply `var` to generics variables initialized by constructor calls. 
* [org.openrewrite.java.testing.junit5.AssertTrueInstanceofToAssertInstanceOf](https://docs.openrewrite.org/reference/recipes/java/testing/junit5/asserttrueinstanceoftoassertinstanceof): Migration of JUnit4 (or potentially JUnit5) test case in form of assertTrue(x instanceof y) to assertInstanceOf(y.class, x). 
* [org.openrewrite.jenkins.AddPluginsBom](https://docs.openrewrite.org/reference/recipes/jenkins/addpluginsbom): Adds [Jenkins plugins BOM](https://www.jenkins.io/doc/developer/plugin-development/dependency-management/#jenkins-plugin-bom) at the latest release if the project depends on any managed versions. BOMs are expected to be synchronized to Jenkins LTS versions, so this will also remove any mismatched BOMs (Such as using Jenkins 2.387.3, but importing bom-2.319.x). If the expected BOM is already added, the version will not be upgraded. 
* [org.openrewrite.jenkins.CommonsLang3ToApiPlugin](https://docs.openrewrite.org/reference/recipes/jenkins/commonslang3toapiplugin): Updates `pom.xml` to depend on `commons-lang3-api` and exclude `commons-lang3` where it is brought in transitively. 
* [org.openrewrite.jenkins.CreateIndexJelly](https://docs.openrewrite.org/reference/recipes/jenkins/createindexjelly): Jenkins tooling [requires](https://github.com/jenkinsci/maven-hpi-plugin/pull/302) `src/main/resources/index.jelly` exists with a description. 
* [org.openrewrite.jenkins.DisableLocalResolutionForParentPom](https://docs.openrewrite.org/reference/recipes/jenkins/disablelocalresolutionforparentpom): Explicitly sets `<relativePath/>` to disable file resolution, as recommended in the [plugin development guide](https://www.jenkins.io/doc/developer/plugin-development/updating-parent/). 
* [org.openrewrite.jenkins.IsJenkinsPlugin](https://docs.openrewrite.org/reference/recipes/jenkins/isjenkinsplugin): Checks if the project is a Jenkins plugin by the presence of a managed version of jenkins-core 
* [org.openrewrite.jenkins.JavaxAnnotationsToSpotbugs](https://docs.openrewrite.org/reference/recipes/jenkins/javaxannotationstospotbugs): SpotBugs is the [preferred replacement](https://www.jenkins.io/doc/developer/tutorial-improve/replace-jsr-305-annotations/) of JSR-305 annotations for Jenkins plugins. 
* [org.openrewrite.jenkins.ModernizeJenkinsfile](https://docs.openrewrite.org/reference/recipes/jenkins/modernizejenkinsfile): Updates `Jenkinsfile` to build with recommended Java versions, platforms, and settings. 
* [org.openrewrite.jenkins.ModernizePlugin](https://docs.openrewrite.org/reference/recipes/jenkins/modernizeplugin): This recipe is intended to change over time to reflect the most recent tooling and [recommended Jenkins baseline](https://www.jenkins.io/doc/developer/plugin-development/choosing-jenkins-baseline/). 
* [org.openrewrite.jenkins.ModernizePluginForJava8](https://docs.openrewrite.org/reference/recipes/jenkins/modernizepluginforjava8): This recipe is intended to break down the modernization of very old plugins into distinct steps. It allows modernizing all tooling up to the last versions that supported Java 8. This can then be followed by another recipe that makes the jump to Java 11. 
* [org.openrewrite.jenkins.ReplaceLibrariesWithApiPlugin](https://docs.openrewrite.org/reference/recipes/jenkins/replacelibrarieswithapiplugin): Prefer Jenkins API plugins over bundling libraries for slimmer plugins. 
* [org.openrewrite.jenkins.UpgradeHtmlUnit_3_3_0](https://docs.openrewrite.org/reference/recipes/jenkins/upgradehtmlunit_3_3_0): Automates the HtmlUnit [migration guide](https://htmlunit.sourceforge.io/migration.html) from 2.x to 3.x. This change was brought in by [parent 4.66](https://github.com/jenkinsci/plugin-pom/releases/tag/plugin-4.66). 
* [org.openrewrite.jenkins.UpgradeVersionProperty](https://docs.openrewrite.org/reference/recipes/jenkins/upgradeversionproperty): If the current value is < given version, upgrade it. 
* [org.openrewrite.maven.search.FindRepositoryOrder](https://docs.openrewrite.org/reference/recipes/maven/search/findrepositoryorder): Determine the order in which dependencies will be resolved for each `pom.xml` based on its defined repositories and effective `settings.xml`. 
* [org.openrewrite.search.FindBuildMetadata](https://docs.openrewrite.org/reference/recipes/search/findbuildmetadata): Find source files with matching build metadata. 

## Removed Recipes

* **org.openrewrite.gradle.plugins.AddGradleEnterprise**: Add the Gradle Enterprise plugin to settings.gradle files. 
* **org.openrewrite.java.testing.mockito.UsesMockitoAll**: Finds projects that depend on `mockito-all` through Maven or Gradle. 

## Changed Recipes

* [org.openrewrite.FindParseFailures](https://docs.openrewrite.org/reference/recipes/findparsefailures) was changed:
  * Old Options:
    * `maxSnippetLength: { type: Integer, required: false }`
  * New Options:
    * `maxSnippetLength: { type: Integer, required: false }`
    * `parserType: { type: String, required: false }`
    * `stackTrace: { type: String, required: false }`
* [org.openrewrite.text.Find](https://docs.openrewrite.org/reference/recipes/text/find) was changed:
  * Old Options:
    * `caseInsensitive: { type: Boolean, required: false }`
    * `dotAll: { type: Boolean, required: false }`
    * `filePattern: { type: String, required: true }`
    * `find: { type: String, required: true }`
    * `multiline: { type: Boolean, required: false }`
    * `regex: { type: Boolean, required: false }`
  * New Options:
    * `caseSensitive: { type: Boolean, required: false }`
    * `dotAll: { type: Boolean, required: false }`
    * `filePattern: { type: String, required: true }`
    * `find: { type: String, required: true }`
    * `multiline: { type: Boolean, required: false }`
    * `regex: { type: Boolean, required: false }`
* [org.openrewrite.text.FindAndReplace](https://docs.openrewrite.org/reference/recipes/text/findandreplace) was changed:
  * Old Options:
    * `caseInsensitive: { type: Boolean, required: false }`
    * `dotAll: { type: Boolean, required: false }`
    * `filePattern: { type: String, required: true }`
    * `find: { type: String, required: true }`
    * `multiline: { type: Boolean, required: false }`
    * `regex: { type: Boolean, required: false }`
    * `replace: { type: String, required: true }`
  * New Options:
    * `caseSensitive: { type: Boolean, required: false }`
    * `dotAll: { type: Boolean, required: false }`
    * `filePattern: { type: String, required: true }`
    * `find: { type: String, required: true }`
    * `multiline: { type: Boolean, required: false }`
    * `regex: { type: Boolean, required: false }`
    * `replace: { type: String, required: true }`
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
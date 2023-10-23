# Snapshot (2023-10-23)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.gradle.plugins.AddDevelocityGradlePlugin](https://docs.openrewrite.org/recipes/gradle/plugins/adddevelocitygradleplugin): Add the Develocity Gradle plugin to settings.gradle files. 
* [org.openrewrite.java.migrate.UseTabsOrSpaces](https://docs.openrewrite.org/recipes/java/migrate/usetabsorspaces): This is useful for one-off migrations of a codebase that has mixed indentation styles, while preserving all other auto-detected formatting rules. 
* [org.openrewrite.java.migrate.guava.NoGuavaJava21](https://docs.openrewrite.org/recipes/java/migrate/guava/noguavajava21): Guava filled in important gaps in the Java standard library and still does. But at least some of Guava's API surface area is covered by the Java standard library now, and some projects may be able to remove Guava altogether if they migrate to standard library for these functions. 
* [org.openrewrite.java.migrate.guava.PreferMathClamp](https://docs.openrewrite.org/recipes/java/migrate/guava/prefermathclamp): Prefer `java.lang.Math#clamp` instead of using `com.google.common.primitives.*#constrainToRange`. 
* [org.openrewrite.java.migrate.jakarta.RemovedStateManagerMethods](https://docs.openrewrite.org/recipes/java/migrate/jakarta/removedstatemanagermethods): Methods that were removed from the `jakarta.faces.application.StateManager` and `javax.faces.application.StateManager` classes in Jakarta Faces 4.0 are replaced by `jakarta.faces.view.StateManagementStrategy` or `javax.faces.view.StateManagementStrategy` based on Jakarta10 migration in Faces 4.0 
* [org.openrewrite.java.security.ImproperPrivilegeManagement](https://docs.openrewrite.org/recipes/java/security/improperprivilegemanagement): Marking code as privileged enables a piece of trusted code to temporarily enable access to more resources than are available directly to the code that called it. 
* [org.openrewrite.java.security.OwaspA04](https://docs.openrewrite.org/recipes/java/security/owaspa04): OWASP [A04:2021](https://owasp.org/Top10/A04_2021-Insecure_Design/) focuses on risks related to design and architectural flaws,  with a call for more use of threat modeling, secure design patterns, and reference architectures. This recipe seeks to remediate these vulnerabilities. 
* [org.openrewrite.java.security.marshalling.InsecureJmsDeserialization](https://docs.openrewrite.org/recipes/java/security/marshalling/insecurejmsdeserialization): JMS `Object` messages depend on Java Serialization for marshalling/unmarshalling of the message payload when `ObjectMessage#getObject` is called. Deserialization of untrusted data can lead to security flaws. 
* [org.openrewrite.java.testing.jmockit.JMockitExpectationsToMockitoWhen](https://docs.openrewrite.org/recipes/java/testing/jmockit/jmockitexpectationstomockitowhen): Rewrites JMockit `Expectations` to `Mockito.when`. 
* [org.openrewrite.java.testing.jmockit.JMockitToMockito](https://docs.openrewrite.org/recipes/java/testing/jmockit/jmockittomockito): This recipe will apply changes commonly needed when migrating from JMockit to Mockito. 
* [org.openrewrite.maven.AddDevelocityMavenExtension](https://docs.openrewrite.org/recipes/maven/adddevelocitymavenextension): To integrate the Develocity Maven extension into Maven projects, ensure that the `gradle-enterprise-maven-extension` is added to the `.mvn/extensions.xml` file if not already present. Additionally, configure the extension by adding the `.mvn/gradle-enterprise.xml` configuration file. 
* [org.openrewrite.staticanalysis.TernaryOperatorsShouldNotBeNested](https://docs.openrewrite.org/recipes/staticanalysis/ternaryoperatorsshouldnotbenested): Nested ternary operators can be hard to read quickly. Prefer simpler constructs for improved readability. If supported, this recipe will try to replace nested ternaries with switch expressions. 

## Removed Recipes

* **org.openrewrite.gradle.plugins.AddGradleEnterpriseGradlePlugin**: Add the Gradle Enterprise Gradle plugin to settings.gradle files. 
* **org.openrewrite.maven.AddGradleEnterpriseMavenExtension**: To integrate Gradle Enterprise Maven extension into maven projects, ensure that the `gradle-enterprise-maven-extension` is added to the `.mvn/extensions.xml` file if not already present. Additionally, configure the extension by adding the `.mvn/gradle-enterprise.xml` configuration file. 
* **org.openrewrite.staticanalysis.TestRecipe**: for testing nonsense 


# Snapshot (2023-09-22)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.gradle.ChangeExtraProperty](https://docs.openrewrite.org/recipes/gradle/changeextraproperty): Gradle's [ExtraPropertiesExtension](https://docs.gradle.org/current/dsl/org.gradle.api.plugins.ExtraPropertiesExtension.html) is a commonly used mechanism for setting arbitrary key/value pairs on a project. This recipe will change the value of a property with the given key name if that key can be found. It assumes that the value being set is a String literal. Does not add the value if it does not already exist. 
* [org.openrewrite.java.migrate.ChangeMethodInvocationReturnType](https://docs.openrewrite.org/recipes/java/migrate/changemethodinvocationreturntype): Changes the return type of a method invocation. 
* [org.openrewrite.java.migrate.DeprecatedJavaxSecurityCert](https://docs.openrewrite.org/recipes/java/migrate/deprecatedjavaxsecuritycert): The `javax.security.cert` package has been deprecated for removal. 
* [org.openrewrite.java.migrate.DeprecatedLogRecordThreadID](https://docs.openrewrite.org/recipes/java/migrate/deprecatedlogrecordthreadid): Avoid using the deprecated methods in `java.util.logging.LogRecord` 
* [org.openrewrite.java.migrate.JavaVersion21](https://docs.openrewrite.org/recipes/java/migrate/javaversion21): Change maven.compiler.source and maven.compiler.target values to 21. 
* [org.openrewrite.java.migrate.Jre17AgentMainPreMainPublic](https://docs.openrewrite.org/recipes/java/migrate/jre17agentmainpremainpublic): Check for a behavior change in Java agents. 
* [org.openrewrite.java.migrate.RemovedLegacySunJSSEProviderName](https://docs.openrewrite.org/recipes/java/migrate/removedlegacysunjsseprovidername): The `com.sun.net.ssl.internal.ssl.Provider` provider name was removed. 
* [org.openrewrite.java.migrate.ReplaceStringLiteralValue](https://docs.openrewrite.org/recipes/java/migrate/replacestringliteralvalue): Replace the value of a complete `String` literal. 
* [org.openrewrite.java.migrate.UpgradeToJava21](https://docs.openrewrite.org/recipes/java/migrate/upgradetojava21): This recipe will apply changes commonly needed when migrating to Java 21. This recipe will also replace deprecated API with equivalents when there is a clear migration strategy. Build files will also be updated to use Java 21 as the target/source and plugins will be also be upgraded to versions that are compatible with Java 21. 
* [org.openrewrite.java.migrate.guava.NoGuavaJava11](https://docs.openrewrite.org/recipes/java/migrate/guava/noguavajava11): Guava filled in important gaps in the Java standard library and still does. But at least some of Guava's API surface area is covered by the Java standard library now, and some projects may be able to remove Guava altogether if they migrate to standard library for these functions. 
* [org.openrewrite.java.migrate.guava.PreferJavaUtilObjectsRequireNonNullElse](https://docs.openrewrite.org/recipes/java/migrate/guava/preferjavautilobjectsrequirenonnullelse): Prefer `java.util.Objects#requireNonNullElse` instead of using `com.google.common.base.MoreObjects#firstNonNull`. 
* [org.openrewrite.java.migrate.jakarta.JakartaEE10](https://docs.openrewrite.org/recipes/java/migrate/jakarta/jakartaee10): These recipes help with the Migration to Jakarta EE 10, flagging and updating deprecated methods. 
* [org.openrewrite.java.migrate.jakarta.WsWsocServerContainerDeprecation](https://docs.openrewrite.org/recipes/java/migrate/jakarta/wswsocservercontainerdeprecation): Deprecated `WsWsocServerContainer.doUpgrade(..)` is replaced by the Jakarta WebSocket 2.1 specification `ServerContainer.upgradeHttpToWebSocket(..)`. 
* [org.openrewrite.java.migrate.maven.shared.MavenSharedStringUtilsRecipes](https://docs.openrewrite.org/recipes/java/migrate/maven/shared/mavensharedstringutilsrecipes): Refaster template recipes for `org.openrewrite.java.migrate.maven.shared.MavenSharedStringUtils`. 
* [org.openrewrite.java.migrate.plexus.PlexusStringUtilsRecipes](https://docs.openrewrite.org/recipes/java/migrate/plexus/plexusstringutilsrecipes): Refaster template recipes for `org.openrewrite.java.migrate.plexus.PlexusStringUtils`. 
* [org.openrewrite.java.migrate.util.ReplaceStreamCollectWithToList](https://docs.openrewrite.org/recipes/java/migrate/util/replacestreamcollectwithtolist): Replace `Stream.collect(Collectors.toUnmodifiableList())` with Java 16+ `Stream.toList()`. Also replaces `Stream.collect(Collectors.toList())` if `convertToList` is set to `true`. 
* [org.openrewrite.java.search.FindMethodDeclaration](https://docs.openrewrite.org/recipes/java/search/findmethoddeclaration): Locates the declaration of a method. 
* [org.openrewrite.java.security.OwaspA01](https://docs.openrewrite.org/recipes/java/security/owaspa01): OWASP [A01:2021](https://owasp.org/Top10/A01_2021-Broken_Access_Control/) describes failures related to broken access  control. 
* [org.openrewrite.java.security.OwaspA02](https://docs.openrewrite.org/recipes/java/security/owaspa02): OWASP [A02:2021](https://owasp.org/Top10/A02_2021-Cryptographic_Failures/) describes failures related to cryptography  (or lack thereof), which often lead to exposure of sensitive data. This recipe seeks to remediate these vulnerabilities. 
* [org.openrewrite.java.security.OwaspA03](https://docs.openrewrite.org/recipes/java/security/owaspa03): OWASP [A03:2021](https://owasp.org/Top10/A03_2021-Injection/) describes failures related to user-supplied data being used to influence program state to operate outside of its intended bounds. This recipe seeks to remediate these vulnerabilities. 
* [org.openrewrite.java.security.OwaspA05](https://docs.openrewrite.org/recipes/java/security/owaspa05): OWASP [A05:2021](https://owasp.org/Top10/A05_2021-Security_Misconfiguration/) describes failures related to security  misconfiguration. 
* [org.openrewrite.java.security.OwaspA06](https://docs.openrewrite.org/recipes/java/security/owaspa06): OWASP [A06:2021](https://owasp.org/Top10/A06_2021-Vulnerable_and_Outdated_Components/) describes failures related to  vulnerable and outdated components. 
* [org.openrewrite.java.security.OwaspA08](https://docs.openrewrite.org/recipes/java/security/owaspa08): OWASP [A08:2021](https://owasp.org/Top10/A08_2021-Software_and_Data_Integrity_Failures/) software and data integrity  failures. 
* [org.openrewrite.java.security.OwaspA10](https://docs.openrewrite.org/recipes/java/security/owaspa10): OWASP [A10:2021](https://owasp.org/Top10/A10_2021-Server-Side_Request_Forgery_%28SSRF%29/) Server-Side Request Forgery (SSRF) 
* [org.openrewrite.java.security.OwaspTopTen](https://docs.openrewrite.org/recipes/java/security/owasptopten): [OWASP](https://owasp.org) publishes a list of the most impactful common security vulnerabilities.  These recipes identify and remediate vulnerabilities from the OWASP Top Ten. 
* [org.openrewrite.java.spring.boot3.UpgradeSpringDoc_2](https://docs.openrewrite.org/recipes/java/spring/boot3/upgradespringdoc_2): Migrate applications to the latest spring-doc 2 release. This recipe will modify an application's build files and make changes code changes for removed/updated APIs. See the [upgrade guide](https://springdoc.org/#migrating-from-springdoc-v1) 
* [org.openrewrite.java.testing.assertj.AdoptAssertJDurationAssertions](https://docs.openrewrite.org/recipes/java/testing/assertj/adoptassertjdurationassertions): Adopt AssertJ `DurationAssert` assertions for more expressive messages. 
* [org.openrewrite.maven.ChangeProjectVersion](https://docs.openrewrite.org/recipes/maven/changeprojectversion): Change the project version of a Maven pom.xml. Identifies the project to be changed by its groupId and artifactId. If the version is defined as a property, this recipe will only change the property value if the property exists within the same pom. 
* [org.openrewrite.maven.IncrementProjectVersion](https://docs.openrewrite.org/recipes/maven/incrementprojectversion): Increase Maven project version by incrementing either the major, minor, or patch version as defined by [semver](https://semver.org/). Other versioning schemes are not supported. 
* [org.openrewrite.micrometer.TimerToObservation](https://docs.openrewrite.org/recipes/micrometer/timertoobservation): Convert Micrometer Timer to Observations. 

## Removed Recipes

* **org.openrewrite.java.liberty.ChangeStringLiteral**: Changes the value of a string literal. 
* **org.openrewrite.java.migrate.JavaVersion20**: Change maven.compiler.source and maven.compiler.target values to 20. 
* **org.openrewrite.java.migrate.UpgradeToJava20**: This recipe will apply changes commonly needed when migrating to Java 20. This recipe will also replace deprecated API with equivalents when there is a clear migration strategy. Build files will also be updated to use Java 20 as the target/source and plugins will be also be upgraded to versions that are compatible with Java 20. 
* **org.openrewrite.java.search.FindCallGraph**: Produce the call graph describing the relationships between methods. 

## Changed Recipes

* [org.openrewrite.text.FindAndReplace](https://docs.openrewrite.org/recipes/text/findandreplace) was changed:
  * Old Options:
    * `caseSensitive: { type: Boolean, required: false }`
    * `dotAll: { type: Boolean, required: false }`
    * `filePattern: { type: String, required: true }`
    * `find: { type: String, required: true }`
    * `multiline: { type: Boolean, required: false }`
    * `regex: { type: Boolean, required: false }`
    * `replace: { type: String, required: true }`
  * New Options:
    * `caseSensitive: { type: Boolean, required: false }`
    * `dotAll: { type: Boolean, required: false }`
    * `filePattern: { type: String, required: false }`
    * `find: { type: String, required: true }`
    * `multiline: { type: Boolean, required: false }`
    * `regex: { type: Boolean, required: false }`
    * `replace: { type: String, required: true }`
* [org.openrewrite.gradle.plugins.AddSettingsPluginRepository](https://docs.openrewrite.org/recipes/gradle/plugins/addsettingspluginrepository) was changed:
  * Old Options:
    * `type: { type: String, required: true }`
    * `url: { type: String, required: true }`
  * New Options:
    * `type: { type: String, required: true }`
    * `url: { type: String, required: false }`
* [org.openrewrite.maven.AddPlugin](https://docs.openrewrite.org/recipes/maven/addplugin) was changed:
  * Old Options:
    * `artifactId: { type: String, required: true }`
    * `configuration: { type: String, required: false }`
    * `dependencies: { type: String, required: false }`
    * `executions: { type: String, required: false }`
    * `groupId: { type: String, required: true }`
    * `version: { type: String, required: true }`
  * New Options:
    * `artifactId: { type: String, required: true }`
    * `configuration: { type: String, required: false }`
    * `dependencies: { type: String, required: false }`
    * `executions: { type: String, required: false }`
    * `filePattern: { type: String, required: false }`
    * `groupId: { type: String, required: true }`
    * `version: { type: String, required: false }`
* [org.openrewrite.maven.ChangeParentPom](https://docs.openrewrite.org/recipes/maven/changeparentpom) was changed:
  * Old Options:
    * `allowVersionDowngrades: { type: Boolean, required: false }`
    * `newArtifactId: { type: String, required: false }`
    * `newGroupId: { type: String, required: false }`
    * `newVersion: { type: String, required: true }`
    * `oldArtifactId: { type: String, required: true }`
    * `oldGroupId: { type: String, required: true }`
    * `retainVersions: { type: List, required: false }`
    * `versionPattern: { type: String, required: false }`
  * New Options:
    * `allowVersionDowngrades: { type: Boolean, required: false }`
    * `newArtifactId: { type: String, required: false }`
    * `newGroupId: { type: String, required: false }`
    * `newRelativePath: { type: String, required: false }`
    * `newVersion: { type: String, required: true }`
    * `oldArtifactId: { type: String, required: true }`
    * `oldGroupId: { type: String, required: true }`
    * `oldRelativePath: { type: String, required: false }`
    * `retainVersions: { type: List, required: false }`
    * `versionPattern: { type: String, required: false }`
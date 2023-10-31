# Snapshot (2023-10-31)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.java.migrate.jakarta.FacesJNDINamesChanged](https://docs.openrewrite.org/recipes/java/migrate/jakarta/facesjndinameschanged): The `jsf/ClientSideSecretKey` JNDI name has been renamed to `faces/ClientSideSecretKey`,  and the `jsf/FlashSecretKey` JNDI name has been renamed to `faces/FlashSecretKey`.  The JNDI keys that have been renamed are updated to allow use of the keys. 
* [org.openrewrite.java.migrate.jakarta.RemoveMethods](https://docs.openrewrite.org/recipes/java/migrate/jakarta/removemethods): Checks for a method patterns and removes the method call from the class 
* [org.openrewrite.java.migrate.jakarta.RemovedJakartaFacesResourceResolver](https://docs.openrewrite.org/recipes/java/migrate/jakarta/removedjakartafacesresourceresolver): The `ResourceResolver` class was removed in Jakarta Faces 4.0.  The functionality provided by that class can be replaced by using the `jakarta.faces.application.ResourceHandler` class. 
* [org.openrewrite.java.migrate.jakarta.RemovedUIComponentConstant](https://docs.openrewrite.org/recipes/java/migrate/jakarta/removeduicomponentconstant): Replace `jakarta.faces.component.UIComponent.CURRENT_COMPONENT` and `CURRENT_COMPOSITE_COMPONENT` constants with `jakarta.faces.component.UIComponent.getCurrentComponent()` and `getCurrentCompositeComponent()` that were added in JSF 2.0 
* [org.openrewrite.java.migrate.jakarta.ServletCookieBehaviorChangeRFC6265](https://docs.openrewrite.org/recipes/java/migrate/jakarta/servletcookiebehaviorchangerfc6265): Jakarta Servlet methods have been deprecated for removal in Jakarta Servlet 6.0 to align with RFC 6265.  In addition, the behavior of these methods has been changed so the setters no longer have any effect, the getComment methods return null, and the getVersion method returns 0. The deprecated methods are removed. 
* [org.openrewrite.java.migrate.lombok.LombokValueToRecord](https://docs.openrewrite.org/recipes/java/migrate/lombok/lombokvaluetorecord): Convert Lombok `@Value` annotated classes to standard Java Records. 
* [org.openrewrite.java.testing.cleanup.AssertEqualsBooleanToAssertBoolean](https://docs.openrewrite.org/recipes/java/testing/cleanup/assertequalsbooleantoassertboolean): Using `assertFalse` or `assertTrue` is simpler and more clear. 
* [org.openrewrite.java.testing.cleanup.AssertNotEqualsBooleanToAssertBoolean](https://docs.openrewrite.org/recipes/java/testing/cleanup/assertnotequalsbooleantoassertboolean): Using `assertFalse` or `assertTrue` is simpler and more clear. 
* [org.openrewrite.java.testing.jmockit.JMockitExpectationsToMockito](https://docs.openrewrite.org/recipes/java/testing/jmockit/jmockitexpectationstomockito): Rewrites JMockit `Expectations` blocks to Mockito statements. 
* [org.openrewrite.maven.cleanup.ExplicitPluginVersion](https://docs.openrewrite.org/recipes/maven/cleanup/explicitpluginversion): Add explicit plugin versions to POMs for reproducibility, as [MNG-4173](https://issues.apache.org/jira/browse/MNG-4173) removes automatic version resolution for POM plugins. 

## Removed Recipes

* **org.openrewrite.java.testing.jmockit.JMockitExpectationsToMockitoWhen**: Rewrites JMockit `Expectations` to `Mockito.when`. 

## Changed Recipes

* [org.openrewrite.gradle.AddDependency](https://docs.openrewrite.org/recipes/gradle/adddependency) was changed:
  * Old Options:
    * `acceptTransitive: { type: Boolean, required: false }`
    * `artifactId: { type: String, required: true }`
    * `classifier: { type: String, required: false }`
    * `configuration: { type: String, required: false }`
    * `extension: { type: String, required: false }`
    * `familyPattern: { type: String, required: false }`
    * `groupId: { type: String, required: true }`
    * `onlyIfUsing: { type: String, required: true }`
    * `version: { type: String, required: false }`
    * `versionPattern: { type: String, required: false }`
  * New Options:
    * `acceptTransitive: { type: Boolean, required: false }`
    * `artifactId: { type: String, required: true }`
    * `classifier: { type: String, required: false }`
    * `configuration: { type: String, required: false }`
    * `extension: { type: String, required: false }`
    * `familyPattern: { type: String, required: false }`
    * `groupId: { type: String, required: true }`
    * `onlyIfUsing: { type: String, required: false }`
    * `version: { type: String, required: false }`
    * `versionPattern: { type: String, required: false }`
* [org.openrewrite.java.dependencies.AddDependency](https://docs.openrewrite.org/recipes/java/dependencies/adddependency) was changed:
  * Old Options:
    * `acceptTransitive: { type: Boolean, required: false }`
    * `artifactId: { type: String, required: true }`
    * `classifier: { type: String, required: false }`
    * `configuration: { type: String, required: false }`
    * `extension: { type: String, required: false }`
    * `familyPattern: { type: String, required: false }`
    * `groupId: { type: String, required: true }`
    * `onlyIfUsing: { type: String, required: true }`
    * `optional: { type: Boolean, required: false }`
    * `releasesOnly: { type: Boolean, required: false }`
    * `scope: { type: String, required: false }`
    * `type: { type: String, required: false }`
    * `version: { type: String, required: false }`
    * `versionPattern: { type: String, required: false }`
  * New Options:
    * `acceptTransitive: { type: Boolean, required: false }`
    * `artifactId: { type: String, required: true }`
    * `classifier: { type: String, required: false }`
    * `configuration: { type: String, required: false }`
    * `extension: { type: String, required: false }`
    * `familyPattern: { type: String, required: false }`
    * `groupId: { type: String, required: true }`
    * `onlyIfUsing: { type: String, required: false }`
    * `optional: { type: Boolean, required: false }`
    * `releasesOnly: { type: Boolean, required: false }`
    * `scope: { type: String, required: false }`
    * `type: { type: String, required: false }`
    * `version: { type: String, required: false }`
    * `versionPattern: { type: String, required: false }`
* [org.openrewrite.maven.AddDependency](https://docs.openrewrite.org/recipes/maven/adddependency) was changed:
  * Old Options:
    * `acceptTransitive: { type: Boolean, required: false }`
    * `artifactId: { type: String, required: true }`
    * `classifier: { type: String, required: false }`
    * `familyPattern: { type: String, required: false }`
    * `groupId: { type: String, required: true }`
    * `onlyIfUsing: { type: String, required: true }`
    * `optional: { type: Boolean, required: false }`
    * `releasesOnly: { type: Boolean, required: false }`
    * `scope: { type: String, required: false }`
    * `type: { type: String, required: false }`
    * `version: { type: String, required: true }`
    * `versionPattern: { type: String, required: false }`
  * New Options:
    * `acceptTransitive: { type: Boolean, required: false }`
    * `artifactId: { type: String, required: true }`
    * `classifier: { type: String, required: false }`
    * `familyPattern: { type: String, required: false }`
    * `groupId: { type: String, required: true }`
    * `onlyIfUsing: { type: String, required: false }`
    * `optional: { type: Boolean, required: false }`
    * `releasesOnly: { type: Boolean, required: false }`
    * `scope: { type: String, required: false }`
    * `type: { type: String, required: false }`
    * `version: { type: String, required: true }`
    * `versionPattern: { type: String, required: false }`
* [org.openrewrite.maven.UpgradePluginVersion](https://docs.openrewrite.org/recipes/maven/upgradepluginversion) was changed:
  * Old Options:
    * `artifactId: { type: String, required: true }`
    * `groupId: { type: String, required: true }`
    * `newVersion: { type: String, required: true }`
    * `trustParent: { type: Boolean, required: false }`
    * `versionPattern: { type: String, required: false }`
  * New Options:
    * `addVersionIfMissing: { type: Boolean, required: false }`
    * `artifactId: { type: String, required: true }`
    * `groupId: { type: String, required: true }`
    * `newVersion: { type: String, required: true }`
    * `trustParent: { type: Boolean, required: false }`
    * `versionPattern: { type: String, required: false }`
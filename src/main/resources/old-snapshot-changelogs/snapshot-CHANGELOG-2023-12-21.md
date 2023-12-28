# Snapshot (2023-12-21)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.github.ChangeActionVersion](https://docs.openrewrite.org/recipes/github/changeactionversion): Change the version of a GitHub Action in any `.github/workflows/*.yml` file. 
* [org.openrewrite.java.ClassDefinitionLength](https://docs.openrewrite.org/recipes/java/classdefinitionlength): Locates class definitions and predicts the number of token in each. 
* [org.openrewrite.java.MethodDefinitionLength](https://docs.openrewrite.org/recipes/java/methoddefinitionlength): Locates method definitions and predicts the number of token in each. 
* [org.openrewrite.java.logging.log4j.CommonsLoggingToLog4j](https://docs.openrewrite.org/recipes/java/logging/log4j/commonsloggingtolog4j): Transforms code written using Apache Commons Logging to use Log4j 2.x API. 
* [org.openrewrite.java.logging.log4j.ConvertJulEntering](https://docs.openrewrite.org/recipes/java/logging/log4j/convertjulentering): Replaces JUL's Logger#entering method calls to Log4j API Logger#traceEntry calls. 
* [org.openrewrite.java.logging.log4j.ConvertJulExiting](https://docs.openrewrite.org/recipes/java/logging/log4j/convertjulexiting): Replaces JUL's Logger#exiting method calls to Log4j API Logger#traceEntry calls. 
* [org.openrewrite.java.logging.log4j.JulToLog4j](https://docs.openrewrite.org/recipes/java/logging/log4j/jultolog4j): Transforms code written using `java.util.logging` to use Log4j 2.x API. 
* [org.openrewrite.java.migrate.CastArraysAsListToList](https://docs.openrewrite.org/recipes/java/migrate/castarraysaslisttolist): Convert code like `(Integer[]) Arrays.asList(1, 2, 3).toArray()` to `Arrays.asList(1, 2, 3).toArray(new Integer[0])`. 
* [org.openrewrite.java.migrate.JpaCacheProperties](https://docs.openrewrite.org/recipes/java/migrate/jpacacheproperties): Sets an explicit value for the shared cache mode. 
* [org.openrewrite.java.migrate.jakarta.BeanValidationMessages](https://docs.openrewrite.org/recipes/java/migrate/jakarta/beanvalidationmessages): Migrate `javax.validation.constraints` messages found in Java files to `jakarta.validation.constraints` equivalents. 
* [org.openrewrite.java.migrate.jakarta.Faces2xMigrationToJakarta4x](https://docs.openrewrite.org/recipes/java/migrate/jakarta/faces2xmigrationtojakarta4x): Jakarta EE 10 uses Faces 4.0 a major upgrade to Jakarta packages and XML namespaces. 
* [org.openrewrite.java.migrate.jakarta.JakartaFacesEcmaScript](https://docs.openrewrite.org/recipes/java/migrate/jakarta/jakartafacesecmascript): Convert JSF to Faces values inside JavaScript,TypeScript, and Properties files 
* [org.openrewrite.java.migrate.jakarta.JakartaFacesXhtml](https://docs.openrewrite.org/recipes/java/migrate/jakarta/jakartafacesxhtml): Find and replace legacy JSF namespaces and javax references with Jakarta Faces values in XHTML files. 
* [org.openrewrite.java.migrate.jakarta.JavaxBeansXmlToJakartaBeansXml](https://docs.openrewrite.org/recipes/java/migrate/jakarta/javaxbeansxmltojakartabeansxml): Java EE has been rebranded to Jakarta EE, necessitating an XML namespace relocation. 
* [org.openrewrite.java.migrate.jakarta.JavaxFacesConfigXmlToJakartaFacesConfigXml](https://docs.openrewrite.org/recipes/java/migrate/jakarta/javaxfacesconfigxmltojakartafacesconfigxml): Java EE has been rebranded to Jakarta EE, necessitating an XML namespace relocation. 
* [org.openrewrite.java.migrate.jakarta.JavaxFacesTagLibraryXmlToJakartaFacesTagLibraryXml](https://docs.openrewrite.org/recipes/java/migrate/jakarta/javaxfacestaglibraryxmltojakartafacestaglibraryxml): Java EE has been rebranded to Jakarta EE, necessitating an XML namespace relocation. 
* [org.openrewrite.java.migrate.jakarta.JavaxToJakartaCdiExtensions](https://docs.openrewrite.org/recipes/java/migrate/jakarta/javaxtojakartacdiextensions): Rename `javax.enterprise.inject.spi.Extension` to `jakarta.enterprise.inject.spi.Extension`. 
* [org.openrewrite.java.migrate.jakarta.JavaxWebFragmentXmlToJakartaWebFragmentXml](https://docs.openrewrite.org/recipes/java/migrate/jakarta/javaxwebfragmentxmltojakartawebfragmentxml): Java EE has been rebranded to Jakarta EE, necessitating an XML namespace relocation. 
* [org.openrewrite.java.migrate.jakarta.JavaxWebXmlToJakartaWebXml](https://docs.openrewrite.org/recipes/java/migrate/jakarta/javaxwebxmltojakartawebxml): Java EE has been rebranded to Jakarta EE, necessitating an XML namespace relocation. 
* [org.openrewrite.java.migrate.jakarta.UpgradeFacesOpenSourceLibraries](https://docs.openrewrite.org/recipes/java/migrate/jakarta/upgradefacesopensourcelibraries): Upgrade PrimeFaces, OmniFaces, and MyFaces libraries to Jakarta EE10 versions. 
* [org.openrewrite.java.security.servlet.CookieSetSecure](https://docs.openrewrite.org/recipes/java/security/servlet/cookiesetsecure): Check for use of insecure cookies. Cookies should be marked as secure. This ensures that the cookie is sent only over HTTPS to prevent cross-site scripting attacks. 
* [org.openrewrite.java.spring.security6.ApplyToWithLambdaDsl](https://docs.openrewrite.org/recipes/java/spring/security6/applytowithlambdadsl): Converts `HttpSecurity::apply` chained call from Spring Security pre 6.2.x into new lambda DSL style calls and removes `and()` methods. 
* [org.openrewrite.java.testing.junit5.AddMissingTestBeforeAfterAnnotations](https://docs.openrewrite.org/recipes/java/testing/junit5/addmissingtestbeforeafterannotations): Adds `@BeforeEach`, `@AfterEach`, `@Test` to methods overriding superclass methods if the annoations are present on the superclass method. 
* [org.openrewrite.java.testing.mockito.Mockito1to5Migration](https://docs.openrewrite.org/recipes/java/testing/mockito/mockito1to5migration): Upgrade Mockito from 1.x to 5.x. 
* [org.openrewrite.kotlin.cleanup.RemoveTrailingComma](https://docs.openrewrite.org/recipes/kotlin/cleanup/removetrailingcomma): Remove trailing commas in variable, parameter, and class property lists. 
* [org.openrewrite.kotlin.cleanup.UnnecessaryTypeParentheses](https://docs.openrewrite.org/recipes/kotlin/cleanup/unnecessarytypeparentheses): In Kotlin, it's possible to add redundant nested parentheses in type definitions. This recipe is designed to remove those unnecessary parentheses. 
* [org.openrewrite.quarkus.AddQuarkusProperty](https://docs.openrewrite.org/recipes/quarkus/addquarkusproperty): Add a Quarkus configuration property to an existing configuration file if it does not already exist in that file. 

## Changed Recipes

* [org.openrewrite.FindSourceFiles](https://docs.openrewrite.org/recipes/findsourcefiles) was changed:
  * Old Options:
    * `filePattern: { type: String, required: true }`
  * New Options:
    * `filePattern: { type: String, required: false }`
* [org.openrewrite.text.FindAndReplace](https://docs.openrewrite.org/recipes/text/findandreplace) was changed:
  * Old Options:
    * `caseSensitive: { type: Boolean, required: false }`
    * `dotAll: { type: Boolean, required: false }`
    * `filePattern: { type: String, required: false }`
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
    * `replace: { type: String, required: false }`
* [org.openrewrite.gradle.UpdateGradleWrapper](https://docs.openrewrite.org/recipes/gradle/updategradlewrapper) was changed:
  * Old Options:
    * `addIfMissing: { type: Boolean, required: false }`
    * `distribution: { type: String, required: false }`
    * `repositoryUrl: { type: String, required: false }`
    * `version: { type: String, required: false }`
  * New Options:
    * `addIfMissing: { type: Boolean, required: false }`
    * `distribution: { type: String, required: false }`
    * `version: { type: String, required: false }`
* [org.openrewrite.java.search.FindImplementations](https://docs.openrewrite.org/recipes/java/search/findimplementations) was changed:
  * Old Options:
    * `interfaceFullyQualifiedName: { type: String, required: true }`
    * `matchInherited: { type: Boolean, required: false }`
  * New Options:
    * `typeName: { type: String, required: true }`
* [org.openrewrite.properties.AddProperty](https://docs.openrewrite.org/recipes/properties/addproperty) was changed:
  * Old Options:
    * `delimiter: { type: String, required: false }`
    * `property: { type: String, required: true }`
    * `value: { type: String, required: true }`
  * New Options:
    * `comment: { type: String, required: false }`
    * `delimiter: { type: String, required: false }`
    * `property: { type: String, required: true }`
    * `value: { type: String, required: true }`
* [org.openrewrite.java.spring.RenameBean](https://docs.openrewrite.org/recipes/java/spring/renamebean) was changed:
  * Old Options:
    * `None`
  * New Options:
    * `newName: { type: String, required: true }`
    * `oldName: { type: String, required: true }`
    * `type: { type: String, required: false }`
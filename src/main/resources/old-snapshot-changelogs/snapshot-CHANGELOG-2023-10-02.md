# Snapshot (2023-10-02)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.config.DeclarativeRecipe$LazyLoadedRecipe](https://docs.openrewrite.org/recipes/config/declarativerecipe$lazyloadedrecipe): Recipe that is loaded lazily. 
* [org.openrewrite.java.migrate.IBMSemeru](https://docs.openrewrite.org/recipes/java/migrate/ibmsemeru): This recipe will apply changes commonly needed when upgrading Java versions. The solutions provided in this list are solutions only availible in IBM Semeru Runtimes. 
* [org.openrewrite.java.migrate.InternalBindContextFactory](https://docs.openrewrite.org/recipes/java/migrate/internalbindcontextfactory): Do not use the `com.sun.xml.internal.bind.v2.ContextFactory` class. 
* [org.openrewrite.java.migrate.JREDoNotUseSunNetSslInternalSslProvider](https://docs.openrewrite.org/recipes/java/migrate/jredonotusesunnetsslinternalsslprovider): Do not use the `com.sun.net.ssl.internal.ssl.Provider` class. 
* [org.openrewrite.java.migrate.JREDoNotUseSunNetSslInternalWwwProtocol](https://docs.openrewrite.org/recipes/java/migrate/jredonotusesunnetsslinternalwwwprotocol): Do not use the `com.sun.net.ssl.internal.www.protocol` package. 
* [org.openrewrite.java.migrate.JREDoNotUseSunNetSslInternalWwwProtocolHttpsHandler](https://docs.openrewrite.org/recipes/java/migrate/jredonotusesunnetsslinternalwwwprotocolhttpshandler): Do not use the `com.sun.net.ssl.internal.www.protocol.https.Handler` class. 
* [org.openrewrite.java.migrate.jakarta.RemovedIsParmetersProvidedMethod](https://docs.openrewrite.org/recipes/java/migrate/jakarta/removedisparmetersprovidedmethod): Expression Language prior to 5.0 provides the deprecated MethodExpression.isParmetersProvided() method, with the word 'parameter' misspelled in the method name.  This method is unavailable in Jakarta Expression Language 5.0. Use the correctly spelled MethodExpression.isParametersProvided() method instead. 
* [org.openrewrite.java.migrate.jakarta.RemovedSOAPElementFactory](https://docs.openrewrite.org/recipes/java/migrate/jakarta/removedsoapelementfactory): XML Web Services prior to 4.0 provides the deprecated SOAPElementFactory class,  which is removed in XML Web Services 4.0. The recommended replacement is to use jakarta.xml.soap.SOAPFactory to create SOAPElements. 
* [org.openrewrite.java.migrate.javax.AddJaxbRuntime$AddJaxbRuntimeGradle](https://docs.openrewrite.org/recipes/java/migrate/javax/addjaxbruntime$addjaxbruntimegradle): Update Gradle build files to use the latest JAXB runtime from Jakarta EE 8 to maintain compatibility with Java version 11 or greater.  The recipe will add a JAXB run-time, in `compileOnly`+`testImplementation` configurations, to any project that has a transitive dependency on the JAXB API. **The resulting dependencies still use the `javax` namespace, despite the move to the Jakarta artifact**. 
* [org.openrewrite.java.migrate.javax.AddJaxbRuntime$AddJaxbRuntimeMaven](https://docs.openrewrite.org/recipes/java/migrate/javax/addjaxbruntime$addjaxbruntimemaven): Update Maven build files to use the latest JAXB runtime from Jakarta EE 8 to maintain compatibility with Java version 11 or greater.  The recipe will add a JAXB run-time, in `provided` scope, to any project that has a transitive dependency on the JAXB API. **The resulting dependencies still use the `javax` namespace, despite the move to the Jakarta artifact**. 
* [org.openrewrite.java.migrate.javax.AddJaxwsRuntime$AddJaxwsRuntimeGradle](https://docs.openrewrite.org/recipes/java/migrate/javax/addjaxwsruntime$addjaxwsruntimegradle): Update Gradle build files to use the latest JAX-WS runtime from Jakarta EE 8 to maintain compatibility with Java version 11 or greater.  The recipe will add a JAX-WS run-time, in `compileOnly`+`testImplementation` configurations, to any project that has a transitive dependency on the JAX-WS API. **The resulting dependencies still use the `javax` namespace, despite the move to the Jakarta artifact**. 
* [org.openrewrite.java.migrate.javax.AddJaxwsRuntime$AddJaxwsRuntimeMaven](https://docs.openrewrite.org/recipes/java/migrate/javax/addjaxwsruntime$addjaxwsruntimemaven): Update maven build files to use the latest JAX-WS runtime from Jakarta EE 8 to maintain compatibility with Java version 11 or greater.  The recipe will add a JAX-WS run-time, in `provided` scope, to any project that has a transitive dependency on the JAX-WS API. **The resulting dependencies still use the `javax` namespace, despite the move to the Jakarta artifact**. 
* [org.openrewrite.java.spring.amqp.UseTlsAmqpConnectionString$UseTlsAmqpConnectionStringProperties](https://docs.openrewrite.org/recipes/java/spring/amqp/usetlsamqpconnectionstring$usetlsamqpconnectionstringproperties): Use TLS for AMQP connection strings. 
* [org.openrewrite.java.spring.amqp.UseTlsAmqpConnectionString$UseTlsAmqpConnectionStringYaml](https://docs.openrewrite.org/recipes/java/spring/amqp/usetlsamqpconnectionstring$usetlsamqpconnectionstringyaml): Use TLS for AMQP connection strings. 
* [org.openrewrite.java.spring.boot2.MigrateDatabaseCredentials$MigrateDatabaseCredentialsForToolProperties](https://docs.openrewrite.org/recipes/java/spring/boot2/migratedatabasecredentials$migratedatabasecredentialsfortoolproperties): Migrate null credentials. 
* [org.openrewrite.java.spring.boot2.MigrateDatabaseCredentials$MigrateDatabaseCredentialsForToolYaml](https://docs.openrewrite.org/recipes/java/spring/boot2/migratedatabasecredentials$migratedatabasecredentialsfortoolyaml): Migrate null credentials. 
* [org.openrewrite.java.spring.data.UseTlsJdbcConnectionString$UseTlsJdbcConnectionStringProperties](https://docs.openrewrite.org/recipes/java/spring/data/usetlsjdbcconnectionstring$usetlsjdbcconnectionstringproperties): Use TLS for JDBC connection strings. 
* [org.openrewrite.java.spring.data.UseTlsJdbcConnectionString$UseTlsJdbcConnectionStringYaml](https://docs.openrewrite.org/recipes/java/spring/data/usetlsjdbcconnectionstring$usetlsjdbcconnectionstringyaml): Use TLS for JDBC connection strings. 
* [org.openrewrite.maven.BestPractices](https://docs.openrewrite.org/recipes/maven/bestpractices): Applies best practices to Maven POMs. 
* [org.openrewrite.maven.cleanup.ExplicitPluginGroupId](https://docs.openrewrite.org/recipes/maven/cleanup/explicitplugingroupid): Add the default `<groupId>org.apache.maven.plugins</groupId>` to plugins for clarity. 
* [org.openrewrite.maven.cleanup.PrefixlessExpressions](https://docs.openrewrite.org/recipes/maven/cleanup/prefixlessexpressions): MNG-7404 drops support for prefixless in POMs. This recipe will add the `project.` prefix where missing. 
* [org.openrewrite.maven.search.ParentPomInsight](https://docs.openrewrite.org/recipes/maven/search/parentpominsight): Find Maven parents matching a `groupId` and `artifactId`. 

## Removed Recipes

* **org.openrewrite.java.migrate.apache.commons.io.ApacheCommonsFileUtilsRecipes**: Refaster template recipes for `org.openrewrite.java.migrate.apache.commons.io.ApacheCommonsFileUtils`. 
* **org.openrewrite.java.migrate.apache.commons.lang.ApacheCommonsStringUtilsRecipes**: Refaster template recipes for `org.openrewrite.java.migrate.apache.commons.lang.ApacheCommonsStringUtils`. 
* **org.openrewrite.java.migrate.lang.StringRulesRecipes**: Refaster template recipes for `org.openrewrite.java.migrate.lang.StringRules`. 
* **org.openrewrite.java.migrate.lang.UseStringIsEmptyRecipe**: Recipe created for the following Refaster template:
```java
public class UseStringIsEmpty {
    
    @BeforeTemplate
    boolean before(String s) {
        return s.length() > 0;
    }
    
    @AfterTemplate
    boolean after(String s) {
        return !s.isEmpty();
    }
}
```
. 
* **org.openrewrite.java.migrate.maven.shared.MavenSharedStringUtilsRecipes**: Refaster template recipes for `org.openrewrite.java.migrate.maven.shared.MavenSharedStringUtils`. 
* **org.openrewrite.java.migrate.plexus.PlexusFileUtilsRecipes**: Refaster template recipes for `org.openrewrite.java.migrate.plexus.PlexusFileUtils`. 
* **org.openrewrite.java.migrate.plexus.PlexusStringUtilsRecipes**: Refaster template recipes for `org.openrewrite.java.migrate.plexus.PlexusStringUtils`. 

## Changed Recipes

* [org.openrewrite.java.search.FindDeprecatedFields](https://docs.openrewrite.org/recipes/java/search/finddeprecatedfields) was changed:
  * Old Options:
    * `ignoreDeprecatedScopes: { type: Boolean, required: false }`
    * `typePattern: { type: String, required: false }`
  * New Options:
    * `ignoreDeprecatedScopes: { type: Boolean, required: false }`
    * `matchInherited: { type: Boolean, required: false }`
    * `typePattern: { type: String, required: false }`
* [org.openrewrite.java.search.FindFields](https://docs.openrewrite.org/recipes/java/search/findfields) was changed:
  * Old Options:
    * `fieldName: { type: String, required: true }`
    * `fullyQualifiedTypeName: { type: String, required: true }`
  * New Options:
    * `fieldName: { type: String, required: true }`
    * `fullyQualifiedTypeName: { type: String, required: true }`
    * `matchInherited: { type: Boolean, required: false }`
* [org.openrewrite.java.search.FindFieldsOfType](https://docs.openrewrite.org/recipes/java/search/findfieldsoftype) was changed:
  * Old Options:
    * `fullyQualifiedTypeName: { type: String, required: true }`
  * New Options:
    * `fullyQualifiedTypeName: { type: String, required: true }`
    * `matchInherited: { type: Boolean, required: false }`
* [org.openrewrite.java.search.FindImplementations](https://docs.openrewrite.org/recipes/java/search/findimplementations) was changed:
  * Old Options:
    * `None`
  * New Options:
    * `interfaceFullyQualifiedName: { type: String, required: true }`
    * `matchInherited: { type: Boolean, required: false }`
* [org.openrewrite.java.search.FindImports](https://docs.openrewrite.org/recipes/java/search/findimports) was changed:
  * Old Options:
    * `typePattern: { type: String, required: false }`
  * New Options:
    * `matchInherited: { type: Boolean, required: false }`
    * `typePattern: { type: String, required: false }`
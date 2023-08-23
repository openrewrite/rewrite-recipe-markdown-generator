# Snapshot (2023-08-23)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Artifacts
* rewrite-liberty
* rewrite-micrometer
* rewrite-okhttp

## New Recipes

* [org.openrewrite.java.liberty](https://docs.openrewrite.org/reference/recipes/java/liberty): Use this category of rules to identify code changes needed when migrating  from WebSphere Application Server traditional to Liberty. 
* [org.openrewrite.java.liberty.ChangeStringLiteral](https://docs.openrewrite.org/reference/recipes/java/liberty/changestringliteral): Changes the value of a string literal. 
* [org.openrewrite.java.liberty.RemoveWas2LibertyNonPortableJndiLookup](https://docs.openrewrite.org/reference/recipes/java/liberty/removewas2libertynonportablejndilookup): Remove the use of invalid JNDI properties from Hashtable. 
* [org.openrewrite.java.liberty.ServerName](https://docs.openrewrite.org/reference/recipes/java/liberty/servername): `ServerName.getDisplayName()` is not available in Liberty. 
* [org.openrewrite.java.liberty.WebSphereUnavailableSSOCookieMethod](https://docs.openrewrite.org/reference/recipes/java/liberty/websphereunavailablessocookiemethod): Replace `WSSecurityHelper.revokeSSOCookies(request, response)` with `request.logout()`. 
* [org.openrewrite.java.liberty.WebSphereUnavailableSSOTokenMethod](https://docs.openrewrite.org/reference/recipes/java/liberty/websphereunavailablessotokenmethod): This method `LTPACookieFromSSOToken()` is deprecated in traditional WebSphere Application  Server Version 8.5 and might be removed in a future release. It is not available on Liberty. 
* [org.openrewrite.java.migrate.apache.commons.io.ApacheCommonsFileUtilsRecipes](https://docs.openrewrite.org/reference/recipes/java/migrate/apache/commons/io/apachecommonsfileutilsrecipes): Refaster template recipes for `org.openrewrite.java.migrate.apache.commons.io.ApacheCommonsFileUtils`. 
* [org.openrewrite.java.spring.security5.AuthorizeHttpRequests](https://docs.openrewrite.org/reference/recipes/java/spring/security5/authorizehttprequests): Replace `HttpSecurity.authorizeRequests(...)` deprecated in Spring Security 6 with `HttpSecurity.authorizeHttpRequests(...)` and all method calls on the resultant object respectively. Replace deprecated `AbstractInterceptUrlConfigurer` and its deprecated subclasses with `AuthorizeHttpRequestsConfigurer` and its corresponding subclasses. 
* [org.openrewrite.micrometer.UpgradeMicrometer](https://docs.openrewrite.org/reference/recipes/micrometer/upgrademicrometer): This recipe will apply changes commonly needed when migrating Micrometer. 
* [org.openrewrite.okhttp.ReorderRequestBodyCreateArguments](https://docs.openrewrite.org/reference/recipes/okhttp/reorderrequestbodycreatearguments): Reorder the arguments of `RequestBody.create() to put the `MediaType` argument after the `String` body. 
* [org.openrewrite.okhttp.UpgradeOkHttp5](https://docs.openrewrite.org/reference/recipes/okhttp/upgradeokhttp5): This recipe will apply changes commonly needed when migrating to OkHttp 5.x. 
* [org.openrewrite.okhttp.UpgradeOkHttp5Dependencies](https://docs.openrewrite.org/reference/recipes/okhttp/upgradeokhttp5dependencies): Migrate OkHttp dependencies to 5.x. 
* [org.openrewrite.okio.UpgradeOkio3](https://docs.openrewrite.org/reference/recipes/okio/upgradeokio3): This recipe will apply changes commonly needed when migrating to Okio 3.x. 
* [org.openrewrite.okio.UpgradeOkio3Dependencies](https://docs.openrewrite.org/reference/recipes/okio/upgradeokio3dependencies): Migrate Okio dependencies to 3.x. 
* [org.openrewrite.xml.liberty.AppDDNamespaceRule](https://docs.openrewrite.org/reference/recipes/xml/liberty/appddnamespacerule): Namespace values in application.xml must be consistent with the descriptor version. 
* [org.openrewrite.xml.liberty.ConnectorDDNamespaceRule](https://docs.openrewrite.org/reference/recipes/xml/liberty/connectorddnamespacerule): Namespace values in ra.xml must be consistent with the descriptor version. 
* [org.openrewrite.xml.liberty.EJBDDNamespaceRule](https://docs.openrewrite.org/reference/recipes/xml/liberty/ejbddnamespacerule): Namespace values in ejb-jar.xml must be consistent with the descriptor version. 
* [org.openrewrite.xml.liberty.PersistenceXmlLocationRule](https://docs.openrewrite.org/reference/recipes/xml/liberty/persistencexmllocationrule): This recipes moves persistence.xml files into the root META-INF directory in source folder. 
* [org.openrewrite.xml.liberty.WebDDNamespaceRule](https://docs.openrewrite.org/reference/recipes/xml/liberty/webddnamespacerule): Namespace values in web.xml must be consistent with the descriptor version. 

## Removed Recipes

* **org.openrewrite.java.spring.boot2.AuthorizeHttpRequests**: Replace `HttpSecurity.authorizeRequests(...)` deprecated in Spring Security 6 with `HttpSecurity.authorizeHttpRequests(...)` and all method calls on the resultant object respectively. Replace deprecated `AbstractInterceptUrlConfigurer` and its deprecated subclasses with `AuthorizeHttpRequestsConfigurer` and its corresponding subclasses. 

## Changed Recipes

* [org.openrewrite.maven.AddGradleEnterpriseMavenExtension](https://docs.openrewrite.org/reference/recipes/maven/addgradleenterprisemavenextension) was changed:
  * Old Options:
    * `allowUntrustedServer: { type: Boolean, required: false }`
    * `captureGoalInputFiles: { type: Boolean, required: false }`
    * `publishCriteria: { type: PublishCriteria, required: false }`
    * `server: { type: String, required: true }`
    * `uploadInBackground: { type: Boolean, required: false }`
    * `version: { type: String, required: true }`
  * New Options:
    * `allowUntrustedServer: { type: Boolean, required: false }`
    * `captureGoalInputFiles: { type: Boolean, required: false }`
    * `publishCriteria: { type: PublishCriteria, required: false }`
    * `server: { type: String, required: true }`
    * `uploadInBackground: { type: Boolean, required: false }`
    * `version: { type: String, required: false }`
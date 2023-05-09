# Snapshot (2023-05-09)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes
* [org.openrewrite.java.search.FindCallGraph](https://docs.openrewrite.org/reference/recipes/java/search/findcallgraph): Produce the call graph describing the relationships between methods. 
* [org.openrewrite.java.spring.amqp.UseTlsAmqpConnectionString](https://docs.openrewrite.org/reference/recipes/java/spring/amqp/usetlsamqpconnectionstring): Use TLS for AMQP connection strings. 
* [org.openrewrite.java.spring.security5.WebSecurityConfigurerAdapter](https://docs.openrewrite.org/reference/recipes/java/spring/security5/websecurityconfigureradapter): The Spring Security `WebSecurityConfigurerAdapter` was deprecated 5.7, this recipe will transform `WebSecurityConfigurerAdapter` classes by using a component based approach. Check out the [spring-security-without-the-websecurityconfigureradapter](https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter) blog for more details. 
* [org.openrewrite.maven.AddGradleEnterpriseMavenExtension](https://docs.openrewrite.org/reference/recipes/maven/addgradleenterprisemavenextension): To integrate gradle enterprise maven extension into maven projects, ensure that the `gradle-enterprise-maven-extension` is added to the `.mvn/extensions.xml` file if not already present. Additionally, configure the extension by adding the `.mvn/gradle-enterprise.xml` configuration file. 

## Removed Recipes
* **org.openrewrite.java.spring.boot2.WebSecurityConfigurerAdapter**: The Spring Security `WebSecurityConfigurerAdapter` was deprecated 5.7, this recipe will transform `WebSecurityConfigurerAdapter` classes by using a component based approach. Check out the [spring-security-without-the-websecurityconfigureradapter](https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter) blog for more details. 


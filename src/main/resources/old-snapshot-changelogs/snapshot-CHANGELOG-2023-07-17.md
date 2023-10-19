# Snapshot (2023-07-17)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.java.testing.junit5.RemoveTryCatchFailBlocks](https://docs.openrewrite.org/reference/recipes/java/testing/junit5/removetrycatchfailblocks): Replace `try-catch` blocks where `catch` merely contains a `fail(..)` statement with `Assertions.assertDoesNotThrow(() -> { ... })`. 

## Removed Recipes

* **org.openrewrite.github.AddCronTrigger**: The `schedule` [event](https://docs.github.com/en/actions/reference/events-that-trigger-workflows#scheduled-events) allows you to trigger a workflow at a scheduled time. 
* **org.openrewrite.hibernate.MigrateToHibernate62**: This recipe will apply changes commonly needed when migrating to Hibernate 6.2.x. 
* **org.openrewrite.hibernate.MigrateToHypersistenceUtilsHibernate6.0**: This recipe will migrate any existing dependencies on `com.vladmihalcea:hibernate-types` to `io.hypersistence:hypersistence-utils-hibernate-60`.  This migration will include the adjustment from `com.vladmihalcea` to `io.hypersistence.utils` package name. 
* **org.openrewrite.hibernate.MigrateToHypersistenceUtilsHibernate6.2**: This recipe will migrate any existing dependencies on `io.hypersistence:hypersistence-utils-hibernate-60` to `io.hypersistence:hypersistence-utils-hibernate-62`. 
* **org.openrewrite.java.logging.slf4j.ChangeLogLevel**: Change the log level of slf4j log statements. 
* **org.openrewrite.java.spring.framework.UpgradeSpringFrameworkDependencies**: Upgrade spring-framework 5.x Maven dependencies using a Node Semver advanced range selector. 
* **org.openrewrite.staticanalysis.ReplaceWeekYearWithYear**: For most dates Week Year (YYYY) and Year (yyyy) yield the same results. However, on the last week of December and first week of January Week Year could produce unexpected results. 

## Changed Recipes

* [org.openrewrite.java.spring.amqp.UseTlsAmqpConnectionString](https://docs.openrewrite.org/reference/recipes/java/spring/amqp/usetlsamqpconnectionstring) was changed:
  * Old Options:
    * `oldPort: { type: Integer, required: true }`
    * `pathExpressions: { type: List, required: false }`
    * `port: { type: Integer, required: true }`
    * `propertyKey: { type: String, required: false }`
    * `tlsPropertyKey: { type: String, required: false }`
  * New Options:
    * `oldPort: { type: Integer, required: true }`
    * `pathExpressions: { type: List, required: false }`
    * `port: { type: Integer, required: true }`
    * `propertyKey: { type: String, required: true }`
    * `tlsPropertyKey: { type: String, required: true }`
---
description: What's changed in OpenRewrite version 8.45.0.
---

# 8.45.0 release (2025-02-08)

_Total recipe count: 3041_

:::info
This changelog only shows what recipes have been added, removed, or changed. OpenRewrite may do releases that do not include these types of changes. To see these changes, please go to the [releases page](https://github.com/openrewrite/rewrite/releases).
:::

## New Artifacts
* rewrite-rewrite

## New Recipes

* [io.moderne.knowledge.ComprehendCodeWithUnitTestExamples](https://docs.openrewrite.org/recipes/knowledge/comprehendcodewithunittestexamples): First runs a scanning recipe to grab all unit tests, then supplements the unit tests examples for the AI-generate descriptions.
* [io.moderne.knowledge.search.SearchDesignTechnique](https://docs.openrewrite.org/recipes/knowledge/search/searchdesigntechnique): Search for a class that uses the given design technique.
* [io.moderne.recipe.hibernate.MigrateToHibernate66](https://docs.openrewrite.org/recipes/recipe/hibernate/migratetohibernate66): This recipe will apply changes commonly needed when migrating to Hibernate 6.5.x.
* [io.moderne.recipe.hibernate.search.FindJPQLDefinitions](https://docs.openrewrite.org/recipes/recipe/hibernate/search/findjpqldefinitions): Find Java Persistence Query Language definitions in the codebase.
* [io.moderne.recipe.hibernate.update66.FixConflictingClassTypeAnnotations](https://docs.openrewrite.org/recipes/recipe/hibernate/update66/fixconflictingclasstypeannotations): Since Hibernate 6.6 a mapped class can have *either* `@MappedSuperclass` or `@Embeddable`, or `@Entity`. This recipe removes `@Entity` from classes annotated with `@MappedSuperclass` or `@Embeddable`.For the moment die combination of `@MappedSuperclass` or `@Embeddable` is advised to migrate to [Single Table Inheritance](https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#entity-inheritance-single-table)but still accepted and therefore stays.
* [io.moderne.recipe.hibernate.update66.RemoveTableFromInheritedEntity](https://docs.openrewrite.org/recipes/recipe/hibernate/update66/removetablefrominheritedentity): For Single Table Inherited Entities Hibernate ignores the `@Table` annotation on child entities. From Version 6.6 it is considered an error.
* [io.moderne.recipe.spring.boot3.AddValidToConfigurationPropertiesFields](https://docs.openrewrite.org/recipes/recipe/spring/boot3/addvalidtoconfigurationpropertiesfields): In Spring Boot 3.4, validation of `@ConfigurationProperties` classes annotated with `@Validated` now follows the Bean Validation specification, only cascading to nested properties if the corresponding field is annotated with `@Valid`. The recipe will add a `@Valid` annotation to each field which has a type that has a field which is annotated with a `jakarta.validation.constraints.*` annotation.
* [io.moderne.recipe.spring.boot3.CommentDeprecations](https://docs.openrewrite.org/recipes/recipe/spring/boot3/commentdeprecations): Spring Boot 3.4 deprecates methods that are not commonly used or need manual interaction.
* [io.moderne.recipe.spring.boot3.CommentOnMockAndSpyBeansInConfigSpring34](https://docs.openrewrite.org/recipes/recipe/spring/boot3/commentonmockandspybeansinconfigspring34): As stated in [Spring Docs](https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-mockitobean.html) `@MockitoSpyBean` and `@MockitoBean` will only work in tests, explicitly not in `@Configuration` annotated classes.
* [io.moderne.recipe.spring.boot3.ConditionalOnAvailableEndpointMigrationSpring34](https://docs.openrewrite.org/recipes/recipe/spring/boot3/conditionalonavailableendpointmigrationspring34): Migrate `@ConditionalOnAvailableEndpoint(EndpointExposure.CLOUD_FOUNDRY)` to `@ConditionalOnAvailableEndpoint(EndpointExposure.WEB)` for Spring Boot 3.4.
* [io.moderne.recipe.spring.boot3.MigrateEndpointAccessValueSpring34](https://docs.openrewrite.org/recipes/recipe/spring/boot3/migrateendpointaccessvaluespring34): Migrate manage endpoint access value from `false` to `none` and `true` to `read-only`.
* [io.moderne.recipe.spring.boot3.MigrateEndpointAnnotationAccessValueSpring34](https://docs.openrewrite.org/recipes/recipe/spring/boot3/migrateendpointannotationaccessvaluespring34): Since Spring Boot 3.4 the `@Endpoint` access configuration values are no longer `true|false` but `none|read-only|unrestricted`
* [io.moderne.recipe.spring.boot3.RemoveReplaceNoneFromAutoConfigureTestDatabase](https://docs.openrewrite.org/recipes/recipe/spring/boot3/removereplacenonefromautoconfiguretestdatabase): `Replace.NONE` is the default value for `@AutoConfigureTestDatabase` since Spring Boot 3.4.
* [io.moderne.recipe.spring.boot3.SpringBoot3BestPractices](https://docs.openrewrite.org/recipes/recipe/spring/boot3/springboot3bestpractices): Applies best practices to Spring Boot 3.4+ applications.
* [io.moderne.recipe.spring.boot3.SpringBootManagementEndpointProperties_3_4](https://docs.openrewrite.org/recipes/recipe/spring/boot3/springbootmanagementendpointproperties_3_4): Migrate the settings for Management Endpoint Security from `true`|`false` to `read-only`|`none`.
* [io.moderne.recipe.spring.boot3.SpringBootProperties_3_4](https://docs.openrewrite.org/recipes/recipe/spring/boot3/springbootproperties_3_4): Migrate properties found in `application.properties` and `application.yml`.
* [io.moderne.recipe.spring.boot3.UpgradeGradle7Spring34](https://docs.openrewrite.org/recipes/recipe/spring/boot3/upgradegradle7spring34): Spring Boot 3.4 requires Gradle 7.6.4.
* [io.moderne.recipe.spring.boot3.UpgradeGradle8Spring34](https://docs.openrewrite.org/recipes/recipe/spring/boot3/upgradegradle8spring34): Spring Boot 3.4 requires Gradle 8.4+.
* [io.moderne.recipe.spring.boot3.UpgradeSpringBoot_3_4](https://docs.openrewrite.org/recipes/recipe/spring/boot3/upgradespringboot_3_4): Migrate applications to the latest Spring Boot 3.4 release. This recipe will modify an application's build files, make changes to deprecated/preferred APIs, and migrate configuration settings that have changes between versions. This recipe will also chain additional framework migrations (Spring Framework, Spring Data, etc) that are required as part of the migration to Spring Boot 3.4.
* [io.moderne.recipe.spring.cloud2024.DependencyUpgrades](https://docs.openrewrite.org/recipes/recipe/spring/cloud2024/dependencyupgrades): Upgrade dependencies to Spring Cloud 2024 from prior 2023.x version.
* [io.moderne.recipe.spring.cloud2024.UpgradeSpringCloud_2024](https://docs.openrewrite.org/recipes/recipe/spring/cloud2024/upgradespringcloud_2024): Migrate applications to the latest Spring Cloud 2024 (Leyton) release.
* [io.moderne.recipe.spring.framework.UpgradeSpringFramework_6_2](https://docs.openrewrite.org/recipes/recipe/spring/framework/upgradespringframework_6_2): Migrate applications to the latest Spring Framework 6.2 release.
* [io.quarkus.updates.core.quarkus318.RemoveFlywayCleanOnValidationError](https://docs.openrewrite.org/recipes/io/quarkus/updates/core/quarkus318/removeflywaycleanonvalidationerror):
* [io.quarkus.updates.core.quarkus37.SetupJavaUpgradeJavaVersion](https://docs.openrewrite.org/recipes/io/quarkus/updates/core/quarkus37/setupjavaupgradejavaversion): Update the Java version used by `actions/setup-java` if it is below the expected version number.
* [io.quarkus.updates.core.quarkus37.UpgradeJavaVersion](https://docs.openrewrite.org/recipes/io/quarkus/updates/core/quarkus37/upgradejavaversion): Upgrade build plugin configuration to use the specified Java version. This recipe changes `java.toolchain.languageVersion` in `build.gradle(.kts)` of gradle projects, or maven-compiler-plugin target version and related settings. Will not downgrade if the version is newer than the specified version.
* [org.apache.camel.upgrade.CamelMigrationRecipe](https://docs.openrewrite.org/recipes/org/apache/camel/upgrade/camelmigrationrecipe): Migrates Apache Camel application to 4.9.0
* [org.apache.camel.upgrade.camel40.properties.rejectedPolicy](https://docs.openrewrite.org/recipes/org/apache/camel/upgrade/camel40/properties/rejectedpolicy): Apache Camel API migration from version 3.20 or higher to 4.0. Removal of deprecated APIs, which could be part of the application.properties.
* [org.apache.camel.upgrade.camel49.AwsSecretRecipe](https://docs.openrewrite.org/recipes/org/apache/camel/upgrade/camel49/awssecretrecipe): The syntax for retrieving a single field of a secret has been changed..
* [org.apache.camel.upgrade.camel49.AzureSecretRecipe](https://docs.openrewrite.org/recipes/org/apache/camel/upgrade/camel49/azuresecretrecipe): The syntax for retrieving a single field of a secret has been changed..
* [org.apache.camel.upgrade.camel49.CamelMigrationRecipe](https://docs.openrewrite.org/recipes/org/apache/camel/upgrade/camel49/camelmigrationrecipe): Migrates `camel 4.8` application to `camel 4.9`.
* [org.apache.camel.upgrade.camel49.DebeziumChangeTypes](https://docs.openrewrite.org/recipes/org/apache/camel/upgrade/camel49/debeziumchangetypes): each camel-debezium module has its own subpackage corresponding to the database type. So for example, all the classes of the module camel-debezium-postgres have been moved to a dedicated package which is org.apache.camel.component.debezium.postgres instead of having everything under the root package org.apache.camel.component.debezium.
* [org.apache.camel.upgrade.camel49.GcpSecretRecipe](https://docs.openrewrite.org/recipes/org/apache/camel/upgrade/camel49/gcpsecretrecipe): The syntax for retrieving a single field of a secret has been changed..
* [org.apache.camel.upgrade.camel49.HashicorpSecretRecipe](https://docs.openrewrite.org/recipes/org/apache/camel/upgrade/camel49/hashicorpsecretrecipe): The syntax for retrieving a single field of a secret has been changed..
* [org.apache.camel.upgrade.camel49.removedDependencies](https://docs.openrewrite.org/recipes/org/apache/camel/upgrade/camel49/removeddependencies): Removed deprecated components (camel-groovy-dsl, camel-js-dsl, camel-jsh-dsl, camel-kotlin-api, camel-kotlin-dsl).
* [org.apache.camel.upgrade.camel49.renamedAPIs](https://docs.openrewrite.org/recipes/org/apache/camel/upgrade/camel49/renamedapis): Renamed classes for API.
* [org.apache.camel.upgrade.customRecipes.LiteralRegexpConverterRecipe](https://docs.openrewrite.org/recipes/org/apache/camel/upgrade/customrecipes/literalregexpconverterrecipe): Replaces literal, groups from regexp can be used as ${0}, ${1}, ...
* [org.openrewrite.codemods.UI5](https://docs.openrewrite.org/recipes/codemods/ui5): Runs the [UI5 Linter](https://github.com/SAP/ui5-linter), a static code analysis tool for UI5 projects. It checks JavaScript, TypeScript, XML, JSON, and other files in your project and reports findings.
* [org.openrewrite.codemods.migrate.nextjs.v14_0.MetadataToViewportExport](https://docs.openrewrite.org/recipes/codemods/migrate/nextjs/v14_0/metadatatoviewportexport): This codemod migrates certain viewport metadata to `viewport` export.
* [org.openrewrite.codemods.migrate.nextjs.v6.UrlToWithrouter](https://docs.openrewrite.org/recipes/codemods/migrate/nextjs/v6/urltowithrouter): Transforms the deprecated automatically injected url property on top-level pages to using `withRouter` and the `router` property it injects. Read more [here](https://nextjs.org/docs/messages/url-deprecated).
* [org.openrewrite.codemods.migrate.nextjs.v8.WithampToConfig](https://docs.openrewrite.org/recipes/codemods/migrate/nextjs/v8/withamptoconfig): Transforms the `withAmp` HOC into Next.js 9 page configuration.
* [org.openrewrite.java.migrate.ChangeDefaultKeyStore](https://docs.openrewrite.org/recipes/java/migrate/changedefaultkeystore): In Java 11 the default keystore was updated from JKS to PKCS12. As a result, applications relying on KeyStore.getDefaultType() may encounter issues after migrating, unless their JKS keystore has been converted to PKCS12. This recipe returns default key store of `jks` when `KeyStore.getDefaultType()` method is called to use the pre Java 11 default keystore.
* [org.openrewrite.java.testing.search.FindUnitTests](https://docs.openrewrite.org/recipes/java/testing/search/findunittests): Produces a data table showing how methods are used in unit tests.
* [org.openrewrite.json.format.AutoFormat](https://docs.openrewrite.org/recipes/json/format/autoformat): Format JSON code using a standard comprehensive set of JSON formatting recipes.
* [org.openrewrite.json.format.WrappingAndBraces](https://docs.openrewrite.org/recipes/json/format/wrappingandbraces): Split members into separate lines in JSON.
* [org.openrewrite.recipes.JavaRecipeBestPractices](https://docs.openrewrite.org/recipes/recipes/javarecipebestpractices): Best practices for Java recipe development.
* [org.openrewrite.recipes.RecipeNullabilityBestPractices](https://docs.openrewrite.org/recipes/recipes/recipenullabilitybestpractices): Use JSpecify nullable annotations; drop Nonnull annotations; use `NullMarked` on `package-info.java` instead.
* [org.openrewrite.recipes.RecipeTestingBestPractices](https://docs.openrewrite.org/recipes/recipes/recipetestingbestpractices): Best practices for testing recipes.
* [org.openrewrite.recipes.rewrite.OpenRewriteRecipeBestPractices](https://docs.openrewrite.org/recipes/recipes/rewrite/openrewriterecipebestpractices): Best practices for OpenRewrite recipe development.
* [tech.picnic.errorprone.refasterrules.AssertJStringRulesRecipes$AssertThatStringContainsRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/assertjstringrulesrecipesusdassertthatstringcontainsrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatStringContains {
    
    @BeforeTemplate
    AbstractBooleanAssert<?> before(String string, CharSequence substring) {
        return assertThat(string.contains(substring)).isTrue();
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    AbstractStringAssert<?> after(String string, CharSequence substring) {
        return assertThat(string).contains(substring);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.AssertJStringRulesRecipes$AssertThatStringDoesNotContainRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/assertjstringrulesrecipesusdassertthatstringdoesnotcontainrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatStringDoesNotContain {
    
    @BeforeTemplate
    AbstractBooleanAssert<?> before(String string, CharSequence substring) {
        return assertThat(string.contains(substring)).isFalse();
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    AbstractStringAssert<?> after(String string, CharSequence substring) {
        return assertThat(string).doesNotContain(substring);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.AssertJThrowingCallableRulesRecipes$AssertThatThrownByIOExceptionRootCauseHasMessageRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/assertjthrowingcallablerulesrecipesusdassertthatthrownbyioexceptionrootcausehasmessagerecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatThrownByIOExceptionRootCauseHasMessage {
    
    @BeforeTemplate
    @SuppressWarnings(value = "AssertThatThrownByIOException")
    AbstractObjectAssert<?, ?> before(ThrowingCallable throwingCallable, String message) {
        return assertThatIOException().isThrownBy(throwingCallable).havingRootCause().withMessage(message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    AbstractObjectAssert<?, ?> after(ThrowingCallable throwingCallable, String message) {
        return assertThatThrownBy(throwingCallable).isInstanceOf(IOException.class).rootCause().hasMessage(message);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.AssertJThrowingCallableRulesRecipes$AssertThatThrownByIllegalArgumentExceptionRootCauseHasMessageRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/assertjthrowingcallablerulesrecipesusdassertthatthrownbyillegalargumentexceptionrootcausehasmessagerecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatThrownByIllegalArgumentExceptionRootCauseHasMessage {
    
    @BeforeTemplate
    @SuppressWarnings(value = "AssertThatThrownByIllegalArgumentException")
    AbstractObjectAssert<?, ?> before(ThrowingCallable throwingCallable, String message) {
        return assertThatIllegalArgumentException().isThrownBy(throwingCallable).havingRootCause().withMessage(message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    AbstractObjectAssert<?, ?> after(ThrowingCallable throwingCallable, String message) {
        return assertThatThrownBy(throwingCallable).isInstanceOf(IllegalArgumentException.class).rootCause().hasMessage(message);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.AssertJThrowingCallableRulesRecipes$AssertThatThrownByIllegalStateExceptionRootCauseHasMessageRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/assertjthrowingcallablerulesrecipesusdassertthatthrownbyillegalstateexceptionrootcausehasmessagerecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatThrownByIllegalStateExceptionRootCauseHasMessage {
    
    @BeforeTemplate
    @SuppressWarnings(value = "AssertThatThrownByIllegalStateException")
    AbstractObjectAssert<?, ?> before(ThrowingCallable throwingCallable, String message) {
        return assertThatIllegalStateException().isThrownBy(throwingCallable).havingRootCause().withMessage(message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    AbstractObjectAssert<?, ?> after(ThrowingCallable throwingCallable, String message) {
        return assertThatThrownBy(throwingCallable).isInstanceOf(IllegalStateException.class).rootCause().hasMessage(message);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.AssertJThrowingCallableRulesRecipes$AssertThatThrownByNullPointerExceptionRootCauseHasMessageRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/assertjthrowingcallablerulesrecipesusdassertthatthrownbynullpointerexceptionrootcausehasmessagerecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatThrownByNullPointerExceptionRootCauseHasMessage {
    
    @BeforeTemplate
    @SuppressWarnings(value = "AssertThatThrownByNullPointerException")
    AbstractObjectAssert<?, ?> before(ThrowingCallable throwingCallable, String message) {
        return assertThatNullPointerException().isThrownBy(throwingCallable).havingRootCause().withMessage(message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    AbstractObjectAssert<?, ?> after(ThrowingCallable throwingCallable, String message) {
        return assertThatThrownBy(throwingCallable).isInstanceOf(NullPointerException.class).rootCause().hasMessage(message);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.AssertJThrowingCallableRulesRecipes$AssertThatThrownByRootCauseHasMessageRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/assertjthrowingcallablerulesrecipesusdassertthatthrownbyrootcausehasmessagerecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatThrownByRootCauseHasMessage {
    
    @BeforeTemplate
    @SuppressWarnings(value = "AssertThatThrownByAsInstanceOfThrowable")
    AbstractObjectAssert<?, ?> before(ThrowingCallable throwingCallable, Class<? extends Throwable> exceptionType, String message) {
        return assertThatExceptionOfType(exceptionType).isThrownBy(throwingCallable).havingRootCause().withMessage(message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    AbstractObjectAssert<?, ?> after(ThrowingCallable throwingCallable, Class<? extends Throwable> exceptionType, String message) {
        return assertThatThrownBy(throwingCallable).isInstanceOf(exceptionType).rootCause().hasMessage(message);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.CharSequenceRulesRecipes](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/charsequencerulesrecipes): Refaster rules related to expressions dealing with `CharSequence`s [Source](https://error-prone.picnic.tech/refasterrules/CharSequenceRules).
* [tech.picnic.errorprone.refasterrules.CharSequenceRulesRecipes$CharSequenceIsEmptyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/charsequencerulesrecipesusdcharsequenceisemptyrecipe): Prefer `CharSequence#isEmpty()` over alternatives that consult the char sequence's length
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatBooleanArrayContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatbooleanarraycontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatBooleanArrayContainsExactly {
    
    @BeforeTemplate
    void before(boolean[] actual, boolean[] expected) {
        assertArrayEquals(expected, actual);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(boolean[] actual, boolean[] expected) {
        assertThat(actual).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatBooleanArrayWithFailMessageContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatbooleanarraywithfailmessagecontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatBooleanArrayWithFailMessageContainsExactly {
    
    @BeforeTemplate
    void before(boolean[] actual, String message, boolean[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(boolean[] actual, String message, boolean[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatBooleanArrayWithFailMessageSupplierContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatbooleanarraywithfailmessagesuppliercontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatBooleanArrayWithFailMessageSupplierContainsExactly {
    
    @BeforeTemplate
    void before(boolean[] actual, Supplier<String> message, boolean[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(boolean[] actual, Supplier<String> message, boolean[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatByteArrayContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatbytearraycontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatByteArrayContainsExactly {
    
    @BeforeTemplate
    void before(byte[] actual, byte[] expected) {
        assertArrayEquals(expected, actual);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(byte[] actual, byte[] expected) {
        assertThat(actual).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatByteArrayWithFailMessageContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatbytearraywithfailmessagecontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatByteArrayWithFailMessageContainsExactly {
    
    @BeforeTemplate
    void before(byte[] actual, String message, byte[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(byte[] actual, String message, byte[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatByteArrayWithFailMessageSupplierContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatbytearraywithfailmessagesuppliercontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatByteArrayWithFailMessageSupplierContainsExactly {
    
    @BeforeTemplate
    void before(byte[] actual, Supplier<String> message, byte[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(byte[] actual, Supplier<String> message, byte[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatCharArrayContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatchararraycontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatCharArrayContainsExactly {
    
    @BeforeTemplate
    void before(char[] actual, char[] expected) {
        assertArrayEquals(expected, actual);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(char[] actual, char[] expected) {
        assertThat(actual).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatCharArrayWithFailMessageContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatchararraywithfailmessagecontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatCharArrayWithFailMessageContainsExactly {
    
    @BeforeTemplate
    void before(char[] actual, String message, char[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(char[] actual, String message, char[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatCharArrayWithFailMessageSupplierContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatchararraywithfailmessagesuppliercontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatCharArrayWithFailMessageSupplierContainsExactly {
    
    @BeforeTemplate
    void before(char[] actual, Supplier<String> message, char[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(char[] actual, Supplier<String> message, char[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatDoubleArrayContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatdoublearraycontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatDoubleArrayContainsExactly {
    
    @BeforeTemplate
    void before(double[] actual, double[] expected) {
        assertArrayEquals(expected, actual);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(double[] actual, double[] expected) {
        assertThat(actual).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatDoubleArrayContainsExactlyWithOffsetRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatdoublearraycontainsexactlywithoffsetrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatDoubleArrayContainsExactlyWithOffset {
    
    @BeforeTemplate
    void before(double[] actual, double[] expected, double delta) {
        assertArrayEquals(expected, actual, delta);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(double[] actual, double[] expected, double delta) {
        assertThat(actual).containsExactly(expected, offset(delta));
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatDoubleArrayWithFailMessageContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatdoublearraywithfailmessagecontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatDoubleArrayWithFailMessageContainsExactly {
    
    @BeforeTemplate
    void before(double[] actual, String message, double[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(double[] actual, String message, double[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatDoubleArrayWithFailMessageContainsExactlyWithOffsetRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatdoublearraywithfailmessagecontainsexactlywithoffsetrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatDoubleArrayWithFailMessageContainsExactlyWithOffset {
    
    @BeforeTemplate
    void before(double[] actual, String message, double[] expected, double delta) {
        assertArrayEquals(expected, actual, delta, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(double[] actual, String message, double[] expected, double delta) {
        assertThat(actual).withFailMessage(message).containsExactly(expected, offset(delta));
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatDoubleArrayWithFailMessageSupplierContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatdoublearraywithfailmessagesuppliercontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatDoubleArrayWithFailMessageSupplierContainsExactly {
    
    @BeforeTemplate
    void before(double[] actual, Supplier<String> message, double[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(double[] actual, Supplier<String> message, double[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatDoubleArrayWithFailMessageSupplierContainsExactlyWithOffsetRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatdoublearraywithfailmessagesuppliercontainsexactlywithoffsetrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatDoubleArrayWithFailMessageSupplierContainsExactlyWithOffset {
    
    @BeforeTemplate
    void before(double[] actual, Supplier<String> messageSupplier, double[] expected, double delta) {
        assertArrayEquals(expected, actual, delta, messageSupplier);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(double[] actual, Supplier<String> messageSupplier, double[] expected, double delta) {
        assertThat(actual).withFailMessage(messageSupplier).containsExactly(expected, offset(delta));
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatFloatArrayContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatfloatarraycontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatFloatArrayContainsExactly {
    
    @BeforeTemplate
    void before(float[] actual, float[] expected) {
        assertArrayEquals(expected, actual);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(float[] actual, float[] expected) {
        assertThat(actual).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatFloatArrayContainsExactlyWithOffsetRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatfloatarraycontainsexactlywithoffsetrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatFloatArrayContainsExactlyWithOffset {
    
    @BeforeTemplate
    void before(float[] actual, float[] expected, float delta) {
        assertArrayEquals(expected, actual, delta);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(float[] actual, float[] expected, float delta) {
        assertThat(actual).containsExactly(expected, offset(delta));
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatFloatArrayWithFailMessageContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatfloatarraywithfailmessagecontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatFloatArrayWithFailMessageContainsExactly {
    
    @BeforeTemplate
    void before(float[] actual, String message, float[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(float[] actual, String message, float[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatFloatArrayWithFailMessageContainsExactlyWithOffsetRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatfloatarraywithfailmessagecontainsexactlywithoffsetrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatFloatArrayWithFailMessageContainsExactlyWithOffset {
    
    @BeforeTemplate
    void before(float[] actual, String message, float[] expected, float delta) {
        assertArrayEquals(expected, actual, delta, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(float[] actual, String message, float[] expected, float delta) {
        assertThat(actual).withFailMessage(message).containsExactly(expected, offset(delta));
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatFloatArrayWithFailMessageSupplierContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatfloatarraywithfailmessagesuppliercontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatFloatArrayWithFailMessageSupplierContainsExactly {
    
    @BeforeTemplate
    void before(float[] actual, Supplier<String> message, float[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(float[] actual, Supplier<String> message, float[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatFloatArrayWithFailMessageSupplierContainsExactlyWithOffsetRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatfloatarraywithfailmessagesuppliercontainsexactlywithoffsetrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatFloatArrayWithFailMessageSupplierContainsExactlyWithOffset {
    
    @BeforeTemplate
    void before(float[] actual, Supplier<String> message, float[] expected, float delta) {
        assertArrayEquals(expected, actual, delta, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(float[] actual, Supplier<String> message, float[] expected, float delta) {
        assertThat(actual).withFailMessage(message).containsExactly(expected, offset(delta));
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatIntArrayContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatintarraycontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatIntArrayContainsExactly {
    
    @BeforeTemplate
    void before(int[] actual, int[] expected) {
        assertArrayEquals(expected, actual);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(int[] actual, int[] expected) {
        assertThat(actual).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatIntArrayWithFailMessageContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatintarraywithfailmessagecontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatIntArrayWithFailMessageContainsExactly {
    
    @BeforeTemplate
    void before(int[] actual, String message, int[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(int[] actual, String message, int[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatIntArrayWithFailMessageSupplierContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatintarraywithfailmessagesuppliercontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatIntArrayWithFailMessageSupplierContainsExactly {
    
    @BeforeTemplate
    void before(int[] actual, Supplier<String> message, int[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(int[] actual, Supplier<String> message, int[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatLongArrayContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatlongarraycontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatLongArrayContainsExactly {
    
    @BeforeTemplate
    void before(long[] actual, long[] expected) {
        assertArrayEquals(expected, actual);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(long[] actual, long[] expected) {
        assertThat(actual).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatLongArrayWithFailMessageContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatlongarraywithfailmessagecontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatLongArrayWithFailMessageContainsExactly {
    
    @BeforeTemplate
    void before(long[] actual, String message, long[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(long[] actual, String message, long[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatLongArrayWithFailMessageSupplierContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatlongarraywithfailmessagesuppliercontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatLongArrayWithFailMessageSupplierContainsExactly {
    
    @BeforeTemplate
    void before(long[] actual, Supplier<String> message, long[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(long[] actual, Supplier<String> message, long[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatObjectArrayContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatobjectarraycontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatObjectArrayContainsExactly {
    
    @BeforeTemplate
    void before(Object[] actual, Object[] expected) {
        assertArrayEquals(expected, actual);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(Object[] actual, Object[] expected) {
        assertThat(actual).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatObjectArrayWithFailMessageContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatobjectarraywithfailmessagecontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatObjectArrayWithFailMessageContainsExactly {
    
    @BeforeTemplate
    void before(Object[] actual, String message, Object[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(Object[] actual, String message, Object[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatObjectArrayWithFailMessageSupplierContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatobjectarraywithfailmessagesuppliercontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatObjectArrayWithFailMessageSupplierContainsExactly {
    
    @BeforeTemplate
    void before(Object[] actual, Supplier<String> message, Object[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(Object[] actual, Supplier<String> message, Object[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatShortArrayContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatshortarraycontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatShortArrayContainsExactly {
    
    @BeforeTemplate
    void before(short[] actual, short[] expected) {
        assertArrayEquals(expected, actual);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(short[] actual, short[] expected) {
        assertThat(actual).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatShortArrayWithFailMessageContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatshortarraywithfailmessagecontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatShortArrayWithFailMessageContainsExactly {
    
    @BeforeTemplate
    void before(short[] actual, String message, short[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(short[] actual, String message, short[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.JUnitToAssertJRulesRecipes$AssertThatShortArrayWithFailMessageSupplierContainsExactlyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/junittoassertjrulesrecipesusdassertthatshortarraywithfailmessagesuppliercontainsexactlyrecipe): Recipe created for the following Refaster template:
```java
static final class AssertThatShortArrayWithFailMessageSupplierContainsExactly {
    
    @BeforeTemplate
    void before(short[] actual, Supplier<String> message, short[] expected) {
        assertArrayEquals(expected, actual, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(short[] actual, Supplier<String> message, short[] expected) {
        assertThat(actual).withFailMessage(message).containsExactly(expected);
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.ReactorRulesRecipes$StepVerifierVerifyDurationRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/reactorrulesrecipesusdstepverifierverifydurationrecipe): Prefer `StepVerifier#verify(Duration)` over a dangling `StepVerifier#verifyThenAssertThat(Duration)`
* [tech.picnic.errorprone.refasterrules.ReactorRulesRecipes$StepVerifierVerifyLaterRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/reactorrulesrecipesusdstepverifierverifylaterrecipe): Don't unnecessarily invoke `StepVerifier#verifyLater()` multiple times
* [tech.picnic.errorprone.refasterrules.ReactorRulesRecipes$StepVerifierVerifyRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/reactorrulesrecipesusdstepverifierverifyrecipe): Prefer `StepVerifier#verify()` over a dangling `StepVerifier#verifyThenAssertThat()`
* [tech.picnic.errorprone.refasterrules.TestNGToAssertJRulesRecipes$AssertEqualDoubleArraysWithDeltaRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/testngtoassertjrulesrecipesusdassertequaldoublearrayswithdeltarecipe): Recipe created for the following Refaster template:
```java
static final class AssertEqualDoubleArraysWithDelta {
    
    @BeforeTemplate
    void before(double[] actual, double[] expected, double delta) {
        assertEquals(actual, expected, delta);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(double[] actual, double[] expected, double delta) {
        assertThat(actual).containsExactly(expected, offset(delta));
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.TestNGToAssertJRulesRecipes$AssertEqualDoubleArraysWithDeltaWithMessageRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/testngtoassertjrulesrecipesusdassertequaldoublearrayswithdeltawithmessagerecipe): Recipe created for the following Refaster template:
```java
static final class AssertEqualDoubleArraysWithDeltaWithMessage {
    
    @BeforeTemplate
    void before(double[] actual, String message, double[] expected, double delta) {
        assertEquals(actual, expected, delta, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(double[] actual, String message, double[] expected, double delta) {
        assertThat(actual).withFailMessage(message).containsExactly(expected, offset(delta));
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.TestNGToAssertJRulesRecipes$AssertEqualFloatArraysWithDeltaRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/testngtoassertjrulesrecipesusdassertequalfloatarrayswithdeltarecipe): Recipe created for the following Refaster template:
```java
static final class AssertEqualFloatArraysWithDelta {
    
    @BeforeTemplate
    void before(float[] actual, float[] expected, float delta) {
        assertEquals(actual, expected, delta);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(float[] actual, float[] expected, float delta) {
        assertThat(actual).containsExactly(expected, offset(delta));
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.TestNGToAssertJRulesRecipes$AssertEqualFloatArraysWithDeltaWithMessageRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/testngtoassertjrulesrecipesusdassertequalfloatarrayswithdeltawithmessagerecipe): Recipe created for the following Refaster template:
```java
static final class AssertEqualFloatArraysWithDeltaWithMessage {
    
    @BeforeTemplate
    void before(float[] actual, String message, float[] expected, float delta) {
        assertEquals(actual, expected, delta, message);
    }
    
    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void after(float[] actual, String message, float[] expected, float delta) {
        assertThat(actual).withFailMessage(message).containsExactly(expected, offset(delta));
    }
}
```
.
* [tech.picnic.errorprone.refasterrules.TimeRulesRecipes$InstantIdentityRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/timerulesrecipesusdinstantidentityrecipe): Don't unnecessarily transform an `Instant` to an equivalent instance
* [tech.picnic.errorprone.refasterrules.TimeRulesRecipes$InstantTruncatedToMillisecondsRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/timerulesrecipesusdinstanttruncatedtomillisecondsrecipe): Note that `Instant#toEpochMilli()` throws an `ArithmeticException` for dates
  very far in the past or future, while the suggested alternative doesn't.
* [tech.picnic.errorprone.refasterrules.TimeRulesRecipes$InstantTruncatedToSecondsRecipe](https://docs.openrewrite.org/recipes/tech/picnic/errorprone/refasterrules/timerulesrecipesusdinstanttruncatedtosecondsrecipe): Prefer `Instant#truncatedTo(TemporalUnit)` over less obvious alternatives

## Removed Recipes

* **io.moderne.ai.SpellCheckCommentsInFrenchPomXml**: Use spellchecker to fix mis-encoded French comments in pom.xml files. Mis-encoded comments will contain either '?' or '�'.
* **org.openrewrite.codemods.migrate.nextjs.v14_0.UseViewportExport**: This codemod migrates certain viewport metadata to `viewport` export.
  See [documentation](https://nextjs.org/docs/app/building-your-application/upgrading/codemods#metadata-to-viewport-export) for more information.
* **org.openrewrite.codemods.migrate.nextjs.v6.UrlToWithRouter**: Transforms the deprecated automatically injected url property on top level pages to using `withRouter` and the `router`  property it injects. Read more here: https://nextjs.org/docs/messages/url-deprecated
  See [documentation](https://nextjs.org/docs/app/building-your-application/upgrading/codemods#url-to-withrouter)
  for more information.
* **org.openrewrite.codemods.migrate.nextjs.v8.WithAmpToConfig**: Transforms the `withAmp` HOC into Next.js 9 page configuration.
  See [documentation](https://nextjs.org/docs/app/building-your-application/upgrading/codemods#withamp-to-config)
  for more information.
* **org.openrewrite.java.camel.migrate.ChangePropertyValue**: Apache Camel API migration from version 3.20 or higher to 4.0. Removal of deprecated APIs, which could be part of the application.properties.
* **tech.picnic.errorprone.refasterrules.AssertJStringRulesRecipes$AssertThatStringIsEmptyRecipe**: Recipe created for the following Refaster template:
```java
static final class AssertThatStringIsEmpty {

  @BeforeTemplate
  void before(String string) {
    assertThat(string.isEmpty()).isTrue();
  }

  @AfterTemplate
  @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
  void after(String string) {
    assertThat(string).isEmpty();
  }
}
```
.
* **tech.picnic.errorprone.refasterrules.AssertJStringRulesRecipes$AssertThatStringIsNotEmptyRecipe**: Recipe created for the following Refaster template:
```java
static final class AssertThatStringIsNotEmpty {

  @BeforeTemplate
  AbstractAssert<?, ?> before(String string) {
    return assertThat(string.isEmpty()).isFalse();
  }

  @AfterTemplate
  @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
  AbstractAssert<?, ?> after(String string) {
    return assertThat(string).isNotEmpty();
  }
}
```
.
* **tech.picnic.errorprone.refasterrules.AssertJThrowingCallableRulesRecipes$AssertThatThrownByRecipe**: Recipe created for the following Refaster template:
```java
static final class AssertThatThrownBy {

  @BeforeTemplate
  AbstractObjectAssert<?, ?> before(ThrowingCallable throwingCallable, Class<? extends Throwable> exceptionType) {
    return assertThatExceptionOfType(exceptionType).isThrownBy(throwingCallable);
  }

  @AfterTemplate
  @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
  AbstractObjectAssert<?, ?> after(ThrowingCallable throwingCallable, Class<? extends Throwable> exceptionType) {
    return assertThatThrownBy(throwingCallable).isInstanceOf(exceptionType);
  }
}
```
.
* **tech.picnic.errorprone.refasterrules.BugCheckerRulesRecipes$ConstantsFormatRecipe**: Recipe created for the following Refaster template:
```java
static final class ConstantsFormat {

  @BeforeTemplate
  String before(String value) {
    return String.format("\"%s\"", Convert.quote(value));
  }

  @AfterTemplate
  String after(String value) {
    return Constants.format(value);
  }
}
```
.

## Changed Recipes

* [org.openrewrite.FindParseFailures](https://docs.openrewrite.org/recipes/core/findparsefailures) was changed:
  * Old Options:
    * `maxSnippetLength: { type: Integer, required: false }`
    * `parserType: { type: String, required: false }`
    * `stackTrace: { type: String, required: false }`
  * New Options:
    * `createdAfter: { type: String, required: false }`
    * `maxSnippetLength: { type: Integer, required: false }`
    * `parserType: { type: String, required: false }`
    * `stackTrace: { type: String, required: false }`
* [org.openrewrite.java.dependencies.DependencyList](https://docs.openrewrite.org/recipes/java/dependencies/dependencylist) was changed:
  * Old Options:
    * `includeTransitive: { type: boolean, required: true }`
    * `scope: { type: Scope, required: true }`
  * New Options:
    * `includeTransitive: { type: boolean, required: true }`
    * `scope: { type: Scope, required: true }`
    * `validateResolvable: { type: boolean, required: true }`
* [org.openrewrite.yaml.MergeYaml](https://docs.openrewrite.org/recipes/yaml/mergeyaml) was changed:
  * Old Options:
    * `acceptTheirs: { type: Boolean, required: false }`
    * `filePattern: { type: String, required: false }`
    * `key: { type: String, required: true }`
    * `objectIdentifyingProperty: { type: String, required: false }`
    * `yaml: { type: String, required: true }`
  * New Options:
    * `acceptTheirs: { type: Boolean, required: false }`
    * `filePattern: { type: String, required: false }`
    * `insertBefore: { type: String, required: false }`
    * `key: { type: String, required: true }`
    * `objectIdentifyingProperty: { type: String, required: false }`
    * `yaml: { type: String, required: true }`
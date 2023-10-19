# Snapshot (2023-08-07)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.java.apache.httpclient4.MappingDeprecatedClasses](https://docs.openrewrite.org/reference/recipes/java/apache/httpclient4/mappingdeprecatedclasses): Uses new classes/methods instead of the deprecated ones 
* [org.openrewrite.java.apache.httpclient4.MigrateDefaultHttpClient](https://docs.openrewrite.org/reference/recipes/java/apache/httpclient4/migratedefaulthttpclient): Since DefaultHttpClient is deprecated, we need to change it to the CloseableHttpClient. It only covers the default scenario with no custom HttpParams or ConnectionManager. 
* [org.openrewrite.java.apache.httpclient4.UpgradeApacheHttpClient_4_5](https://docs.openrewrite.org/reference/recipes/java/apache/httpclient4/upgradeapachehttpclient_4_5): Migrate applications to the latest Apache HttpClient 4.5.x release. This recipe modifies  application's build files, make changes to deprecated/preferred APIs, and migrates configuration settings that have changes between versions. 
* [org.openrewrite.java.apache.httpclient5.AddTimeUnitArgument](https://docs.openrewrite.org/reference/recipes/java/apache/httpclient5/addtimeunitargument): In Apache Http Client 5.x migration, an extra TimeUnit argument is required in the timeout and duration methods. Previously in 4.x, all these methods were implicitly having the timeout or duration expressed in milliseconds, but in 5.x the unit of the timeout or duration is required. So, by default this recipe adds `TimeUnit.MILLISECONDS`, it is possible to specify this as a parameter. Since all affected methods of the Apache Http Client 5.x migration only have one integer/long argument, the recipe applies with matched method invocations of exactly one parameter. 
* [org.openrewrite.java.apache.httpclient5.NewStatusLine](https://docs.openrewrite.org/reference/recipes/java/apache/httpclient5/newstatusline): `HttpResponse::getStatusLine()` was deprecated in 4.x, so we replace it for `new StatusLine(HttpResponse)`. Ideally we will try to simplify method chains for `getStatusCode`, `getProtocolVersion` and `getReasonPhrase`, but there are some scenarios where the `StatusLine` object is assigned or used directly, and we need to instantiate the object. 
* [org.openrewrite.java.apache.httpclient5.StatusLine](https://docs.openrewrite.org/reference/recipes/java/apache/httpclient5/statusline): Migrates deprecated methods to their equivalent ones in 5.x 
* [org.openrewrite.java.apache.httpclient5.UpgradeApacheHttpClient_5](https://docs.openrewrite.org/reference/recipes/java/apache/httpclient5/upgradeapachehttpclient_5): Migrate applications to the latest Apache HttpClient 5.x release. This recipe will modify an application's build files, make changes to deprecated/preferred APIs, and migrate configuration settings that have changes between versions. 
* [org.openrewrite.java.apache.httpclient5.UpgradeApacheHttpClient_5_ClassMapping](https://docs.openrewrite.org/reference/recipes/java/apache/httpclient5/upgradeapachehttpclient_5_classmapping): Mapping of all the compatible classes of ApacheHttpClient 5.x from 4.x. 
* [org.openrewrite.java.apache.httpclient5.UpgradeApacheHttpClient_5_DeprecatedMethods](https://docs.openrewrite.org/reference/recipes/java/apache/httpclient5/upgradeapachehttpclient_5_deprecatedmethods): Migrates deprecated methods to their equivalent ones in 5.x 
* [org.openrewrite.java.apache.httpclient5.UpgradeApacheHttpClient_5_TimeUnit](https://docs.openrewrite.org/reference/recipes/java/apache/httpclient5/upgradeapachehttpclient_5_timeunit): Apache HttpClient 5.x Timeout and duration methods need an extra the TimeUnit argument. This recipe uses milliseconds as a default unit. 
* [org.openrewrite.java.testing.assertj.SimplifyChainedAssertJAssertion](https://docs.openrewrite.org/reference/recipes/java/testing/assertj/simplifychainedassertjassertion): Many AssertJ chained assertions have dedicated assertions that function the same. It is best to use the dedicated assertions. 
* [org.openrewrite.java.testing.assertj.SimplifyChainedAssertJAssertions](https://docs.openrewrite.org/reference/recipes/java/testing/assertj/simplifychainedassertjassertions): Replace AssertJ chained assertions with dedicated assertions that function the same. 
* [org.openrewrite.java.testing.hamcrest.HamcrestOfMatchersToAssertJ](https://docs.openrewrite.org/reference/recipes/java/testing/hamcrest/hamcrestofmatcherstoassertj): Migrate the `anyOf` Hamcrest Matcher to AssertJ's `satisfiesAnyOf` assertion. 
* [org.openrewrite.staticanalysis.TestRecipe](https://docs.openrewrite.org/reference/recipes/staticanalysis/testrecipe): for testing nonsense 
* [org.openrewrite.text.FindMultiselect](https://docs.openrewrite.org/reference/recipes/text/findmultiselect): Search for text, treating all textual sources as plain text. This version of the recipe exists to experiment with multiselect recipe options. 

## Removed Recipes

* **org.openrewrite.java.testing.assertj.UseExplicitContains**: Convert AssertJ `assertThat(collection.contains(element)).isTrue()` to `assertThat(collection).contains(element)` and `assertThat(collection.contains(element)).isFalse()` to `assertThat(collection).doesNotContain(element)`. 
* **org.openrewrite.java.testing.assertj.UseExplicitIsEmpty**: Convert AssertJ `assertThat(collection.isEmpty()).isTrue()` to `assertThat(collection).isEmpty()` and `assertThat(collection.isEmpty()).isFalse()` to `assertThat(collection).isNotEmpty()`. 
* **org.openrewrite.java.testing.assertj.UseExplicitSize**: Convert `assertThat(collection.size()).isEqualTo(Y)` to AssertJ's `assertThat(collection).hasSize()`. 
* **org.openrewrite.java.testing.hamcrest.FlattenAllOf**: Convert Hamcrest `allOf(Matcher...)` to individual `assertThat` statements for easier migration. 
* **org.openrewrite.java.testing.hamcrest.HamcrestAnyOfToAssertJ**: Migrate the `anyOf` Hamcrest Matcher to AssertJ's `satisfiesAnyOf` assertion. 


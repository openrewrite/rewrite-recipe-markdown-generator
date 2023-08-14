# Snapshot (2023-08-14)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.kotlin.format.AutoFormat](https://docs.openrewrite.org/reference/recipes/kotlin/format/autoformat): Format Kotlin code using a standard comprehensive set of Kotlin formatting recipes. 
* [org.openrewrite.search.FindParseToPrintInequality](https://docs.openrewrite.org/reference/recipes/search/findparsetoprintinequality): OpenRewrite `Parser` implementations should produce `SourceFile` objects whose `printAll()` method should be byte-for-byte equivalent with the original source file. When this isn't true, recipes can still run on the `SourceFile` and even produce diffs, but the diffs would fail to apply as a patch to the original source file. Most `Parser` use `Parser#requirePrintEqualsInput` to produce a `ParseError` when they fail to produce a `SourceFile` that is print idempotent. 
* [org.openrewrite.xml.ChangeNamespaceValue](https://docs.openrewrite.org/reference/recipes/xml/changenamespacevalue): Alters XML Attribute value within specified element of a specific resource versions. 

## Removed Recipes

* **org.openrewrite.FindBuildToolFailures**: This recipe explores build tool failures after an LST is produced for classifying the types of failures that can occur and prioritizing fixes according to the most common problems. 

## Changed Recipes

* [org.openrewrite.text.Find](https://docs.openrewrite.org/reference/recipes/text/find) was changed:
  * Old Options:
    * `caseSensitive: { type: Boolean, required: false }`
    * `dotAll: { type: Boolean, required: false }`
    * `filePattern: { type: String, required: true }`
    * `find: { type: String, required: true }`
    * `multiline: { type: Boolean, required: false }`
    * `regex: { type: Boolean, required: false }`
  * New Options:
    * `caseSensitive: { type: Boolean, required: false }`
    * `dotAll: { type: Boolean, required: false }`
    * `filePattern: { type: String, required: false }`
    * `find: { type: String, required: true }`
    * `multiline: { type: Boolean, required: false }`
    * `regex: { type: Boolean, required: false }`
* [org.openrewrite.maven.search.EffectiveMavenRepositories](https://docs.openrewrite.org/reference/recipes/maven/search/effectivemavenrepositories) was changed:
  * Old Options:
    * `None`
  * New Options:
    * `useMarkers: { type: Boolean, required: false }`
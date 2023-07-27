# Snapshot (2023-07-27)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.java.migrate.io.ReplaceFileInOrOutputStreamFinalizeWithClose](https://docs.openrewrite.org/reference/recipes/java/migrate/io/replacefileinoroutputstreamfinalizewithclose): Replace invocations of the deprecated `finalize()` method on `FileInputStream` and `FileOutputStream` with `close()`. 
* [org.openrewrite.java.migrate.lombok.UpdateLombokToJava11](https://docs.openrewrite.org/reference/recipes/java/migrate/lombok/updatelomboktojava11): Update Lombok dependency to a version that is compatible with Java 11 and migrate experimental Lombok types that have been promoted. 
* [org.openrewrite.java.testing.hamcrest.HamcrestAnyOfToAssertJ](https://docs.openrewrite.org/reference/recipes/java/testing/hamcrest/hamcrestanyoftoassertj): Migrate the `anyOf` Hamcrest Matcher to AssertJ's `satisfiesAnyOf` assertion. 

## Removed Recipes

* **org.openrewrite.java.cleanup.SimplifyBooleanExpression**: Checks for over-complicated boolean expressions. Finds code like `if (b == true)`, `b || true`, `!false`, etc. 
* **org.openrewrite.java.cleanup.SimplifyBooleanReturn**: Simplifies Boolean expressions by removing redundancies, e.g.: `a && true` simplifies to `a`. 
* **org.openrewrite.java.cleanup.UnnecessaryParentheses**: Removes unnecessary parentheses from code where extra parentheses pairs are redundant. 
* **org.openrewrite.java.migrate.lombok.UpdateLombokToJava17**: Update Lombok dependency to a version that is compatible with Java 17 and migrate experimental Lombok types that have been promoted. 

## Changed Recipes

* [org.openrewrite.gradle.ChangeDependency](https://docs.openrewrite.org/reference/recipes/gradle/changedependency) was changed:
  * Old Options:
    * `newArtifactId: { type: String, required: false }`
    * `newGroupId: { type: String, required: false }`
    * `newVersion: { type: String, required: false }`
    * `oldArtifactId: { type: String, required: true }`
    * `oldGroupId: { type: String, required: true }`
    * `versionPattern: { type: String, required: false }`
  * New Options:
    * `newArtifactId: { type: String, required: false }`
    * `newGroupId: { type: String, required: false }`
    * `newVersion: { type: String, required: false }`
    * `oldArtifactId: { type: String, required: true }`
    * `oldGroupId: { type: String, required: true }`
    * `overrideManagedVersion: { type: Boolean, required: false }`
    * `versionPattern: { type: String, required: false }`
# Snapshot (2023-12-28)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.java.ai.ClassDefinitionLength](https://docs.openrewrite.org/recipes/java/ai/classdefinitionlength): Locates class definitions and predicts the number of token in each. 
* [org.openrewrite.java.ai.MethodDefinitionLength](https://docs.openrewrite.org/recipes/java/ai/methoddefinitionlength): Locates method definitions and predicts the number of token in each. 
* [org.openrewrite.java.logging.log4j.Slf4jToLog4j](https://docs.openrewrite.org/recipes/java/logging/log4j/slf4jtolog4j): Transforms code written using SLF4J to use Log4j 2.x API. 

## Removed Recipes

* **org.openrewrite.java.ClassDefinitionLength**: Locates class definitions and predicts the number of token in each. 
* **org.openrewrite.java.MethodDefinitionLength**: Locates method definitions and predicts the number of token in each. 

## Changed Recipes

* [org.openrewrite.java.search.HasJavaVersion](https://docs.openrewrite.org/recipes/java/search/hasjavaversion) was changed:
  * Old Options:
    * `checkTargetCompatibility: { type: Boolean, required: true }`
    * `version: { type: String, required: true }`
  * New Options:
    * `checkTargetCompatibility: { type: Boolean, required: false }`
    * `version: { type: String, required: true }`
* [org.openrewrite.maven.ChangePluginGroupIdAndArtifactId](https://docs.openrewrite.org/recipes/maven/changeplugingroupidandartifactid) was changed:
  * Old Options:
    * `newArtifactId: { type: String, required: false }`
    * `newGroupId: { type: String, required: false }`
    * `oldArtifactId: { type: String, required: true }`
    * `oldGroupId: { type: String, required: true }`
  * New Options:
    * `newArtifact: { type: String, required: false }`
    * `newGroupId: { type: String, required: false }`
    * `oldArtifactId: { type: String, required: true }`
    * `oldGroupId: { type: String, required: true }`
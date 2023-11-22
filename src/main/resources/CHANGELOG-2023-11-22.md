# 8.9.6 release (2023-11-22)

## New Recipes

* [org.openrewrite.java.ChangeAnnotationAttributeName](https://docs.openrewrite.org/recipes/java/changeannotationattributename): Some annotations accept arguments. This recipe renames an existing attribute. 

## Changed Recipes

* [org.openrewrite.maven.search.ParentPomInsight](https://docs.openrewrite.org/recipes/maven/search/parentpominsight) was changed:
  * Old Options:
    * `artifactIdPattern: { type: String, required: true }`
    * `groupIdPattern: { type: String, required: true }`
  * New Options:
    * `artifactIdPattern: { type: String, required: true }`
    * `groupIdPattern: { type: String, required: true }`
    * `version: { type: String, required: false }`
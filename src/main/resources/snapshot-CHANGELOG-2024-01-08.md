# Snapshot (2024-01-08)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.java.RemoveAnnotationAttribute](https://docs.openrewrite.org/recipes/java/removeannotationattribute): Some annotations accept arguments. This recipe removes an existing attribute. 
* [org.openrewrite.staticanalysis.SimplifyTernaryRecipes](https://docs.openrewrite.org/recipes/staticanalysis/simplifyternaryrecipes): Refaster template recipes for `org.openrewrite.staticanalysis.SimplifyTernary`. 
* [org.openrewrite.staticanalysis.SimplifyTernaryRecipes$SimplifyTernaryFalseTrueRecipe](https://docs.openrewrite.org/recipes/staticanalysis/simplifyternaryrecipes$simplifyternaryfalsetruerecipe): Simplify `expr ? false : true` to `!expr`. 
* [org.openrewrite.staticanalysis.SimplifyTernaryRecipes$SimplifyTernaryTrueFalseRecipe](https://docs.openrewrite.org/recipes/staticanalysis/simplifyternaryrecipes$simplifyternarytruefalserecipe): Simplify `expr ? true : false` to `expr`. 

## Changed Recipes

* [org.openrewrite.java.migrate.UseJavaUtilBase64](https://docs.openrewrite.org/recipes/java/migrate/usejavautilbase64) was changed:
  * Old Options:
    * `None`
  * New Options:
    * `useMimeCoder: { type: boolean, required: false }`
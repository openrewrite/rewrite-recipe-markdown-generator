# Snapshot (2023-07-24)

{% hint style="info" %}
Want to learn how to use snapshot versions in your project? Check out our [snapshot version guide](/reference/snapshot-instructions.md).
{% endhint %}

## New Recipes

* [org.openrewrite.FindLstProvenance](https://docs.openrewrite.org/reference/recipes/findlstprovenance): Produces a data table showing what versions of OpenRewrite/Moderne tooling was used to produce a given LST. 
* [org.openrewrite.java.testing.hamcrest.HamcrestIsMatcherToAssertJ](https://docs.openrewrite.org/reference/recipes/java/testing/hamcrest/hamcrestismatchertoassertj): Migrate Hamcrest `is(Object)` to AssertJ `Assertions.assertThat(..)`. 
* [org.openrewrite.java.testing.junit5.UseXMLUnitLegacy](https://docs.openrewrite.org/reference/recipes/java/testing/junit5/usexmlunitlegacy): Migrates XMLUnit 1.x to XMLUnit legacy 2.x 
* [org.openrewrite.staticanalysis.SortedSetStreamToLinkedHashSet](https://docs.openrewrite.org/reference/recipes/staticanalysis/sortedsetstreamtolinkedhashset): Correct 'set.stream().sorted().collect(Collectors.toSet())' to 'set.stream().sorted().collect(LinkedHashSet::new)'. 


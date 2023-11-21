# X.XX.X release (2023-11-21)

## New Recipes

* [org.openrewrite.java.spring.boot3.EnableVirtualThreads](https://docs.openrewrite.org/recipes/java/spring/boot3/enablevirtualthreads): Set `spring.threads.virtual.enabled` to `true` in `application.properties` or `application.yml`. 
* [org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_2](https://docs.openrewrite.org/recipes/java/spring/boot3/upgradespringboot_3_2): Migrate applications to the latest Spring Boot 3.2 release. This recipe will modify an application's build files, make changes to deprecated/preferred APIs, and migrate configuration settings that have changes between versions. This recipe will also chain additional framework migrations (Spring Framework, Spring Data, etc) that are required as part of the migration to Spring Boot 3.1. 
* [org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_2](https://docs.openrewrite.org/recipes/java/spring/security6/upgradespringsecurity_6_2): Migrate applications to the latest Spring Security 6.2 release. This recipe will modify an application's build files, make changes to deprecated/preferred APIs, and migrate configuration settings that have changes between versions. 


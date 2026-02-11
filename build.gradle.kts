plugins {
    application
    id("org.jetbrains.kotlin.jvm").version("1.9.25")
    id("org.owasp.dependencycheck") version "latest.release"
}

dependencyCheck {
    analyzers.assemblyEnabled = false
    failBuildOnCVSS = 9.0F
    suppressionFile = "suppressions.xml"
    scanConfigurations = listOf("runtimeClasspath")
    nvd.apiKey = System.getenv("NVD_API_KEY")
    analyzers.centralEnabled = System.getenv("CENTRAL_ANALYZER_ENABLED").toBoolean()
    analyzers.ossIndex.username = System.getenv("OSSINDEX_USERNAME")
    analyzers.ossIndex.password = System.getenv("OSSINDEX_PASSWORD")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://central.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://maven.diffblue.com/snapshot") }
    gradlePluginPortal()
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
}

val recipeConf = configurations.create("recipe")

// Either `latest.release` or `latest.integration`
val rewriteVersion = "latest.release"

dependencies {
    // Platform dependencies (BOMs)
    implementation(platform("io.moderne.recipe:moderne-recipe-bom:latest.release"))
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Core implementation dependencies
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("info.picocli:picocli:latest.release")
    implementation("io.github.java-diff-utils:java-diff-utils:4.11")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.openrewrite:rewrite-core")
    implementation("org.openrewrite:rewrite-javascript")

    // Runtime dependencies
    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")

    // Test dependencies
    testImplementation("org.assertj:assertj-core:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Recipe configuration dependencies
    // Note: Not using BOM to show the latest patch versions of individual modules

    // Core rewrite modules (org.openrewrite)
    "recipe"("org.openrewrite:rewrite-core:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-csharp:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-docker:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-gradle:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-groovy:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-hcl:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-java:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-javascript:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-json:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-kotlin:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-maven:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-properties:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-protobuf:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-toml:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-xml:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-yaml:$rewriteVersion")

    // Additional core modules (versions only, recipes not yet included)
//    "recipe"("org.openrewrite:rewrite-cobol:$rewriteVersion")
//    "recipe"("org.openrewrite:rewrite-polyglot:$rewriteVersion")
//    "recipe"("org.openrewrite:rewrite-python:$rewriteVersion")
//    "recipe"("org.openrewrite:rewrite-templating:$rewriteVersion")

    // Recipe modules (org.openrewrite.recipe)
    "recipe"("org.openrewrite.meta:rewrite-analysis:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-ai-search:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-all:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-android:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-apache:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-azul:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-circleci:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-codemods:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-codemods-ng:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-compiled-analysis:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-concourse:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-cucumber-jvm:$rewriteVersion")
//    "recipe"("org.openrewrite.recipe:rewrite-diffblue:latest.integration") {
//        exclude(group = "org.openrewrite")
//        exclude(group = "org.openrewrite.recipe")
//    }
    "recipe"("org.openrewrite.recipe:rewrite-dotnet:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-dropwizard:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-feature-flags:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-github-actions:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-gitlab:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-hibernate:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-jackson:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-java-security:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-jenkins:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-joda:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-kubernetes:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-liberty:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-logging-frameworks:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-micrometer:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-micronaut:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-migrate-java:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-netty:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-nodejs:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-okhttp:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-openapi:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-prethink:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-quarkus:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-reactive-streams:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-rewrite:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-spring:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-spring-to-quarkus:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-sql:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-static-analysis:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-struts:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-terraform:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-testing-frameworks:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-third-party:$rewriteVersion")

    // Moderne recipe modules (io.moderne.recipe)
    "recipe"("io.moderne.recipe:rewrite-ai:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-angular:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-cryptography:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-devcenter:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-elastic:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-hibernate:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-java-application-server:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-jasperreports:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-kafka:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-program-analysis:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-prethink:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-react:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-spring:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-tapestry:$rewriteVersion")
    "recipe"("io.moderne.recipe:rewrite-vulncheck:$rewriteVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

tasks.withType<Test> {
    useJUnitPlatform()
}

application {
    mainClass.set("org.openrewrite.RecipeMarkdownGenerator")
}

tasks.named<JavaExec>("run").configure {
    maxHeapSize = "4g"
    val targetDir = layout.buildDirectory.dir("docs").get().asFile
    val moderneTargetDir = layout.buildDirectory.dir("moderne-docs").get().asFile

    val latestVersionsOnly = providers.gradleProperty("latestVersionsOnly").getOrElse("").equals("true")
    if (latestVersionsOnly) {
        // Additional modules whose versions we want to show, but not (yet) their recipes
        dependencies {
            "recipe"("org.openrewrite:rewrite-cobol:$rewriteVersion")
            "recipe"("org.openrewrite:rewrite-javascript:$rewriteVersion")
            "recipe"("org.openrewrite:rewrite-polyglot:$rewriteVersion")
            "recipe"("org.openrewrite:rewrite-python:$rewriteVersion")
            "recipe"("org.openrewrite:rewrite-templating:$rewriteVersion")
        }
    }

    // Collect all of the dependencies from recipeConf, then stuff them into a string representation
    val recipeModules = recipeConf.resolvedConfiguration.firstLevelModuleDependencies.flatMap { dep ->
        dep.moduleArtifacts.map { artifact ->
            "${dep.moduleGroup}:${dep.moduleName}:${dep.moduleVersion}:${artifact.file}"
        }
    }.joinToString(";")
    // recipeModules doesn't include transitive dependencies, but those are needed to load recipes and their descriptors
    val recipeClasspath = recipeConf.incoming.files.asSequence()
        .map { it.absolutePath }
        .joinToString(";")

    description = "Writes generated markdown docs to $targetDir and $moderneTargetDir"
    val arguments = mutableListOf(
        targetDir.toString(),
        recipeModules,
        recipeClasspath,
        latestVersion("org.openrewrite:rewrite-bom:$rewriteVersion"),
        latestVersion("org.openrewrite.recipe:rewrite-recipe-bom:$rewriteVersion"),
        latestVersion("io.moderne.recipe:moderne-recipe-bom:$rewriteVersion"),
        latestVersion("org.openrewrite:plugin:$rewriteVersion"),
        latestVersion("org.openrewrite.maven:rewrite-maven-plugin:$rewriteVersion"),
        moderneTargetDir.toString()
    )
    if (latestVersionsOnly) {
        arguments.add("--latest-versions-only")
    }
    args = arguments
    doFirst {
        logger.lifecycle("Recipe modules: ")
        logger.lifecycle(recipeModules.replace(";", "\n"))

        // Ensure no stale output from previous runs is in the output directories
        targetDir.deleteRecursively()
        targetDir.mkdirs()
        moderneTargetDir.deleteRecursively()
        moderneTargetDir.mkdirs()
    }
    doLast {
        this as JavaExec
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION") // IntelliJ says this is unnecessary, kotlin compiler disagrees
        logger.lifecycle("Wrote OpenRewrite docs to: file://${args!!.first()}")
        logger.lifecycle("Wrote Moderne docs to: file://$moderneTargetDir")
    }
}

defaultTasks = mutableListOf("run")

fun latestVersion(arg: String) =
    configurations.detachedConfiguration(dependencies.create(arg))
        .resolvedConfiguration
        .firstLevelModuleDependencies
        .first()
        .moduleVersion

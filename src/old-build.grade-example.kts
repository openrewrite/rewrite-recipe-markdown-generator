// This file is an example of what the build.gradle file would look like
// for diff logs on old versions of OpenRewrite. Some key things to note include:
// 1. `rewriteVersion` is a version number instead of something like `latest`
// 2. Not all `recipe` dependencies will exist at the same time as this release number. You will need to ensure that
//    artifacts that did not exist at that time are commented out or not included.
// 3. Most `recipe` dependencies will need their own specific version number that corresponds to the rewrite version
//    specified in this file. You can't rely on the bom for all of these.

plugins {
    application
    id("org.jetbrains.kotlin.jvm").version("1.7.20")
    id("org.owasp.dependencycheck") version "7.0.4.1"
}

dependencyCheck {
    analyzers.assemblyEnabled = false
    failBuildOnCVSS = 9.0F
    suppressionFile = "suppressions.xml"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    mavenCentral()
    gradlePluginPortal()
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
}

val recipeConf = configurations.create("recipe")
val rewriteVersion = "7.24.0"

// Used to determine what type of changelog to build up.
//   * "release"  : When making a changelog for larger releases of OpenRewrite
//   * "snapshot" : When making a changelog for snapshot releases on a weekly cadence.
val deployType = "snapshot"

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("info.picocli:picocli:latest.release")
    implementation("org.openrewrite:rewrite-core:7.24.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.+")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.+")
    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")

    "recipe"(platform("org.openrewrite:rewrite-bom:7.24.1"))
    "recipe"("org.openrewrite:rewrite-core")
    "recipe"("org.openrewrite:rewrite-groovy")
    "recipe"("org.openrewrite:rewrite-gradle")
    "recipe"("org.openrewrite:rewrite-hcl")
    "recipe"("org.openrewrite:rewrite-java")
    "recipe"("org.openrewrite:rewrite-json")
    "recipe"("org.openrewrite:rewrite-maven")
    "recipe"("org.openrewrite:rewrite-properties")
    "recipe"("org.openrewrite:rewrite-protobuf")
    "recipe"("org.openrewrite:rewrite-xml")
    "recipe"("org.openrewrite:rewrite-yaml")

    "recipe"("org.openrewrite.recipe:rewrite-circleci:1.8.0")
    "recipe"("org.openrewrite.recipe:rewrite-concourse:1.7.0")
    "recipe"("org.openrewrite.recipe:rewrite-github-actions:1.7.0")
    "recipe"("org.openrewrite.recipe:rewrite-java-security:1.12.0")
//    "recipe"("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-jhipster:1.7.0")
    "recipe"("org.openrewrite.recipe:rewrite-kubernetes:1.18.0")
    "recipe"("org.openrewrite.recipe:rewrite-logging-frameworks:1.8.0")
    "recipe"("org.openrewrite.recipe:rewrite-micronaut:1.12.0")
    "recipe"("org.openrewrite.recipe:rewrite-migrate-java:1.7.0")
    "recipe"("org.openrewrite.recipe:rewrite-quarkus:1.7.0")
    "recipe"("org.openrewrite.recipe:rewrite-spring:4.22.1")
    "recipe"("org.openrewrite.recipe:rewrite-terraform:1.6.0")
    "recipe"("org.openrewrite.recipe:rewrite-testing-frameworks:1.23.1")
//    "recipe"("org.openrewrite.recipe:rewrite-cloud-suitability-analyzer:$rewriteVersion")
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

application {
    mainClass.set("org.openrewrite.RecipeMarkdownGenerator")
}

tasks.named<JavaExec>("run").configure {
    val targetDir = File(project.buildDir, "docs")
    // Collect all of the dependencies from recipeConf, then stuff them into a string representation
    val recipeModules = recipeConf.resolvedConfiguration.firstLevelModuleDependencies.flatMap { dep ->
        dep.moduleArtifacts.map { artifact ->
            "${dep.moduleGroup}:${dep.moduleName}:${dep.moduleVersion}:${artifact.file}"
        }
    }.joinToString(";")
    // recipeModules doesn't include transitive dependencies, but those are needed to load recipes and their descriptors
    val recipeClasspath = recipeConf.resolvedConfiguration.files.asSequence()
        .map { it.absolutePath }
        .joinToString(";")

    val gradlePluginVersion = configurations.detachedConfiguration(dependencies.create("org.openrewrite:plugin:latest.release"))
        .resolvedConfiguration
        .firstLevelModuleDependencies
        .first()
        .moduleVersion

    val mavenPluginVersion = configurations.detachedConfiguration(dependencies.create("org.openrewrite.maven:rewrite-maven-plugin:latest.release"))
        .resolvedConfiguration
        .firstLevelModuleDependencies
        .first()
        .moduleVersion

    description = "Writes generated markdown docs to $targetDir"
    args = listOf(targetDir.toString(), recipeModules, recipeClasspath, gradlePluginVersion, mavenPluginVersion, deployType)
    doFirst {
        logger.lifecycle("Recipe modules: ")
        logger.lifecycle(recipeModules)

        // Ensure no stale output from previous runs is in the output directory
        targetDir.deleteRecursively()
        targetDir.mkdirs()
    }
    doLast {
        this as JavaExec
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION") // IntelliJ says this is unnecessary, kotlin compiler disagrees
        logger.lifecycle("Wrote generated docs to: ${args!!.first()}")
    }
}

defaultTasks = mutableListOf("run")

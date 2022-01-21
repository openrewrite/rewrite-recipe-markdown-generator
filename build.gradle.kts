buildscript {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    application
    id("org.jetbrains.kotlin.jvm").version("1.5.0")
    id("org.owasp.dependencycheck") version "6.5.3"
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
val rewriteVersion = "latest.release"
dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("info.picocli:picocli:latest.release")
    implementation("org.openrewrite:rewrite-core:$rewriteVersion")
    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")

    "recipe"("org.openrewrite:rewrite-core:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-groovy:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-gradle:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-hcl:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-java:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-json:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-maven:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-properties:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-xml:$rewriteVersion")
    "recipe"("org.openrewrite:rewrite-yaml:$rewriteVersion")

    "recipe"("org.openrewrite.recipe:rewrite-circleci:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-concourse:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-github-actions:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-java-security:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-jhipster:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-kubernetes:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-logging-frameworks:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-micronaut:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-migrate-java:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-quarkus:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-spring:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-terraform:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-testing-frameworks:$rewriteVersion")
}

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()

    options.isFork = true
    options.compilerArgs.addAll(listOf("--release", "8"))
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
    args = listOf(targetDir.toString(), recipeModules, recipeClasspath, gradlePluginVersion, mavenPluginVersion)
    doFirst {
        logger.lifecycle("Recipe modules: ")
        logger.lifecycle(recipeModules)

        // Ensure no stale output from previous runs is in the output directory
        targetDir.deleteRecursively()
        targetDir.mkdirs()
    }
    doLast {
        this as JavaExec
        logger.lifecycle("Wrote generated docs to: ${args!!.first()}")
    }
}

defaultTasks = mutableListOf("run")

plugins {
    application
    id("org.jetbrains.kotlin.jvm").version("1.7.20")
    id("org.owasp.dependencycheck") version "7.0.4.1"
}

dependencyCheck {
    analyzers.assemblyEnabled = false
    failBuildOnCVSS = 9.0F
    suppressionFile = "suppressions.xml"
    scanConfigurations = listOf("runtimeClasspath")
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

// Either `latest.release` or `latest.integration`
val rewriteVersion = "latest.release"

// Used to determine what type of changelog to build up.
//   * "release"  : When making a changelog for larger releases of OpenRewrite
//   * "snapshot" : When making a changelog for snapshot releases on a weekly cadence.
//   * "diff" : When making a diff-log for what recipes are made over time.
val deployType = "release"

// When you set the above to diff, this will be the name of the markdown file generated
val diffFileName = "desjardins"

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("info.picocli:picocli:latest.release")
    implementation("org.openrewrite:rewrite-core:$rewriteVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.2")
    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")

    "recipe"(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
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

    "recipe"("org.openrewrite.recipe:rewrite-circleci:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-concourse:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-github-actions:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-java-security:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-jhipster:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-kubernetes:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-logging-frameworks:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-micronaut:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-migrate-java:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-quarkus:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-spring:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-terraform:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-testing-frameworks:$rewriteVersion")
    "recipe"("org.openrewrite.recipe:rewrite-cloud-suitability-analyzer:$rewriteVersion")
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
    args = listOf(targetDir.toString(), recipeModules, recipeClasspath, gradlePluginVersion, mavenPluginVersion, deployType, diffFileName)
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

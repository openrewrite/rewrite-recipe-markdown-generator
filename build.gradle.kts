import org.kordamp.gradle.plugin.markdown.tasks.MarkdownToHtmlTask

plugins {
    application
    id("org.jetbrains.kotlin.jvm").version("1.7.20")
    id("org.owasp.dependencycheck") version "7.0.4.1"
    id("org.kordamp.gradle.markdown") version "2.2.0"
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
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    implementation("io.github.java-diff-utils:java-diff-utils:4.11")
    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")

    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")

    "recipe"(platform("org.openrewrite.recipe:rewrite-recipe-bom:$rewriteVersion"))
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
    "recipe"("org.openrewrite:rewrite-java-test")

//    "recipe"("org.openrewrite:rewrite-csharp")
//    "recipe"("org.openrewrite:rewrite-javascript")
    "recipe"("org.openrewrite:rewrite-kotlin")
//    "recipe"("org.openrewrite:rewrite-python") // Removed based on Olga request
//    "recipe"("org.openrewrite:rewrite-ruby")

//    "recipe"("org.openrewrite.recipe:rewrite-all") // Exclude language composition data table recipes
    "recipe"("org.openrewrite.meta:rewrite-analysis")
    "recipe"("org.openrewrite.recipe:rewrite-ai-search")
    "recipe"("org.openrewrite.recipe:rewrite-apache")
    "recipe"("org.openrewrite.recipe:rewrite-circleci")
    "recipe"("org.openrewrite.recipe:rewrite-concourse")
    "recipe"("org.openrewrite.recipe:rewrite-cucumber-jvm")
    "recipe"("org.openrewrite.recipe:rewrite-docker")
    "recipe"("org.openrewrite.recipe:rewrite-github-actions")
    "recipe"("org.openrewrite.recipe:rewrite-hibernate")
    "recipe"("org.openrewrite.recipe:rewrite-jackson")
    "recipe"("org.openrewrite.recipe:rewrite-java-dependencies")
    "recipe"("org.openrewrite.recipe:rewrite-java-security")
    "recipe"("org.openrewrite.recipe:rewrite-jenkins")
    "recipe"("org.openrewrite.recipe:rewrite-kubernetes")
    "recipe"("org.openrewrite.recipe:rewrite-liberty")
    "recipe"("org.openrewrite.recipe:rewrite-launchdarkly")
    "recipe"("org.openrewrite.recipe:rewrite-logging-frameworks")
    "recipe"("org.openrewrite.recipe:rewrite-micrometer")
    "recipe"("org.openrewrite.recipe:rewrite-micronaut")
    "recipe"("org.openrewrite.recipe:rewrite-migrate-java")
    "recipe"("org.openrewrite.recipe:rewrite-nodejs")
    "recipe"("org.openrewrite.recipe:rewrite-okhttp")
    "recipe"("org.openrewrite.recipe:rewrite-openapi")
    "recipe"("org.openrewrite.recipe:rewrite-quarkus")
    "recipe"("org.openrewrite.recipe:rewrite-recommendations")
    "recipe"("org.openrewrite.recipe:rewrite-spring")
    "recipe"("org.openrewrite.recipe:rewrite-sql")
    "recipe"("org.openrewrite.recipe:rewrite-static-analysis")
    "recipe"("org.openrewrite.recipe:rewrite-struts")
    "recipe"("org.openrewrite.recipe:rewrite-terraform")
    "recipe"("org.openrewrite.recipe:rewrite-testing-frameworks")
    "recipe"("org.openrewrite.recipe:rewrite-third-party")
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
        logger.lifecycle(recipeModules.replace(";", "\n"))

        // Ensure no stale output from previous runs is in the output directory
        targetDir.deleteRecursively()
        targetDir.mkdirs()
    }
    doLast {
        this as JavaExec
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION") // IntelliJ says this is unnecessary, kotlin compiler disagrees
        logger.lifecycle("Wrote generated docs to: file://${args!!.first()}")
    }
}

defaultTasks = mutableListOf("run")

tasks.withType<MarkdownToHtmlTask> {
    dependsOn("run")
    sourceDir = file("build/docs")
    outputDir = file("build/html")
    fencedCodeBlocks = true
    tables = true
    doLast {
        this as MarkdownToHtmlTask
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION") // IntelliJ says this is unnecessary, kotlin compiler disagrees
        logger.lifecycle("Wrote generated html to: file://${outputDir}")
    }
}

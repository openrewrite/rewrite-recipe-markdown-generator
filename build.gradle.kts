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
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
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

// Used to determine what type of changelog to build up.
//   * "release"  : When making a changelog for larger releases of OpenRewrite
//   * "snapshot" : When making a changelog for snapshot releases on a weekly cadence.
//   * "diff" : When making a diff-log for what recipes are made over time.
val deployType = "release"

// When you set the above to diff, this will be the name of the markdown file generated
val diffFileName = "desjardins"

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(platform("io.moderne.recipe:moderne-recipe-bom:$rewriteVersion"))

    implementation("info.picocli:picocli:latest.release")
    implementation("org.openrewrite:rewrite-core")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    implementation("io.github.java-diff-utils:java-diff-utils:4.11")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")

    testImplementation("org.junit.jupiter:junit-jupiter:latest.release")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    "recipe"(platform("io.moderne.recipe:moderne-recipe-bom:$rewriteVersion"))

    "recipe"("org.openrewrite:rewrite-core")
    "recipe"("org.openrewrite:rewrite-gradle")
    "recipe"("org.openrewrite:rewrite-groovy")
    "recipe"("org.openrewrite:rewrite-hcl")
    "recipe"("org.openrewrite:rewrite-java")
    "recipe"("org.openrewrite:rewrite-json")
    "recipe"("org.openrewrite:rewrite-kotlin")
    "recipe"("org.openrewrite:rewrite-maven")
    "recipe"("org.openrewrite:rewrite-properties")
    "recipe"("org.openrewrite:rewrite-protobuf")
    "recipe"("org.openrewrite:rewrite-toml")
    "recipe"("org.openrewrite:rewrite-xml")
    "recipe"("org.openrewrite:rewrite-yaml")

    // Only show the versions of these modules below
//    "recipe"("org.openrewrite:rewrite-cobol:$rewriteVersion")
//    "recipe"("org.openrewrite:rewrite-csharp:$rewriteVersion")
//    "recipe"("org.openrewrite:rewrite-javascript:$rewriteVersion")
//    "recipe"("org.openrewrite:rewrite-polyglot:$rewriteVersion")
//    "recipe"("org.openrewrite:rewrite-python:$rewriteVersion")
//    "recipe"("org.openrewrite:rewrite-templating:$rewriteVersion")

    "recipe"("org.openrewrite.recipe:rewrite-all")
    "recipe"("org.openrewrite.meta:rewrite-analysis")
    "recipe"("org.openrewrite.recipe:rewrite-ai-search")
    "recipe"("org.openrewrite.recipe:rewrite-android")
    "recipe"("org.openrewrite.recipe:rewrite-apache")
    "recipe"("org.openrewrite.recipe:rewrite-azul")
    "recipe"("org.openrewrite.recipe:rewrite-circleci")
    "recipe"("org.openrewrite.recipe:rewrite-codemods")
    "recipe"("org.openrewrite.recipe:rewrite-codemods-ng")
    "recipe"("org.openrewrite.recipe:rewrite-compiled-analysis")
    "recipe"("org.openrewrite.recipe:rewrite-comprehension")
    "recipe"("org.openrewrite.recipe:rewrite-concourse")
    "recipe"("org.openrewrite.recipe:rewrite-cucumber-jvm")
    "recipe"("org.openrewrite.recipe:rewrite-diffblue:latest.integration") // Not released yet
    "recipe"("org.openrewrite.recipe:rewrite-docker")
    "recipe"("org.openrewrite.recipe:rewrite-dotnet")
    "recipe"("org.openrewrite.recipe:rewrite-dropwizard")
    "recipe"("org.openrewrite.recipe:rewrite-feature-flags")
    "recipe"("org.openrewrite.recipe:rewrite-github-actions")
    "recipe"("org.openrewrite.recipe:rewrite-gitlab")
    "recipe"("org.openrewrite.recipe:rewrite-hibernate")
    "recipe"("org.openrewrite.recipe:rewrite-jackson")
    "recipe"("org.openrewrite.recipe:rewrite-java-dependencies")
    "recipe"("org.openrewrite.recipe:rewrite-java-security")
    "recipe"("org.openrewrite.recipe:rewrite-jenkins")
    "recipe"("org.openrewrite.recipe:rewrite-kubernetes")
    "recipe"("org.openrewrite.recipe:rewrite-liberty")
    "recipe"("org.openrewrite.recipe:rewrite-logging-frameworks")
    "recipe"("org.openrewrite.recipe:rewrite-micrometer")
    "recipe"("org.openrewrite.recipe:rewrite-micronaut")
    "recipe"("org.openrewrite.recipe:rewrite-migrate-java")
    "recipe"("org.openrewrite.recipe:rewrite-netty")
    "recipe"("org.openrewrite.recipe:rewrite-nodejs")
    "recipe"("org.openrewrite.recipe:rewrite-okhttp")
    "recipe"("org.openrewrite.recipe:rewrite-openapi")
    "recipe"("org.openrewrite.recipe:rewrite-quarkus")
    "recipe"("org.openrewrite.recipe:rewrite-reactive-streams")
    "recipe"("org.openrewrite.recipe:rewrite-rewrite")
    "recipe"("org.openrewrite.recipe:rewrite-spring")
    "recipe"("org.openrewrite.recipe:rewrite-sql")
    "recipe"("org.openrewrite.recipe:rewrite-static-analysis")
    "recipe"("org.openrewrite.recipe:rewrite-struts")
    "recipe"("org.openrewrite.recipe:rewrite-terraform")
    "recipe"("org.openrewrite.recipe:rewrite-testing-frameworks")
    "recipe"("org.openrewrite.recipe:rewrite-third-party")

    "recipe"("io.moderne.recipe:rewrite-cryptography")
    "recipe"("io.moderne.recipe:rewrite-devcenter")
    "recipe"("io.moderne.recipe:rewrite-hibernate")
    "recipe"("io.moderne.recipe:rewrite-spring")
    "recipe"("io.moderne.recipe:rewrite-vulncheck")
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
    val targetDir = layout.buildDirectory.dir("docs").get().asFile

    val latestVersionsOnly = providers.gradleProperty("latestVersionsOnly").getOrElse("").equals("true")
    if (latestVersionsOnly) {
        // Additional modules whose versions we want to show, but not (yet) their recipes
        dependencies {
            "recipe"("org.openrewrite:rewrite-cobol:$rewriteVersion")
            "recipe"("org.openrewrite:rewrite-csharp:$rewriteVersion")
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
    val recipeClasspath = recipeConf.resolvedConfiguration.files.asSequence()
        .map { it.absolutePath }
        .joinToString(";")

    description = "Writes generated markdown docs to $targetDir"
    val arguments = mutableListOf(
        targetDir.toString(),
        recipeModules,
        recipeClasspath,
        latestVersion("org.openrewrite:rewrite-bom:latest.release"),
        latestVersion("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"),
        latestVersion("io.moderne.recipe:moderne-recipe-bom:latest.release"),
        latestVersion("org.openrewrite:plugin:latest.release"),
        latestVersion("org.openrewrite.maven:rewrite-maven-plugin:latest.release"),
        deployType,
        diffFileName
    )
    if (latestVersionsOnly) {
        arguments.add("--latest-versions-only")
    }
    args = arguments
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

fun latestVersion(arg: String) =
    configurations.detachedConfiguration(dependencies.create(arg))
        .resolvedConfiguration
        .firstLevelModuleDependencies
        .first()
        .moduleVersion

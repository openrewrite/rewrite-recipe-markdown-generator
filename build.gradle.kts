import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
    application
    id("org.jetbrains.kotlin.jvm").version("2.4.20")
    id("org.owasp.dependencycheck") version "latest.release"
}

dependencyCheck {
    analyzers.assemblyEnabled = false
    scanConfigurations = listOf("runtimeClasspath")
    format = System.getenv("DEPENDENCY_CHECK_FORMAT") ?: "HTML"
    suppressionFile = "suppressions.xml"
    nvd.apiKey = System.getenv("NVD_API_KEY")
    analyzers.centralEnabled = System.getenv("CENTRAL_ANALYZER_ENABLED").toBoolean()
    analyzers.ossIndex.username = System.getenv("OSSINDEX_USERNAME")
    analyzers.ossIndex.password = System.getenv("OSSINDEX_PASSWORD")
}

group = "org.example"
version = "1.0-SNAPSHOT"

// Either `latest.release` or `latest.integration`
val rewriteVersion = "latest.release"

// Each repository here is probed for maven-metadata.xml on every dynamic coordinate below, so one
// that hosts nothing this build resolves costs a round trip per module.
repositories {
    mavenLocal()
    val codegenomeUsername = providers.gradleProperty("codegenomeUsername").getOrElse("")
    val codegenomePassword = providers.gradleProperty("codegenomePassword").getOrElse("")
    if (codegenomeUsername.isNotEmpty() && codegenomePassword.isNotEmpty()) {
        maven {
            name = "codegenome"
            url = uri("https://artifacts.codegenomeproject.org/maven")
            credentials {
                username = codegenomeUsername
                password = codegenomePassword
            }
            content {
                includeGroupAndSubgroups("org.openrewrite")
                includeGroupAndSubgroups("io.moderne")
            }
        }
    }
    mavenCentral()
    // Hosts org.openrewrite:plugin, which is not on Maven Central.
    gradlePluginPortal {
        content {
            includeModule("org.openrewrite", "plugin")
        }
    }
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        // Far short of the daily cadence of the scheduled doc builds, so they still see each release.
        cacheDynamicVersionsFor(1, TimeUnit.HOURS)
    }
}

val recipeBomGroups = setOf("org.openrewrite", "org.openrewrite.meta", "org.openrewrite.recipe", "io.moderne.recipe")
val nonRecipeBomModules = setOf(
    "org.openrewrite:plugin",
    "org.openrewrite:rewrite-test",
    // Deprecated, but still managed by moderne-recipe-bom
    "org.openrewrite.recipe:rewrite-ai-search",
)
// Parsers, Lombok support and test harness for rewrite-java
val javaSupportModule = Regex("org\\.openrewrite:rewrite-java-.+")
val moderneRecipeBom = "io.moderne.recipe:moderne-recipe-bom:$rewriteVersion"

// Every recipe module managed by the BOM chain, each at $rewriteVersion rather than the version the BOM pins, so the
// docs show the latest patch release of each module.
val recipeConf = configurations.create("recipe") {
    withDependencies {
        bomModules(moderneRecipeBom)
            .filter { it.substringBefore(':') in recipeBomGroups }
            .filterNot { it in nonRecipeBomModules || javaSupportModule.matches(it) }
            .forEach { add(project.dependencies.create("$it:$rewriteVersion")) }
    }
}

dependencies {
    // Platform dependencies (BOMs)
    // moderne-recipe-bom pins an older rewrite-bom than the recipe modules are resolved at. The language RPCs run the
    // engine version found here (e.g. the openrewrite pip package for rewrite-python), so keep it at $rewriteVersion
    // too, or recipe packages built against the newer engine fail to load.
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
    implementation(platform("io.moderne.recipe:moderne-recipe-bom:latest.release"))
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Core implementation dependencies
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("info.picocli:picocli:latest.release")
    implementation("io.github.java-diff-utils:java-diff-utils:4.11")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.openrewrite:rewrite-core")
    implementation("org.openrewrite:rewrite-javascript")
    implementation("org.openrewrite:rewrite-csharp")
    implementation("org.openrewrite:rewrite-python")
    implementation("org.openrewrite:rewrite-go")

    // Runtime dependencies
    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")
    // Java parser implementation matching the build toolchain (JDK 21, see `java.toolchain` below).
    // rewrite-java is on this (app) classpath transitively via rewrite-csharp/javascript/python, so
    // org.openrewrite.java.JavaParser is loaded here. Some recipes construct a JavaParser when their
    // class is initialized (e.g. ai.timefold.solver.migration.AbstractRecipe in rewrite-third-party);
    // without a rewrite-java-NN parser on JavaParser's own classpath, loading those recipes throws and
    // aborts full doc generation. Version is managed by the moderne-recipe-bom platform above.
    runtimeOnly("org.openrewrite:rewrite-java-21")

    // Test dependencies
    testImplementation("org.assertj:assertj-core:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Recipe modules not managed by moderne-recipe-bom
    "recipe"("io.moderne.recipe:rewrite-cve-2026-22732:$rewriteVersion")
    // Go recipes load via the Go RPC at doc-gen time (see GoRecipeLoader); this empty Maven artifact
    // only anchors the version-table row and the RecipeOrigin the Go recipes are attributed to.
    "recipe"("org.openrewrite.recipe:recipes-go:$rewriteVersion")

//    "recipe"("org.openrewrite.recipe:rewrite-diffblue:latest.integration") {
//        exclude(group = "org.openrewrite")
//        exclude(group = "org.openrewrite.recipe")
//    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
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

    // Collect all of the dependencies from recipeConf, then stuff them into a string representation
    val firstLevelArtifacts = recipeConf.resolvedConfiguration.firstLevelModuleDependencies.flatMap { dep ->
        dep.moduleArtifacts.map { artifact -> dep to artifact }
    }
    val recipeModules = firstLevelArtifacts.joinToString(";") { (dep, artifact) ->
        "${dep.moduleGroup}:${dep.moduleName}:${dep.moduleVersion}:${artifact.file}"
    }
    // recipeModules doesn't include transitive dependencies, but those are needed to load recipes and their descriptors.
    // A --latest-versions-only run reads only each recipe module's own MANIFEST.MF, so resolving the transitive
    // closure there downloads the whole recipe classpath to read nothing from it.
    val recipeClasspath = if (latestVersionsOnly) {
        firstLevelArtifacts.map { (_, artifact) -> artifact.file.absolutePath }
    } else {
        recipeConf.incoming.files.map { it.absolutePath }
    }.joinToString(";")

    description = "Writes generated markdown docs to $targetDir and $moderneTargetDir"
    val arguments = mutableListOf(
        targetDir.toString(),
        recipeModules,
        recipeClasspath,
        latestVersion("org.openrewrite:rewrite-bom:$rewriteVersion"),
        latestVersion("org.openrewrite.recipe:rewrite-recipe-bom:$rewriteVersion"),
        latestVersion(moderneRecipeBom),
        latestVersion("org.openrewrite:plugin:$rewriteVersion"),
        latestVersion("org.openrewrite.maven:rewrite-maven-plugin:$rewriteVersion"),
        moderneTargetDir.toString()
    )
    if (latestVersionsOnly) {
        arguments.add("--latest-versions-only")
    }
    // -PrecipeArtifacts=rewrite-circleci,rewrite-static-analysis restricts loading to those artifacts for
    // fast local iteration (the full classpath is still resolved for transitive/delegatesTo lookups).
    val recipeArtifacts = providers.gradleProperty("recipeArtifacts").getOrElse("")
    if (recipeArtifacts.isNotEmpty()) {
        arguments.add("--only-artifacts=$recipeArtifacts")
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

/// Returns the `groupId:artifactId` of every module managed by the BOM at [coordinate], following `import`-scoped BOMs.
fun bomModules(coordinate: String): Set<String> {
    val managed = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(detachedConfiguration("$coordinate@pom").singleFile)
        .getElementsByTagName("dependencyManagement").item(0) as? Element ?: return emptySet()
    val dependencies = managed.getElementsByTagName("dependency")
    return (0 until dependencies.length).flatMap { i ->
        val dependency = dependencies.item(i) as Element
        fun child(name: String) = dependency.getElementsByTagName(name).item(0)?.textContent?.trim()
        val module = "${child("groupId")}:${child("artifactId")}"
        if (child("scope") == "import") bomModules("$module:${child("version")}") else setOf(module)
    }.toSet()
}

fun detachedConfiguration(arg: String) =
    configurations.detachedConfiguration(dependencies.create(arg))
        // Detached configurations are not in the configuration container, so the `configurations.all`
        // block above never sees them; without this they keep Gradle's 24 hour default and a manually
        // dispatched docs run reports yesterday's BOM and plugin versions.
        .apply { resolutionStrategy.cacheDynamicVersionsFor(1, TimeUnit.HOURS) }

fun latestVersion(arg: String) =
    detachedConfiguration(arg)
        .resolvedConfiguration
        .firstLevelModuleDependencies
        .first()
        .moduleVersion

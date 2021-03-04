buildscript {
    repositories {
        jcenter()
        gradlePluginPortal()
    }
}

plugins {
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    maven { url = uri("https://dl.bintray.com/openrewrite/maven") }
    mavenCentral()
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
}

val rewriteVersion = "latest.integration"

dependencies {
    implementation("info.picocli:picocli:latest.release")
    implementation("org.openrewrite:rewrite-core:$rewriteVersion")
    runtimeOnly("org.openrewrite:rewrite-java:$rewriteVersion")
    runtimeOnly("org.openrewrite:rewrite-xml:$rewriteVersion")
    runtimeOnly("org.openrewrite:rewrite-maven:$rewriteVersion")
    runtimeOnly("org.openrewrite:rewrite-properties:$rewriteVersion")
    runtimeOnly("org.openrewrite:rewrite-yaml:$rewriteVersion")
    runtimeOnly("org.openrewrite.recipe:rewrite-testing-frameworks:$rewriteVersion")
    runtimeOnly("org.openrewrite.recipe:rewrite-spring:$rewriteVersion")
}

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()

    options.isFork = true
    options.forkOptions.executable = "javac"
    options.compilerArgs.addAll(listOf("--release", "8"))
}

application {
    mainClass.set("org.openrewrite.RecipeMarkdownGenerator")
}

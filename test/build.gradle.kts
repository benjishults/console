val kotestVersion: String by project

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.dokka") version "2.0.0"
    `maven-publish`
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":lib"))
    implementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    implementation("io.kotest:kotest-assertions-core:$kotestVersion")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/benjishults/console")
            credentials {
                username = providers
                    .gradleProperty("github.actor")
                    .getOrElse(System.getenv("GITHUB_ACTOR"))
                password = providers
                    .gradleProperty("github.token")
                    .getOrElse(System.getenv("GITHUB_TOKEN"))
            }
        }
    }
    publications {
        create<MavenPublication>("GitHubPackages") {
            groupId = "io.github.benjishults"
            artifactId = "console-test"
            from(components["java"])
            pom {
                name = "BPS Console Library Test Helpers"
                description = "Test helpers that use KoTest FreeSpec"
                url = "https://github.com/benjishults/console"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
            }
        }
    }
}

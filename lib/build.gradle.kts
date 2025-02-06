//import org.jreleaser.model.Active

val commonsValidatorVersion: String by project
val kotestVersion: String by project
val kotlinXDateTimeVersion: String by project
val mockkVersion: String by project

/*
TODO
 1. try the gradle nexus plugin
   * https://h4pehl.medium.com/publish-your-gradle-artifacts-to-maven-central-f74a0af085b1
 1. try different syntax for jreleaser
   * https://jreleaser.org/guide/latest/examples/maven/maven-central.html#_portal_publisher_api
 2. manual upload of zip file:
   * https://central.sonatype.org/publish/publish-portal-upload/
 3. try maven rather than maven-publish
   1. https://central.sonatype.org/publish/publish-gradle/#deploying-to-ossrh-with-gradle-introduction
   2. https://github.com/gradle-nexus/publish-plugin
 4. create a ticket here
   * https://community.sonatype.com/t/401-content-access-is-protected-by-token-authentication-failure-while-performing-maven-release/12741
 4. try publishing to githubs repo instead of maven
   * https://docs.github.com/en/actions/use-cases-and-examples/publishing-packages/publishing-java-packages-with-maven#publishing-packages-to-github-packages
*/

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.dokka") version "2.0.0"
    `maven-publish`
    `java-library`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // This dependency is used internally, and not exposed to consumers on their own compile classpath.
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinXDateTimeVersion")
    implementation("commons-validator:commons-validator:$commonsValidatorVersion")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("io.mockk:mockk-jvm:$mockkVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(project(":test"))
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.10.3")
        }
    }
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
            artifactId = "console"
            from(components["java"])
            pom {
                name = "BPS Console Library"
                description = "Makes it easy to create and test an application with a console UX."
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

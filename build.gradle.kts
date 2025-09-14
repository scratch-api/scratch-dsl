import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import java.time.Duration

plugins {
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    `maven-publish`
    signing
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.10"
}

group = "de.thecommcraft"
version = (System.getenv("GITHUB_REF_NAME")?.removePrefix("v")
    ?: "0.0.1a1")

java {
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.5.4")
    implementation("de.jonasbroeckmann.kzip:kzip:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation(kotlin("reflect"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "scratchdsl"
            version = project.version.toString()

            pom {
                name.set("Scratch DSL")
                description.set("A Kotlin DSL for generating scratch projects.")
                url.set("https://github.com/scratch-api/scratch-dsl")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("TheCommCraft")
                        name.set("TheCommCraft")
                        email.set("tcc@thecommcraft.de")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/scratch-api/scratch-dsl.git")
                    developerConnection.set("scm:git:ssh://github.com/scratch-api/scratch-dsl.git")
                    url.set("https://github.com/scratch-api/scratch-dsl")
                }
            }
        }
    }

    repositories {
        mavenLocal()
    }
}

signing {
    System.getenv("GPG_PRIVATE_KEY")?.let {
        useInMemoryPgpKeys(
            System.getenv("GPG_PRIVATE_KEY"),
            System.getenv("GPG_PASSPHRASE")
        )
    } ?: useGpgCmd()
    sign(publishing.publications["mavenJava"])
}


nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))

            username.set(findProperty("sonatypeUsername") as String? ?: System.getenv("SONATYPE_USERNAME") ?: "")
            password.set(findProperty("sonatypePassword") as String? ?: System.getenv("SONATYPE_PASSWORD") ?: "")

            transitionCheckOptions {
                maxRetries.set(40)
                delayBetween.set(Duration.ofSeconds(5))
            }
        }
    }
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(tasks.named("assemble"))
}

tasks.register("printPublishDebug") {
    doLast {
        println("Project: $group:$version")
        println("GPG exec (signing.gnupg.executable): ${findProperty("signing.gnupg.executable")}")
        println("GPG key (signing.gnupg.keyName): ${findProperty("signing.gnupg.keyName")}")
        println("Sonatype username present: ${findProperty("sonatypeUsername") != null || System.getenv("SONATYPE_USERNAME") != null}")
    }
}

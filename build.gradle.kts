/*
Copyright 2022 c-fraser

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessTask
import io.github.gradlenexus.publishplugin.NexusPublishExtension
import io.gitlab.arturbosch.detekt.Detekt
import java.util.Locale
import kotlinx.knit.KnitPluginExtension
import kotlinx.validation.KotlinApiBuildTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jreleaser.gradle.plugin.JReleaserExtension
import org.jreleaser.model.Active

buildscript { dependencies { classpath(libs.knit) } }

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.dokka)
  alias(libs.plugins.spotless)
  alias(libs.plugins.detekt)
  alias(libs.plugins.nexus.publish)
  alias(libs.plugins.jreleaser)
  alias(libs.plugins.dependency.versions)
  alias(libs.plugins.kover)
  alias(libs.plugins.compatibility.validator)
  `java-library`
  `maven-publish`
  signing
}

apply(plugin = "kotlinx-knit")

allprojects {
  group = "io.github.c-fraser"
  version = "0.1.0"
}

java {
  toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
  withSourcesJar()
}

repositories { mavenCentral() }

dependencies {
  api(libs.okhttp)
  implementation(libs.netty.buffer)
  implementation(libs.netty.codec.http)
  implementation(libs.netty.handler)
  implementation(libs.bouncy.castle)
  implementation(libs.caffeine.cache)
  implementation(libs.slf4j.api)
  runtimeOnly(libs.netty.tcnative)

  testImplementation(kotlin("test"))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.junit.pioneer)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.okhttp.tls)
  testImplementation(libs.javalin)
  testImplementation(libs.jimfs)
  testImplementation(libs.slf4j.nop)
  testImplementation(libs.knit.test)
  testImplementation(libs.zt.exec)
}

configure<SpotlessExtension> {
  val ktfmtVersion = libs.versions.ktfmt.get()
  val licenseHeader =
      """
      /*
      Copyright 2022 c-fraser
      
      Licensed under the Apache License, Version 2.0 (the "License");
      you may not use this file except in compliance with the License.
      You may obtain a copy of the License at
      
          https://www.apache.org/licenses/LICENSE-2.0
      
      Unless required by applicable law or agreed to in writing, software
      distributed under the License is distributed on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
      See the License for the specific language governing permissions and
      limitations under the License.
      */
      """
          .trimIndent()

  kotlin {
    ktfmt(ktfmtVersion)
    licenseHeader(licenseHeader)
    targetExclude("src/**/knit/**/*.kt")
  }

  kotlinGradle {
    ktfmt(ktfmtVersion)
    licenseHeader(licenseHeader, "(import|rootProject)")
    target(fileTree(rootProject.rootDir) { include("**/*.gradle.kts") })
  }
}

publishing {
  val javadocJar by
      tasks.registering(Jar::class) {
        val dokkaJavadoc by tasks.getting(DokkaTask::class)
        dependsOn(dokkaJavadoc)
        archiveClassifier.set("javadoc")
        from(dokkaJavadoc.outputDirectory.get())
      }

  publications {
    create<MavenPublication>("maven") {
      from(project.components["java"])
      artifact(javadocJar)
      pom {
        name.set(rootProject.name)
        description.set("${rootProject.name}-${project.version}")
        url.set("https://github.com/c-fraser/${rootProject.name}")
        inceptionYear.set("2022")

        issueManagement {
          system.set("GitHub")
          url.set("https://github.com/c-fraser/${rootProject.name}/issues")
        }

        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }

        developers {
          developer {
            id.set("c-fraser")
            name.set("Chris Fraser")
          }
        }

        scm {
          url.set("https://github.com/c-fraser/${rootProject.name}")
          connection.set("scm:git:git://github.com/c-fraser/${rootProject.name}.git")
          developerConnection.set("scm:git:ssh://git@github.com/c-fraser/${rootProject.name}.git")
        }
      }
    }
  }

  signing {
    publications.withType<MavenPublication>().all mavenPublication@{
      useInMemoryPgpKeys(System.getenv("GPG_SIGNING_KEY"), System.getenv("GPG_PASSWORD"))
      sign(this@mavenPublication)
    }
  }
}

configure<NexusPublishExtension> {
  repositories {
    sonatype {
      nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
      snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
      username.set(System.getenv("SONATYPE_USERNAME"))
      password.set(System.getenv("SONATYPE_PASSWORD"))
    }
  }
}

configure<JReleaserExtension> {
  project {
    authors.set(listOf("c-fraser"))
    license.set("Apache-2.0")
    extraProperties.put("inceptionYear", "2022")
    description.set("Intercepting HTTP(S) proxy server")
    links { homepage.set("https://github.com/c-fraser/${rootProject.name}") }
  }

  release {
    github {
      repoOwner.set("c-fraser")
      overwrite.set(true)
      token.set(System.getenv("GITHUB_TOKEN").orEmpty())
      changelog {
        formatted.set(Active.ALWAYS)
        format.set("- {{commitShortHash}} {{commitTitle}}")
        contributors.enabled.set(false)
        for (status in listOf("added", "changed", "fixed", "removed")) {
          labeler {
            label.set(status)
            title.set(status)
          }
          category {
            title.set(status.capitalize())
            labels.set(listOf(status))
          }
        }
      }
    }
  }
}

configure<KnitPluginExtension> { files = files("README.md") }

tasks {
  withType<KotlinCompile> {
    kotlinOptions.freeCompilerArgs = listOf("-Xjsr305=strict", "-Xjvm-default=all")
  }

  withType<Jar> {
    manifest { attributes("Automatic-Module-Name" to "io.github.cfraser.${rootProject.name}") }
  }

  test {
    useJUnitPlatform { excludeTags("performance") }
    systemProperties(
        "io.netty.leakDetection.level" to "PARANOID",
    /*"org.slf4j.simpleLogger.defaultLogLevel" to "debug", "javax.net.debug" to "all"*/ )
  }

  val perfTest by creating(Test::class) { useJUnitPlatform { includeTags("performance") } }

  withType<Test> {
    testLogging {
      showExceptions = true
      showStandardStreams = true
      events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED)
      exceptionFormat = TestExceptionFormat.FULL
    }
  }

  withType<DokkaTask> {
    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
      footerMessage = "Copyright &copy; 2022 c-fraser"
    }
  }

  val setupDocs by creating {
    dependsOn(dokkaHtml)
    doLast {
      copy {
        from(dokkaHtml.get().outputDirectory)
        into(layout.projectDirectory.dir("docs/api"))
      }
      val docs = rootDir.resolve("docs/index.md")
      rootDir.resolve("README.md").copyTo(docs, overwrite = true)
      docs.writeText(
          docs
              .readText()
              // remove project header
              .replace(
                  "# ${rootProject.name}${System.lineSeparator()}", "# ${System.lineSeparator()}")
              // unqualify docs references
              .replace(Regex("\\(docs/.*\\)")) { it.value.replace("docs/", "") }
              // remove inline TOC
              .replace(
                  Regex(
                      "<!--- TOC -->[\\s\\S]*<!--- END -->[\\n|\\r|\\n\\r]", RegexOption.MULTILINE),
                  ""))
    }
  }

  spotlessApply { mustRunAfter(setupDocs) }

  val spotlessKotlin by
      getting(SpotlessTask::class) {
        mustRunAfter(
            *listOf(withType<KotlinCompile>(), withType<KotlinApiBuildTask>(), withType<Test>())
                .flatten()
                .toTypedArray())
      }
  val spotlessKotlinGradle by getting(SpotlessTask::class) { mustRunAfter(spotlessKotlin) }

  withType<Detekt> {
    mustRunAfter(withType<SpotlessTask>())
    jvmTarget = "${JavaVersion.VERSION_11}"
    parallel = true
    buildUponDefaultConfig = true
    allRules = true
    config.setFrom(rootDir.resolve("detekt.yml"))
  }
}

/** Capitalize *this* [String]. */
fun String.capitalize(): String = replaceFirstChar {
  if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
}

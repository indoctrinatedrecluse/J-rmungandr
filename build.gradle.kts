plugins {
    id("java")
    kotlin("jvm") version "2.1.0" apply false
    id("org.jetbrains.intellij.platform") version "2.2.1" apply false
    id("org.jetbrains.intellij.platform.module") version "2.2.1" apply false
}

allprojects {
    group = property("pluginGroup") as String
    version = property("pluginVersion") as String
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(property("javaVersion").toString().toInt()))
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
            )
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.java")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.JUnit5)
    }

    implementation(project(":modules:extension-core"))
    implementation(project(":modules:jupyter-integration"))
    implementation(project(":modules:database-suite"))
    implementation(project(":modules:dataframe-viewer"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

intellijPlatform {
    pluginConfiguration {
        id.set("org.jormungandr.ide")
        name.set("Jörmungandr")
        version.set(providers.gradleProperty("pluginVersion"))
        description.set("The Modular, Open-Source IDE for Python, Data Science & Analytics.")
        vendor {
            name.set("indoctrinatedrecluse")
        }
    }
}

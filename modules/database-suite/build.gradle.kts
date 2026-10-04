plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform.module")
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
    }

    implementation(project(":modules:extension-core"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

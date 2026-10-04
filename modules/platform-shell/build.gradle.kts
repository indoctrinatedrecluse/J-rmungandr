import java.io.File

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

tasks {
    runIde {
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf(
                "-Didea.vendor.name=indoctrinatedrecluse",
                "-Djb.consents.confirmation.enabled=false",
                "-Deua.consents.confirmation.enabled=false",
                "-Didea.initially.ask.config=false",
                "-Dide.show.tips.on.startup=false",
                "-Dide.mac.message.dialogs.as.sheets=false",
                "-Dwsl.use.remote.agent.for.nio.filesystem=false",
                "-Dwsl.enabled=false",
                "-Dide.ijent.wsldefault=false",
                "-Didea.wsl.support.enabled=false"
            )
        })

        doFirst {
            val platformVer = providers.gradleProperty("platformVersion").get()
            val sandboxConfigDir = layout.buildDirectory.dir("idea-sandbox/IC-$platformVer/config").get().asFile
            val nowMs = System.currentTimeMillis()
            val acceptedStr = "rsch.send.usage.stat:1.1:0:$nowMs;eap:2021.2:0:$nowMs;\n"
            val cachedJson = """[{"consentId":"rsch.send.usage.stat","version":"1.1","text":"Help improve Jörmungandr.","printableName":"Send Usage Statistics","accepted":"false"},{"consentId":"eap","version":"2021.2","text":"Surveys","printableName":"Feedback","accepted":"false"}]"""

            // 1. Sandbox configuration paths
            listOf(
                File(sandboxConfigDir, "consentOptions"),
                File(sandboxConfigDir, "jormungandr/consentOptions"),
                File(sandboxConfigDir, "idea/consentOptions")
            ).forEach { dir ->
                dir.mkdirs()
                File(dir, "accepted").writeText(acceptedStr)
                File(dir, "cached").writeText(cachedJson)
            }

            // 2. User AppData paths for both custom vendor and JetBrains fallback
            val appData = System.getenv("APPDATA")
            if (appData != null) {
                listOf(
                    File(appData, "indoctrinatedrecluse/consentOptions"),
                    File(appData, "indoctrinatedrecluse/jormungandr/consentOptions"),
                    File(appData, "indoctrinatedrecluse/idea/consentOptions"),
                    File(appData, "JetBrains/consentOptions"),
                    File(appData, "JetBrains/jormungandr/consentOptions"),
                    File(appData, "JetBrains/idea/consentOptions")
                ).forEach { dir ->
                    try {
                        dir.mkdirs()
                        File(dir, "accepted").writeText(acceptedStr)
                        File(dir, "cached").writeText(cachedJson)
                    } catch (_: Throwable) {}
                }
            }

            val optionsDir = File(sandboxConfigDir, "options")
            optionsDir.mkdirs()

            val otherXml = File(optionsDir, "other.xml")
            if (otherXml.exists()) {
                val content = otherXml.readText()
                if (!content.contains("eua_accepted_version")) {
                    val updated = content.replace(
                        "\"keyToString\": {",
                        """"keyToString": {
    "eua_accepted_version": "2.0",
    "privacy_policy_accepted_version": "2.0",
    "previous_eua_accepted_version": "2.0",
    "ask.about.tip.of.the.day": "false",
    "show.tips.on.startup": "false",""""
                    )
                    otherXml.writeText(updated)
                }
            } else {
                otherXml.writeText(
                    """<application>
  <component name="PropertyService"><![CDATA[{
  "keyToString": {
    "eua_accepted_version": "2.0",
    "privacy_policy_accepted_version": "2.0",
    "previous_eua_accepted_version": "2.0",
    "ask.about.tip.of.the.day": "false",
    "show.tips.on.startup": "false"
  }
}]]></component>
</application>"""
                )
            }

            val generalLocalXml = File(optionsDir, "ide.general.local.xml")
            if (!generalLocalXml.exists()) {
                generalLocalXml.writeText(
                    """<application>
  <component name="GeneralLocalSettings">
    <option name="showTipsOnStartup" value="false" />
  </component>
</application>"""
                )
            }

            val lafXml = File(optionsDir, "laf.xml")
            if (!lafXml.exists() || !lafXml.readText().contains("org.jormungandr.theme")) {
                lafXml.writeText(
                    """<application>
  <component name="LafManager" autodetect="false">
    <laf themeId="org.jormungandr.theme.solarized.light" />
    <preferred-light-laf themeId="org.jormungandr.theme.solarized.light" />
    <preferred-dark-laf themeId="org.jormungandr.theme.bubblegum.barbie" />
  </component>
</application>"""
                )
            }

            val colorsSchemeXml = File(optionsDir, "colors.scheme.xml")
            if (!colorsSchemeXml.exists() || colorsSchemeXml.readText().contains("name=\"Dark\"")) {
                colorsSchemeXml.writeText(
                    """<application>
  <component name="EditorColorsManagerImpl">
    <global_color_scheme name="Solarized Light" />
  </component>
</application>"""
                )
            }
        }
    }
}

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // No DefaultImpls bridges for platform interfaces: otherwise Plugin Verifier reports
        // deprecated/experimental ToolWindowFactory defaults as overridden by the plugin
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        val localPath = providers.gradleProperty("platformLocalPath").orNull
        if (localPath != null && file(localPath).exists()) {
            local(localPath)
        } else {
            datagrip(providers.gradleProperty("platformVersion"))
        }
        bundledPlugin("com.intellij.database")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("version")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false

    pluginVerification {
        ides {
            val localPath = providers.gradleProperty("platformLocalPath").orNull
            if (localPath != null && file(localPath).exists()) {
                local(localPath)
            } else {
                create(IntelliJPlatformType.DataGrip, providers.gradleProperty("platformVersion"))
            }
        }
    }
}

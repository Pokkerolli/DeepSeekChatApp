import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.GradleException

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
}

val packagingJdk = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(17))
}

compose.desktop {
    application {
        javaHome = packagingJdk.get().metadata.installationPath.asFile.absolutePath
        mainClass = "com.example.deepseekchat.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "DeepSeekChatApp"
            packageVersion = "1.0.0"
        }
    }
}

val isWindowsHost = System.getProperty("os.name")
    .contains("windows", ignoreCase = true)

val verifyWindowsPackagingHost by tasks.registering {
    doLast {
        if (!isWindowsHost) {
            throw GradleException(
                "Windows packaging tasks are only supported on Windows hosts. " +
                    "Current host: ${System.getProperty("os.name")}. " +
                    "Use ':desktopApp:packageDmg' on macOS."
            )
        }
    }
}

val windowsPackagingTasks = setOf(
    "packageExe",
    "packageMsi",
    "packageReleaseExe",
    "packageReleaseMsi"
)

tasks.configureEach {
    if (name in windowsPackagingTasks) {
        dependsOn(verifyWindowsPackagingHost)
    }
}

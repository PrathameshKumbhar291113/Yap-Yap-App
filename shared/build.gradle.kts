import org.jetbrains.kotlin.gradle.dsl.JvmTarget

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildkonfig)
}

if (findProperty("buildkonfig.flavor") == null) {
    val requestedTasks = gradle.startParameter.taskNames.joinToString(" ").lowercase()
    val flavor = when {
        requestedTasks.contains("release") && !requestedTasks.contains("debug") -> "release"
        else -> "debug"
    }
    extensions.extraProperties["buildkonfig.flavor"] = flavor
}

buildkonfig {
    packageName = "com.yapyap.core.config"
    objectName = "AppBuildConfig"

    defaultConfigs {
        buildConfigField(STRING, "BASE_URL", "https://api.yapyap.app")
        buildConfigField(BOOLEAN, "IS_DEBUG", "false")
    }

    defaultConfigs("debug") {
        buildConfigField(BOOLEAN, "IS_DEBUG", "true")
    }

    targetConfigs("debug") {
        create("android") {
            buildConfigField(STRING, "BASE_URL", "http://10.0.2.2:8080")
        }
        create("iosArm64") {
            buildConfigField(STRING, "BASE_URL", "http://localhost:8080")
        }
        create("iosSimulatorArm64") {
            buildConfigField(STRING, "BASE_URL", "http://localhost:8080")
        }
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    androidLibrary {
       namespace = "com.yapyap.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.security.crypto)
            implementation(libs.koin.android)
            implementation("io.socket:socket.io-client:${libs.versions.socket.io.get()}") {
                exclude(group = "org.json", module = "json")
            }
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.kotlinx.coroutines.play.services)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.core.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.kermit)
            implementation(libs.kotlinx.datetime)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.auth)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
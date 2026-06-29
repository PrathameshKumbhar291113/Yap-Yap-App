import org.jetbrains.kotlin.gradle.dsl.JvmTarget

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("YapYapDatabase") {
            packageName.set("com.yapyap.core.database")
        }
    }
}

if (findProperty("buildkonfig.flavor") == null) {
    val requestedTasks = gradle.startParameter.taskNames.joinToString(" ").lowercase()
    val flavor = when {
        requestedTasks.contains("release") && !requestedTasks.contains("debug") -> "release"
        else -> "debug"
    }
    extensions.extraProperties["buildkonfig.flavor"] = flavor
}

val yapProductionBaseUrl = "https://yapyap-backend.onrender.com"

fun readYapProperty(key: String): String? {
    val localProperties = Properties()
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { localProperties.load(it) }
    }
    return localProperties.getProperty(key) ?: findProperty(key) as String?
}

/** Ktor paths already include `/api/...`; strip a trailing `/api` if present in gradle.properties. */
fun normalizeApiHost(url: String): String = url.trimEnd('/').removeSuffix("/api")

val debugApiHostAndroid = normalizeApiHost(
    readYapProperty("API_BASE_URL_ANDROID") ?: "http://127.0.0.1:8080",
)
val debugApiHostIos = normalizeApiHost(
    readYapProperty("API_BASE_URL_IOS") ?: "http://localhost:8080",
)
val debugSocketHostAndroid = (
    readYapProperty("SOCKET_URL_ANDROID") ?: debugApiHostAndroid
    ).trimEnd('/')
val debugSocketHostIos = (
    readYapProperty("SOCKET_URL_IOS") ?: debugApiHostIos
    ).trimEnd('/')

buildkonfig {
    packageName = "com.yapyap.core.config"
    objectName = "AppBuildConfig"

    defaultConfigs {
        buildConfigField(STRING, "BASE_URL", yapProductionBaseUrl)
        buildConfigField(STRING, "SOCKET_URL", yapProductionBaseUrl)
        buildConfigField(BOOLEAN, "IS_DEBUG", "false")
        buildConfigField(STRING, "APP_VERSION", "1.0")
    }

    defaultConfigs("debug") {
        buildConfigField(BOOLEAN, "IS_DEBUG", "true")
    }

    defaultConfigs("release") {
        buildConfigField(STRING, "BASE_URL", yapProductionBaseUrl)
        buildConfigField(STRING, "SOCKET_URL", yapProductionBaseUrl)
        buildConfigField(BOOLEAN, "IS_DEBUG", "false")
    }

    targetConfigs("debug") {
        create("android") {
            buildConfigField(STRING, "BASE_URL", debugApiHostAndroid)
            buildConfigField(STRING, "SOCKET_URL", debugSocketHostAndroid)
        }
        listOf("iosArm64", "iosSimulatorArm64").forEach { iosTarget ->
            create(iosTarget) {
                buildConfigField(STRING, "BASE_URL", debugApiHostIos)
                buildConfigField(STRING, "SOCKET_URL", debugSocketHostIos)
            }
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
            implementation(libs.androidx.lifecycle.process)
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
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
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
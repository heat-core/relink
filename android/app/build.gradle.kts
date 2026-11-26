import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes only from the owner configuration in git-ignored local.properties.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// One version for every app: the VERSION file at the repository root (tools/release.sh writes it). versionCode grows
// with every release: major*1000000 + minor*10000 + patch*100 + 99, a pre-release (1.2.0-rc.3) takes its number
// (03) in place of 99 so it sorts before the release.
val tetherVersion = rootProject.file("../VERSION").readText().trim()
val tetherCommit = providers.gradleProperty("tether.commit")
    .orElse(providers.environmentVariable("GITHUB_SHA")).getOrElse("local")
require(tetherCommit == "local" || tetherCommit.matches(Regex("[0-9a-f]{40}"))) { "tether.commit must be a full Git SHA" }
val tetherVersionCode = Regex("""(\d+)\.(\d+)\.(\d+)(?:-[A-Za-z]+\.?(\d+)?)?""").matchEntire(tetherVersion)
    ?.destructured?.let { (ma, mi, pa, pre) ->
        ma.toInt() * 1_000_000 + mi.toInt() * 10_000 + pa.toInt() * 100 +
            (if (tetherVersion.contains('-')) (pre.toIntOrNull() ?: 0).coerceAtMost(98) else 99)
    } ?: error("VERSION must look like 1.2.3 or 1.2.3-rc.1, not '$tetherVersion'")

android {
    namespace = "app.tether"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "app.relink.android"
        minSdk = 26
        targetSdk = 36
        versionCode = tetherVersionCode
        versionName = tetherVersion.substringBefore('-')
        buildConfigField("String", "TETHER_RELEASE", "\"$tetherVersion\"")
        buildConfigField("String", "TETHER_COMMIT", "\"$tetherCommit\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        localProps.getProperty("tether.signing.storeFile")?.let { store ->
            create("own") {
                storeFile = file(store)
                storePassword = localProps.getProperty("tether.signing.storePassword")
                keyAlias = localProps.getProperty("tether.signing.keyAlias")
                keyPassword = localProps.getProperty("tether.signing.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("own")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES")
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint {
        abortOnError = false
        error += "NewApi"
    }
    sourceSets["test"].resources.srcDir("../../protocol")
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation(project(":helper"))
    implementation(project(":core")) { exclude(group = "org.json") } // protocol and codecs, shared with the Android client
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.9.0")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.87")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}

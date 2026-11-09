plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.dhnam.screentranslator"
    compileSdk = 34
    defaultConfig {
        applicationId = "dev.dhnam.screentranslator"
        minSdk = 30
        targetSdk = 34
        // CI에서 VERSION_CODE / VERSION_NAME 환경 변수로 덮어쓴다.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
    }
    // 릴리스 서명: KEYSTORE_FILE과 KEYSTORE_PASSWORD 환경 변수가 있을 때만 쓴다.
    // (키 별칭은 release, keystore 비밀번호와 키 비밀번호는 같다.)
    val keystoreFile = System.getenv("KEYSTORE_FILE")?.let { file(it) }
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    signingConfigs {
        if (keystoreFile != null && keystorePassword != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "release"
                keyPassword = keystorePassword
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("boolean", "UPDATE_ENABLED", "true")
        }
        // 개발용: 릴리스 앱과 나란히 설치되는 "화면 번역 (dev)"
        debug {
            applicationIdSuffix = ".dev"
            // dev 앱은 릴리스 APK로 업데이트할 수 없으므로 업데이트 확인을 끈다.
            buildConfigField("boolean", "UPDATE_ENABLED", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
}

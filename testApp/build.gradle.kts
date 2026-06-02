plugins {
    id("com.android.application") version "9.0.0"
    id("com.androidacy.lsparanoid") version "0.11.0"
}

lsparanoid {
    classFilter = { it.startsWith("com.androidacy.lsparanoid.testapp") }
    // Enable obfuscation for both debug and release to allow testing
    variantFilter = { variant ->
        true
    }

    // 自定义加解密处理器配置
    // 使用示例处理器（XOR 加密）。注释掉以下行则使用默认处理器。
    processor = "com.androidacy.lsparanoid.testapp.ExampleStringProcessor"

    // 加密密钥（写入 DEX 时会混淆处理，不以明文出现）
    // 如果不设置，则由处理器自行管理密钥
    key = "test-secret-key-2024"

    // 加密数据在 DEX 中的显示格式: "base64"(默认), "hex", "bytes"
    mode = "custom"     // 使用 ExampleStringProcessor 自定义的 formatData/parseData（喵呜格式）
}

android {
    namespace = "com.androidacy.lsparanoid.testapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.androidacy.lsparanoid.testapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // No minification for debug - tests run faster
            isMinifyEnabled = false
        }
        release {
            // Enable minification for release to test ProGuard rules
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // 声明原生编译位置，准确到CMakeLists的位置
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel8api35") {
                    device = "Pixel 8"
                    apiLevel = 35
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

dependencies {
    implementation("com.androidacy.lsparanoid:core:0.11.0")

    // Kotlin stdlib needed for annotations used by lsparanoid
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")

    // Testing dependencies
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

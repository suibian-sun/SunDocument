plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appVersionName = (project.findProperty("appVersionName") as String?) ?: "1.9.0"
val appVersionCode = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 27

android {
    namespace = "com.suibian.sunfile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.suibian.sunfile"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables.useSupportLibrary = true
    }

    // 正式签名：CI 通过环境变量注入，本地缺少密钥时自动回退 debug 签名
    val keystorePath = System.getenv("SIGNING_KEYSTORE") ?: "keystore/release.jks"
    val keystoreFile = file(keystorePath)
    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "sunfile2019"
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "sunfile"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "sunfile2019"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // 压缩包：7z / xz / bzip2 的读写（纯 Java，minSdk 26 起无兼容问题）
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    // ZIP 的 AES-256 / ZipCrypto 读写（密码保护）
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    // RAR 只读
    implementation("com.github.junrar:junrar:7.5.5")
    // material-icon-theme 的 SVG 图标渲染
    implementation("com.caverock:androidsvg-aar:1.4")
    // 图片详情里的 EXIF
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // 免 Root 访问 Android/data：SAF 目录
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Shizuku：把文件操作代理给 shell 进程执行，绕过普通 App 的 SELinux / scoped storage 限制
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // 网络存储：FTP/FTPS 用 commons-net，SFTP 用 jsch，SMB2/3 用 smbj
    implementation("commons-net:commons-net:3.10.0")
    implementation("com.github.mwiede:jsch:0.2.20")
    implementation("com.hierynomus:smbj:0.13.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("org.slf4j:slf4j-nop:2.0.13")
    // 给改过的 APK 重签名（v1 + v2 + v3）
    implementation("com.android.tools.build:apksig:8.5.2")
    // Smali 反汇编 / 汇编：dexlib2 + baksmali + smali（纯 Java，运行期只用 antlr runtime）
    implementation("org.smali:dexlib2:2.5.2") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
    }
    implementation("org.smali:baksmali:2.5.2") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
    }
    implementation("org.smali:smali:2.5.2") {
        exclude(group = "org.antlr", module = "antlr")
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "com.beust", module = "jcommander")
    }
    implementation("org.antlr:antlr-runtime:3.5.2")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

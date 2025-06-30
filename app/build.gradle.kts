plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.stockdivarapp"
    compileSdk = 34

    // <<< بخش جدید برای تنظیمات امضا >>>
    signingConfigs {
        create("release") {
            storeFile = file("C:/Users/iTeck/android.keystore")
            keyAlias = "my-key-alias"
            // خواندن رمزها از فایل gradle.properties (روش صحیح)
            storePassword = project.property("RELEASE_STORE_PASSWORD") as String
            keyPassword = project.property("RELEASE_KEY_PASSWORD") as String
        }
    }

    defaultConfig {
        applicationId = "com.example.stockdivarapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // <<< این خط جدید، بیلد release را به تنظیمات امضای بالا متصل می‌کند >>>
            signingConfig = signingConfigs.getByName("release")
        }
    }

    kotlin {
        jvmToolchain(11)
    }

    buildFeatures {
        compose = true
    }

    composeOptions {

    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("com.android.volley:volley:1.2.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-graphics")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.4")
    
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))

    // اضافه‌کردن Firebase Analytics (اجباری نیست ولی برای تست خوبه)
    implementation("com.google.firebase:firebase-analytics")

    // اضافه‌کردن Firebase Cloud Messaging (برای ارسال نوتیف)
    implementation("com.google.firebase:firebase-messaging")
}

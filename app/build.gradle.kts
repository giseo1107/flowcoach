import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// API 키는 local.properties에만 두고 Git에는 올리지 않는다.
//   TRAFFIC_API_KEY=발급받은키          (국가교통정보센터 실시간 교통)
//   KAKAO_REST_API_KEY=카카오REST키     (실제 도로 시연 '내 경로' 장소 검색)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.giseo.flowcoach"
    compileSdk = 35
    // AAOS 표준 차량 API(android.car) — 실제 차속 읽기용
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.giseo.flowcoach"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "TRAFFIC_API_KEY", "\"${localProps.getProperty("TRAFFIC_API_KEY", "")}\"")
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"${localProps.getProperty("KAKAO_REST_API_KEY", "")}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper) // 현재 위치·진행 방향·현재 도로 제한속도

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

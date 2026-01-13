plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.xchat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.xchat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {


    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("com.google.firebase:firebase-database-ktx:21.0.0")
    implementation("com.google.firebase:firebase-firestore-ktx:25.1.3")
    implementation("androidx.activity:activity:1.12.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")


    // Firebase BoM (Bill of Materials)
    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))

    // Firebase dependencies
    implementation("com.google.firebase:firebase-auth-ktx")

    implementation ("com.google.firebase:firebase-database-ktx")


    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.firebase:firebase-analytics")
    // Glide (Image loading)
    implementation("com.github.bumptech.glide:glide:4.16.0")


    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation ("de.hdodenhof:circleimageview:3.1.0")


    implementation ("com.google.firebase:firebase-messaging:24.1.1")

    implementation("io.github.webrtc-sdk:android:137.7151.05")
   // implementation("com.infobip:google-webrtc:1.0.45036")

    implementation ("io.socket:socket.io-client:2.1.0") // WebSocket client

    implementation ("com.squareup.okhttp3:okhttp:4.9.3")

    implementation("com.google.code.gson:gson:2.8.8")



        implementation ("com.google.android.gms:play-services-base:18.10.0")
        // or the specific service you're using:
        implementation ("com.google.android.gms:play-services-location:21.3.0")

}
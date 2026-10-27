plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit)
}

// 单测从仓库根目录跑，方便定位 app/src/main/assets/L2.lexd
tasks.test {
    workingDir = rootProject.projectDir
    systemProperty("lexd.path", "app/src/main/assets/L2.lexd")
}

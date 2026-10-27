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
    api(project(":core:dict"))
    api(project(":core:morph"))
    testImplementation(libs.junit)
}

tasks.test {
    workingDir = rootProject.projectDir
    systemProperty("lexd.path", "app/src/main/assets/L2.lexd")
}

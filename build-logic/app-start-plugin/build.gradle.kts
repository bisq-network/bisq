plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("AppStartPlugin") {
            id = "bisq.gradle.app_start_plugin.AppStartPlugin"
            implementationClass = "bisq.gradle.app_start_plugin.AppStartPlugin"
        }
    }
}

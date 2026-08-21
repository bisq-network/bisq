plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("RegtestPlugin") {
            id = "bisq.gradle.regtest_plugin.RegtestPlugin"
            implementationClass = "bisq.gradle.regtest_plugin.RegtestPlugin"
        }
    }
}

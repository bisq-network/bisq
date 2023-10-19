plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("BisqTorBinaryPlugin") {
            id = "bisq.gradle.tor_binary.BisqTorBinaryPlugin"
            implementationClass = "bisq.gradle.tor_binary.BisqTorBinaryPlugin"
        }
    }
}

dependencies {
    implementation(project(":gradle-tasks"))
}

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("PackagingPlugin") {
            id = "bisq.gradle.packaging.PackagingPlugin"
            implementationClass = "bisq.gradle.packaging.PackagingPlugin"
        }
    }
}

dependencies {
    implementation(project(":gradle-tasks"))
    implementation(libs.commons.codec)
}

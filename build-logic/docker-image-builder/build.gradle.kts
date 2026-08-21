plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("DockerImageBuilderPlugin") {
            id = "bisq.gradle.docker.image_builder.DockerImageBuilderPlugin"
            implementationClass = "bisq.gradle.docker.image_builder.DockerImageBuilderPlugin"
        }
    }
}

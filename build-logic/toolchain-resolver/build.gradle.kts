plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("ToolchainResolverPlugin") {
            id = "bisq.gradle.toolchain_resolver.ToolchainResolverPlugin"
            implementationClass = "bisq.gradle.toolchain_resolver.ToolchainResolverPlugin"
        }
    }
}

package bisq.gradle.packaging.jpackage

data class JPackageAppConfig(
        val appVersion: String,
        val mainJarFileName: String,
        val mainClassName: String,
        val jvmArgs: Set<String>,
)

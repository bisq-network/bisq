package bisq.gradle.packaging.jpackage

import bisq.gradle.packaging.jpackage.package_formats.JPackagePackageFormatConfigs
import java.nio.file.Path

data class JPackageConfig(
        val inputDirPath: Path,
        val outputDirPath: Path,
        val runtimeImageDirPath: Path,
        val appConfig: JPackageAppConfig,
        val packageFormatConfigs: JPackagePackageFormatConfigs
)

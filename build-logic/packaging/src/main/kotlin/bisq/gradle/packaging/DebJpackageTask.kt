package bisq.gradle.packaging

import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class DebJpackageTask : JPackageTask() {

    @get:Internal
    override val replacedPackageFormats = setOf(PackageFormat.DEB)

    @TaskAction
    override fun run() {
        val jPackagePath = jdkDirectory.asFile.get().toPath().resolve("bin").resolve("jpackage")
        val jPackageConfig = createJPackageConfig()

        DebPackager(jPackagePath, jPackageConfig)
            .build(computePackageFormatConfigs())
    }
}

package bisq.gradle.packaging

import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class RpmJpackageTask : JPackageTask() {

    @get:Internal
    override val replacedPackageFormats = setOf(PackageFormat.RPM)

    @TaskAction
    override fun run() {
        val jPackagePath = jdkDirectory.asFile.get().toPath().resolve("bin").resolve("jpackage")
        val jPackageConfig = createJPackageConfig()

        RpmPackager(jPackagePath, jPackageConfig)
            .build(computePackageFormatConfigs())
    }
}

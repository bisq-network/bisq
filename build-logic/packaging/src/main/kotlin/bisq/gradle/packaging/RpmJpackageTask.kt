package bisq.gradle.packaging

import org.gradle.api.tasks.TaskAction

abstract class RpmJpackageTask : JPackageTask() {

    @TaskAction
    override fun run() {
        val jPackagePath = jdkDirectory.asFile.get().toPath().resolve("bin").resolve("jpackage")
        val jPackageConfig = createJPackageConfig()

        RpmPackager(jPackagePath, jPackageConfig)
            .build(computePackageFormatConfigs())
    }
}

package bisq.gradle.packaging

import bisq.gradle.packaging.jpackage.JPackageAppConfig
import bisq.gradle.packaging.jpackage.JPackageConfig
import bisq.gradle.packaging.jpackage.PackageFactory
import bisq.gradle.packaging.jpackage.package_formats.JPackagePackageFormatConfigs
import bisq.gradle.packaging.jpackage.package_formats.LinuxPackages
import bisq.gradle.packaging.jpackage.package_formats.MacPackage
import bisq.gradle.packaging.jpackage.package_formats.WindowsPackage
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import java.io.File

abstract class JPackageTask : DefaultTask() {

    @get:InputDirectory
    abstract val jdkDirectory: DirectoryProperty

    @get:InputDirectory
    abstract val distDirFile: Property<File>

    @get:InputFile
    abstract val mainJarFile: RegularFileProperty

    @get:Input
    abstract val mainClassName: Property<String>

    @get:Input
    abstract val jvmArgs: SetProperty<String>

    @get:Input
    abstract val appVersion: Property<String>

    @get:InputDirectory
    abstract val packageResourcesDir: DirectoryProperty

    @get:InputDirectory
    abstract val runtimeImageDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val jPackagePath = jdkDirectory.asFile.get().toPath().resolve("bin").resolve("jpackage")

        val jPackageConfig = JPackageConfig(
                inputDirPath = distDirFile.get().toPath().resolve("lib"),
                outputDirPath = outputDirectory.asFile.get().toPath(),
                runtimeImageDirPath = runtimeImageDirectory.asFile.get().toPath(),

                appConfig = JPackageAppConfig(
                        appVersion = appVersion.get(),
                        mainJarFileName = mainJarFile.asFile.get().name,
                        mainClassName = mainClassName.get(),
                        jvmArgs = jvmArgs.get()
                ),

                packageFormatConfigs = getPackageFormatConfigs()
        )

        val packageFactory = PackageFactory(jPackagePath, jPackageConfig)
        packageFactory.createPackages()
    }

    private fun getPackageFormatConfigs(): JPackagePackageFormatConfigs {
        val packagePath = packageResourcesDir.asFile.get().toPath()
        return when (getOS()) {
            OS.WINDOWS -> {
                val resourcesPath = packagePath.resolve("windows")
                WindowsPackage(resourcesPath)
            }

            OS.MAC_OS -> {
                val resourcesPath = packagePath.resolve("macosx")
                MacPackage(resourcesPath)
            }

            OS.LINUX -> {
                val resourcesPath = packagePath.resolve("linux")
                LinuxPackages(resourcesPath)
            }
        }
    }
}

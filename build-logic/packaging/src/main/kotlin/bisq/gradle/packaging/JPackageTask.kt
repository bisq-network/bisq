package bisq.gradle.packaging

import bisq.gradle.packaging.jpackage.JPackageAppConfig
import bisq.gradle.packaging.jpackage.JPackageConfig
import bisq.gradle.packaging.jpackage.PackageFactory
import bisq.gradle.packaging.jpackage.package_formats.JPackagePackageFormatConfigs
import bisq.gradle.packaging.jpackage.package_formats.LinuxPackages
import bisq.gradle.packaging.jpackage.package_formats.MacPackage
import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import bisq.gradle.packaging.jpackage.package_formats.WindowsPackage
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.util.Comparator

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
    abstract val jvmArgs: ListProperty<String>

    @get:Input
    abstract val appVersion: Property<String>

    @get:Input
    abstract val sourceDateEpochSeconds: Property<Long>

    @get:InputDirectory
    abstract val packageResourcesDir: DirectoryProperty

    @get:InputDirectory
    abstract val runtimeImageDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val jPackagePath = jdkDirectory.asFile.get().toPath().resolve("bin").resolve("jpackage")
        val outputDirectoryFile = outputDirectory.asFile.get()
        val sourceDateEpoch = sourceDateEpochSeconds.get()
        deleteExistingInstallerArtifacts(outputDirectoryFile)

        val jPackageConfig = JPackageConfig(
                inputDirPath = distDirFile.get().toPath().resolve("lib"),
                outputDirPath = outputDirectoryFile.toPath(),
                runtimeImageDirPath = runtimeImageDirectory.asFile.get().toPath(),
                temporaryDirPath = temporaryDir.toPath(),
                sourceDateEpochSeconds = sourceDateEpoch,

                appConfig = JPackageAppConfig(
                        appVersion = appVersion.get(),
                        mainJarFileName = mainJarFile.asFile.get().name,
                        mainClassName = mainClassName.get(),
                        jvmArgs = jvmArgs.get()
                ),

                packageFormatConfigs = getPackageFormatConfigs(sourceDateEpoch)
        )

        val packageFactory = PackageFactory(jPackagePath, jPackageConfig)
        packageFactory.createPackages()
    }

    private fun deleteExistingInstallerArtifacts(outputDirectoryFile: File) {
        outputDirectoryFile.mkdirs()
        val installerExtensions = PackageFormat.values()
                .map { it.fileExtension }
                .toSet()
        outputDirectoryFile.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in installerExtensions }
                ?.forEach { installerArtifact ->
                    if (!installerArtifact.delete()) {
                        throw GradleException("Failed to delete stale installer artifact: ${installerArtifact.absolutePath}")
                    }
                }
    }

    private fun getPackageFormatConfigs(sourceDateEpochSeconds: Long): JPackagePackageFormatConfigs {
        val packagePath = packageResourcesDir.asFile.get().toPath()
        return when (getOS()) {
            OS.WINDOWS -> {
                val resourcesPath = packagePath.resolve("windows")
                WindowsPackage(resourcesPath)
            }

            OS.MAC_OS -> {
                val resourcesPath = packagePath.resolve("macosx")
                MacPackage(prepareMacPackageResources(resourcesPath, sourceDateEpochSeconds))
            }

            OS.LINUX -> {
                val resourcesPath = packagePath.resolve("linux")
                LinuxPackages(resourcesPath)
            }
        }
    }

    private fun prepareMacPackageResources(resourcesPath: Path, sourceDateEpochSeconds: Long): Path {
        val preparedResourcesPath = temporaryDir.toPath().resolve("macosx-resources")
        preparedResourcesPath.toFile().deleteRecursively()

        val copyrightYear = Instant.ofEpochSecond(sourceDateEpochSeconds)
                .atZone(ZoneOffset.UTC)
                .year
                .toString()

        Files.walk(resourcesPath).use { paths ->
            paths.sorted(Comparator.comparing { path -> resourcesPath.relativize(path).toString() })
                    .forEach { source ->
                        if (source == resourcesPath) {
                            return@forEach
                        }

                        val target = preparedResourcesPath.resolve(resourcesPath.relativize(source).toString())
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(target)
                        } else {
                            Files.createDirectories(target.parent)
                            if (source.fileName.toString() == "Info.plist") {
                                val infoPlist = Files.readString(source, StandardCharsets.UTF_8)
                                        .replace("@BISQ_COPYRIGHT_YEAR@", copyrightYear)
                                        .replace(
                                                Regex("""Copyright © 2013-\d{4} - The Bisq developers"""),
                                                "Copyright © 2013-${copyrightYear} - The Bisq developers"
                                        )
                                Files.writeString(target, infoPlist, StandardCharsets.UTF_8)
                            } else {
                                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                            }
                        }
                    }
        }

        return preparedResourcesPath
    }
}

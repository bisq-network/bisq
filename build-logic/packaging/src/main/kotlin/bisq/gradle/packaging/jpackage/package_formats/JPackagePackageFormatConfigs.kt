package bisq.gradle.packaging.jpackage.package_formats

interface JPackagePackageFormatConfigs {
    val packageFormats: Set<PackageFormat>
    fun createArgumentsForJPackage(packageFormat: PackageFormat): List<String>
}

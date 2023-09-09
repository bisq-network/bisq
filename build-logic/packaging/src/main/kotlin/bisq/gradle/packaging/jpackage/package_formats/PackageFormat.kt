package bisq.gradle.packaging.jpackage.package_formats

enum class PackageFormat(val fileExtension: String) {
    DEB("deb"),
    DMG("dmg"),
    EXE("exe"),
    RPM("rpm")
}

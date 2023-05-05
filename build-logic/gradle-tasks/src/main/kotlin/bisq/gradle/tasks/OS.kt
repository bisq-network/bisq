package bisq.gradle.tasks

import java.util.*

enum class OS {
    LINUX, MAC_OS, WINDOWS
}

fun getOS(): OS {
    val osName = System.getProperty("os.name").toLowerCase(Locale.US)
    if (isLinux(osName)) {
        return OS.LINUX
    } else if (isMacOs(osName)) {
        return OS.MAC_OS
    } else if (isWindows(osName)) {
        return OS.WINDOWS
    }

    throw IllegalStateException("Running on unsupported OS: $osName")
}

private fun isLinux(osName: String): Boolean {
    return osName.contains("linux")
}

private fun isMacOs(osName: String): Boolean {
    return osName.contains("mac") || osName.contains("darwin")
}

private fun isWindows(osName: String): Boolean {
    return osName.contains("win")
}
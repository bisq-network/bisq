package bisq.gradle.tasks

import java.net.URI
import java.net.URL

interface PerOsUrlProvider {
    val urlPrefix: String

    val linuxUrl: String
    val macOsUrl: String
    val windowsUrl: String

    val url: URL
        get() = URI(urlPrefix + getUrlSuffix()).toURL()

    private fun getUrlSuffix() =
        when (getOS()) {
            OS.LINUX -> linuxUrl
            OS.MAC_OS -> macOsUrl
            OS.WINDOWS -> windowsUrl
        }

}

package bisq.gradle.tor_binary

import org.gradle.api.provider.Property

abstract class BisqTorBinaryPluginExtension {
    abstract val version: Property<String>
}
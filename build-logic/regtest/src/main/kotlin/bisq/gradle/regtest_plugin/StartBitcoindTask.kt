package bisq.gradle.regtest_plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class StartBitcoindTask : DefaultTask() {

    @get:InputDirectory
    abstract val dataDirectory: DirectoryProperty

    @get:Input
    abstract val port: Property<Int>

    @get:Input
    abstract val onionPort: Property<Int>

    @get:Input
    abstract val otherNodes: SetProperty<String>

    @get:Input
    abstract val rpcAllowIp: Property<String>

    @get:Input
    abstract val rpcBindPort: Property<Int>

    @get:Input
    abstract val rpcUser: Property<String>

    @get:Input
    abstract val rpcPassword: Property<String>

    @get:Input
    abstract val blockNotifyArg: Property<String>

    @get:Internal
    abstract val pidFile: RegularFileProperty

    init {
        port.convention(18444)
        onionPort.convention(18445)
        rpcAllowIp.convention("")
        rpcBindPort.convention(18443)
    }

    @TaskAction
    fun run() {
        val command = mutableListOf(
            "bitcoind",
            "-datadir=${dataDirectory.asFile.get().absolutePath}",
            "-regtest",

            "-port=${port.get()}",
            "-bind=127.0.0.1:${onionPort.get()}=onion",

            "-prune=0",
            "-txindex=1",
            "-peerbloomfilters=1",
            "-server",

            "-rpcbind=127.0.0.1:${rpcBindPort.get()}",
            "-rpcuser=${rpcUser.get()}",
            "-rpcpassword=${rpcPassword.get()}",

            "-blocknotify=${blockNotifyArg.get()}"
        )

        otherNodes.get().forEach { node -> command.add("-addnode=$node") }

        val allowIp = rpcAllowIp.get()
        if (allowIp.isNotEmpty()) {
            command.add("-rpcallowip=$allowIp")
        }

        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(true)
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)

        val process = processBuilder.start()
        val pid = process.pid()

        pidFile.asFile
            .get()
            .writeText(pid.toString())
    }
}

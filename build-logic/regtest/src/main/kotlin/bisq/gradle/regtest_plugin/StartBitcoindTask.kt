package bisq.gradle.regtest_plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class StartBitcoindTask : DefaultTask() {

    @get:Internal
    abstract val pidFile: RegularFileProperty

    @get:InputDirectory
    abstract val dataDirectory: DirectoryProperty

    @get:Input
    abstract val rpcUser: Property<String>

    @get:Input
    abstract val rpcPassword: Property<String>

    @get:Input
    abstract val blockNotifyArg: Property<String>

    @TaskAction
    fun run() {
        ProcessKiller(pidFile.asFile.get())
                .kill()

        // Wait until process stopped
        Thread.sleep(5000)

        val processBuilder = ProcessBuilder(
                "bitcoind",
                "-datadir=${dataDirectory.asFile.get().absolutePath}",
                "-regtest",

                "-prune=0",
                "-txindex=1",
                "-peerbloomfilters=1",
                "-server",

                "-rpcuser=${rpcUser.get()}",
                "-rpcpassword=${rpcPassword.get()}",

                "-blocknotify= ${blockNotifyArg.get()}"
        )

        processBuilder.redirectErrorStream(true)
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)

        val process = processBuilder.start()
        val pid = process.pid()

        pidFile.asFile
                .get()
                .writeText(pid.toString())
    }
}

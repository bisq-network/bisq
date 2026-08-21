package bisq.gradle.regtest_plugin

import java.io.File

class ProcessKiller(private val pidFile: File) {

    companion object {
        private const val SIG_INT_NUMBER = 2
    }

    fun kill() {
        if (!pidFile.exists()) {
            return
        }

        val pid = pidFile.readText()
        val processBuilder = ProcessBuilder(
                "kill",
                "-s", SIG_INT_NUMBER.toString(),
                pid
        )

        processBuilder.redirectErrorStream(true)
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)

        val process = processBuilder.start()
        process.waitFor()


    }
}

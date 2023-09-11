package bisq.gradle.regtest_plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class KillTask : DefaultTask() {

    @get:Internal
    abstract val pidFile: RegularFileProperty

    @TaskAction
    fun run() {
        ProcessKiller(pidFile.asFile.get())
                .kill()
    }
}

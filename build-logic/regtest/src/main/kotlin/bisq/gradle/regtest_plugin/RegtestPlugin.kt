package bisq.gradle.regtest_plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class RegtestPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val startBitcoindTask = project.tasks.register<StartBitcoindTask>("startBitcoindRegtest") {
            dataDirectory.set(project.layout.projectDirectory.dir(".localnet/bitcoind"))
            rpcUser.set("bisqdao")
            rpcPassword.set("bsq")
            blockNotifyArg.set(".localnet/bitcoind/blocknotify %s")
        }
    }
}

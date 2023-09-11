package bisq.gradle.regtest_plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class RegtestPlugin : Plugin<Project> {

    companion object {
        const val RPC_USER = "bisqdao"
        const val RPC_PASSWORD = "bsq"
    }

    override fun apply(project: Project) {
        val startBitcoindTask = project.tasks.register<StartBitcoindTask>("startRegtestBitcoind") {
            dataDirectory.set(project.layout.projectDirectory.dir(".localnet/bitcoind"))
            rpcUser.set(RPC_USER)
            rpcPassword.set(RPC_PASSWORD)
            blockNotifyArg.set(".localnet/bitcoind/blocknotify %s")
        }

        val startFirstSeedNodeTask = project.tasks.register<StartSeedNodeTask>("startRegtestFirstSeednode") {
            dependsOn(startBitcoindTask)
            startScriptFile.set(project.layout.projectDirectory.file("bisq-seednode"))

            arguments.set(
                    createSeedNodeArgs(5120, 2002, "seednode")
            )

            workingDirectory.set(project.layout.projectDirectory)
            logFile.set(project.layout.projectDirectory.file(".localnet/seednode_1_shell.log"))
        }

        val startSecondSeedNodeTask = project.tasks.register<StartSeedNodeTask>("startRegtestSecondSeednode") {
            dependsOn(startBitcoindTask)
            dependsOn(startFirstSeedNodeTask)
            startScriptFile.set(project.layout.projectDirectory.file("bisq-seednode"))

            arguments.set(
                    createSeedNodeArgs(5121, 3002, "seednode2")
            )

            workingDirectory.set(project.layout.projectDirectory)
            logFile.set(project.layout.projectDirectory.file(".localnet/seednode_2_shell.log"))
        }
    }

    private fun createSeedNodeArgs(blockNotificationPort: Int, nodePort: Int, appName: String): List<String> = listOf(
            "--baseCurrencyNetwork=BTC_REGTEST",
            "--useLocalhostForP2P=true",
            "--useDevPrivilegeKeys=true",
            "--fullDaoNode=true",

            "--rpcUser=${RPC_USER}",
            "--rpcPassword=${RPC_PASSWORD}",
            "--rpcBlockNotificationPort=$blockNotificationPort",

            "--nodePort=$nodePort",
            "--userDataDir=.localnet",
            "--appName=$appName"
    )
}

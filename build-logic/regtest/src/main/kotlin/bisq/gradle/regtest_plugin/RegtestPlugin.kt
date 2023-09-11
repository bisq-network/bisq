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

        val startFirstSeedNodeTask = project.tasks.register<StartBisqTask>("startRegtestFirstSeednode") {
            dependsOn(startBitcoindTask)
            startScriptFile.set(project.layout.projectDirectory.file("bisq-seednode"))

            arguments.set(
                    createSeedNodeArgs(5120, 2002, "seednode")
            )

            workingDirectory.set(project.layout.projectDirectory)
            logFile.set(project.layout.projectDirectory.file(".localnet/seednode_1_shell.log"))
        }

        val startSecondSeedNodeTask = project.tasks.register<StartBisqTask>("startRegtestSecondSeednode") {
            dependsOn(startBitcoindTask)
            dependsOn(startFirstSeedNodeTask)
            startScriptFile.set(project.layout.projectDirectory.file("bisq-seednode"))

            arguments.set(
                    createSeedNodeArgs(5121, 3002, "seednode2")
            )

            workingDirectory.set(project.layout.projectDirectory)
            logFile.set(project.layout.projectDirectory.file(".localnet/seednode_2_shell.log"))
        }

        val startMediatorTask = project.tasks.register<StartBisqTask>("startRegtestMediator") {
            dependsOn(startFirstSeedNodeTask)
            dependsOn(startSecondSeedNodeTask)

            startScriptFile.set(project.layout.projectDirectory.file("bisq-desktop"))

            arguments.set(
                    createBisqUserArgs(4444, ".localnet/mediator", "Mediator")
            )

            workingDirectory.set(project.layout.projectDirectory)
            logFile.set(project.layout.projectDirectory.file(".localnet/mediator_shell.log"))
        }

        val startAliceTask = project.tasks.register<StartBisqTask>("startRegtestAlice") {
            dependsOn(startFirstSeedNodeTask)
            dependsOn(startSecondSeedNodeTask)

            startScriptFile.set(project.layout.projectDirectory.file("bisq-desktop"))

            val additionalArgs = listOf(
                    "--fullDaoNode=true",
                    "--rpcUser=bisqdao",
                    "--rpcPassword=bsq",
                    "--rpcBlockNotificationPort=5122",
                    "--genesisBlockHeight=111",
                    "--genesisTxId=30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf"
            )
            arguments.set(
                    createBisqUserArgs(5555, ".localnet/alice", "Alice", additionalArgs)
            )

            workingDirectory.set(project.layout.projectDirectory)
            logFile.set(project.layout.projectDirectory.file(".localnet/alice_shell.log"))
        }

        project.tasks.register<StartBisqTask>("startRegtest") {
            dependsOn(startMediatorTask)
            dependsOn(startAliceTask)

            startScriptFile.set(project.layout.projectDirectory.file("bisq-desktop"))

            arguments.set(
                    createBisqUserArgs(6666, ".localnet/bob", "Bob")
            )

            workingDirectory.set(project.layout.projectDirectory)
            logFile.set(project.layout.projectDirectory.file(".localnet/bob_shell.log"))
        }
    }

    private fun createBisqUserArgs(nodePort: Int,
                                   dataDir: String,
                                   appName: String,
                                   additionalArgs: List<String> = emptyList()): List<String> =
            createBisqCommonArgs(nodePort) +
                    listOf(
                            "--appDataDir=$dataDir",
                            "--appName=$appName"
                    ) + additionalArgs

    private fun createSeedNodeArgs(blockNotificationPort: Int, nodePort: Int, appName: String): List<String> =
            createBisqCommonArgs(nodePort) +
                    listOf(
                            "--fullDaoNode=true",

                            "--rpcUser=${RPC_USER}",
                            "--rpcPassword=${RPC_PASSWORD}",
                            "--rpcBlockNotificationPort=$blockNotificationPort",

                            "--userDataDir=.localnet",
                            "--appName=$appName"
                    )

    private fun createBisqCommonArgs(nodePort: Int): List<String> =
            listOf(
                    "--baseCurrencyNetwork=BTC_REGTEST",
                    "--useLocalhostForP2P=true",
                    "--useDevPrivilegeKeys=true",
                    "--nodePort=$nodePort"
            )
}

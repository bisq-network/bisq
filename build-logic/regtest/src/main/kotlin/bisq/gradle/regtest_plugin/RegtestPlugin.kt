package bisq.gradle.regtest_plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.register
import java.io.File
import javax.inject.Inject

@Suppress("unused")
class RegtestPlugin @Inject constructor(private val javaToolchainService: JavaToolchainService) : Plugin<Project> {

    companion object {
        const val RPC_USER = "bisqdao"
        const val RPC_PASSWORD = "bsq"
    }

    override fun apply(project: Project) {
        val initialDaoSetupTask = project.tasks.register<Copy>("initialDaoSetup") {
            from(project.zipTree("docs/dao-setup.zip"))
            into(project.layout.projectDirectory.dir(".localnet"))

            exclude("dao-setup/Bitcoin-regtest/bitcoin.conf")

            eachFile {
                path = sourcePath.replace("dao-setup/Bitcoin-regtest", "bitcoind")
                    .replace("dao-setup/bisq-BTC_REGTEST_Alice_dao", "alice")
                    .replace("dao-setup/bisq-BTC_REGTEST_Bob_dao", "bob")
            }

            includeEmptyDirs = false
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE

            doLast {
                val blockNotifyFile = project.layout.projectDirectory
                    .dir(".localnet")
                    .dir("bitcoind")
                    .file("blocknotify")
                    .asFile
                blockNotifyFile.appendText("echo exit 0")

                project.layout.projectDirectory
                    .dir(".localnet")
                    .dir("bitcoind2")
                    .asFile
                    .mkdirs()
            }
        }

        val startFirstBitcoindTask = project.tasks.register<StartBitcoindTask>("startFirstRegtestBitcoind") {
            dependsOn(initialDaoSetupTask)
            pidFile.set(project.layout.projectDirectory.file(".localnet/bitcoind.pid"))

            dataDirectory.set(project.layout.projectDirectory.dir(".localnet/bitcoind"))
            rpcUser.set(RPC_USER)
            rpcPassword.set(RPC_PASSWORD)
            blockNotifyArg.set(".localnet/bitcoind/blocknotify %s")
        }

        val stopFirstBitcoindTask = project.tasks.register<KillTask>("stopFirstRegtestBitcoind") {
            pidFile.set(startFirstBitcoindTask.flatMap { it.pidFile })
        }

        val startSecondBitcoindTask = project.tasks.register<StartBitcoindTask>("startSecondRegtestBitcoind") {
            dependsOn(initialDaoSetupTask)
            pidFile.set(project.layout.projectDirectory.file(".localnet/bitcoind_2.pid"))

            dataDirectory.set(project.layout.projectDirectory.dir(".localnet/bitcoind2"))
            port.set(19444)
            onionPort.set(19445)
            otherNodes.add("127.0.0.1:18444")

            rpcAllowIp.set("127.0.0.1")
            rpcBindPort.set(19443)
            rpcUser.set(RPC_USER)
            rpcPassword.set(RPC_PASSWORD)

            blockNotifyArg.set(".localnet/bitcoind/blocknotify %s")
        }

        val stopSecondBitcoindTask = project.tasks.register<KillTask>("stopSecondRegtestBitcoind") {
            pidFile.set(startSecondBitcoindTask.flatMap { it.pidFile })
        }

        val seedNodeLibsDirProvider: Provider<Directory> = getBisqAppLibsDirProvider(project, "seednode")

        val startFirstSeedNodeTask = project.tasks.register<StartBisqTask>("startRegtestFirstSeednode") {
            dependsOn(startFirstBitcoindTask)

            workingDirectory.set(project.layout.projectDirectory)
            javaExecutable.set(getJavaExecutable())
            libsDir.set(seedNodeLibsDirProvider)

            mainClass.set("bisq.seednode.SeedNodeMain")
            arguments.set(
                createSeedNodeArgs(5120, 2002, "seednode")
            )

            logFile.set(project.layout.projectDirectory.file(".localnet/seednode_1_shell.log"))
            pidFile.set(project.layout.projectDirectory.file(".localnet/seednode_1.pid"))
        }

        val stopFirstSeedNodeTask = project.tasks.register<KillTask>("stopRegtestFirstSeednode") {
            pidFile.set(startFirstSeedNodeTask.flatMap { it.pidFile })
        }

        val startSecondSeedNodeTask = project.tasks.register<StartBisqTask>("startRegtestSecondSeednode") {
            dependsOn(startFirstBitcoindTask)
            dependsOn(startFirstSeedNodeTask)

            workingDirectory.set(project.layout.projectDirectory)
            javaExecutable.set(getJavaExecutable())
            libsDir.set(seedNodeLibsDirProvider)

            mainClass.set("bisq.seednode.SeedNodeMain")
            arguments.set(
                createSeedNodeArgs(5121, 3002, "seednode2")
            )

            logFile.set(project.layout.projectDirectory.file(".localnet/seednode_2_shell.log"))
            pidFile.set(project.layout.projectDirectory.file(".localnet/seednode_2.pid"))
        }

        val stopSeedNodeTask = project.tasks.register<KillTask>("stopRegtestSecondSeednode") {
            pidFile.set(startSecondSeedNodeTask.flatMap { it.pidFile })
        }

        val desktopAppLibsDirProvider: Provider<Directory> = getBisqAppLibsDirProvider(project, "desktop")

        val startMediatorTask = project.tasks.register<StartBisqTask>("startRegtestMediator") {
            dependsOn(startFirstSeedNodeTask)
            dependsOn(startSecondSeedNodeTask)

            workingDirectory.set(project.layout.projectDirectory)
            javaExecutable.set(getJavaExecutable())
            libsDir.set(desktopAppLibsDirProvider)

            mainClass.set("bisq.desktop.app.BisqAppMain")
            arguments.set(
                createBisqUserArgs(4444, ".localnet/mediator", "Mediator")
            )

            logFile.set(project.layout.projectDirectory.file(".localnet/mediator_shell.log"))
            pidFile.set(project.layout.projectDirectory.file(".localnet/mediator.pid"))
        }

        val stopMediatorTask = project.tasks.register<KillTask>("stopRegtestMediator") {
            pidFile.set(startMediatorTask.flatMap { it.pidFile })
        }

        val startAliceTask = project.tasks.register<StartBisqTask>("startRegtestAlice") {
            dependsOn(startFirstSeedNodeTask)
            dependsOn(startSecondSeedNodeTask)

            workingDirectory.set(project.layout.projectDirectory)
            javaExecutable.set(getJavaExecutable())
            libsDir.set(desktopAppLibsDirProvider)

            mainClass.set("bisq.desktop.app.BisqAppMain")

            val additionalArgs = listOf(
                "--fullDaoNode=true",
                "--isBmFullNode=true",
                "--rpcUser=bisqdao",
                "--rpcPassword=bsq",
                "--rpcBlockNotificationPort=5122",
                "--genesisBlockHeight=111",
                "--genesisTxId=30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf"
            )
            arguments.set(
                createBisqUserArgs(5555, ".localnet/alice", "Alice", additionalArgs)
            )

            logFile.set(project.layout.projectDirectory.file(".localnet/alice_shell.log"))
            pidFile.set(project.layout.projectDirectory.file(".localnet/alice.pid"))
        }

        val stopAliceTask = project.tasks.register<KillTask>("stopRegtestAlice") {
            pidFile.set(startAliceTask.flatMap { it.pidFile })
        }

        val startBobTask = project.tasks.register<StartBisqTask>("startRegtest") {
            dependsOn(startMediatorTask)
            dependsOn(startAliceTask)

            workingDirectory.set(project.layout.projectDirectory)
            javaExecutable.set(getJavaExecutable())
            libsDir.set(desktopAppLibsDirProvider)

            mainClass.set("bisq.desktop.app.BisqAppMain")
            arguments.set(
                createBisqUserArgs(6666, ".localnet/bob", "Bob")
            )

            logFile.set(project.layout.projectDirectory.file(".localnet/bob_shell.log"))
            pidFile.set(project.layout.projectDirectory.file(".localnet/bob.pid"))
        }

        project.tasks.register<KillTask>("stopRegtest") {
            dependsOn(stopFirstBitcoindTask)
            dependsOn(stopSecondBitcoindTask)

            dependsOn(stopFirstSeedNodeTask)
            dependsOn(stopSeedNodeTask)

            dependsOn(stopMediatorTask)
            dependsOn(stopAliceTask)

            pidFile.set(startBobTask.flatMap { it.pidFile })
        }
    }

    private fun getBisqAppLibsDirProvider(project: Project, moduleName: String): Provider<Directory> =
        project.provider {
            project.project(moduleName)
        }.flatMap { seedNodeProject ->
            seedNodeProject.tasks.named("installDist", Sync::class.java)
        }.flatMap { installDistTask ->
            project.layout.dir(project.provider {
                File(installDistTask.destinationDir, "lib")
            })
        }

    private fun getJavaExecutable(): Provider<RegularFile> =
        javaToolchainService.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.AZUL)
            implementation.set(JvmImplementation.VENDOR_SPECIFIC)
        }.map { it.executablePath }

    private fun createBisqUserArgs(
        nodePort: Int,
        dataDir: String,
        appName: String,
        additionalArgs: List<String> = emptyList()
    ): List<String> =
        createBisqCommonArgs(nodePort) +
                listOf(
                    "--appDataDir=$dataDir",
                    "--appName=$appName"
                ) + additionalArgs

    private fun createSeedNodeArgs(blockNotificationPort: Int, nodePort: Int, appName: String): List<String> =
        createBisqCommonArgs(nodePort) +
                listOf(
                    "--fullDaoNode=true",
                    "--isBmFullNode=true",

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

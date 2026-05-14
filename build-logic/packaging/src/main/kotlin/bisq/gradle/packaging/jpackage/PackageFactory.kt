package bisq.gradle.packaging.jpackage

import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import org.gradle.api.GradleException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.Year
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Comparator
import java.util.Locale
import java.util.concurrent.TimeUnit

class PackageFactory(private val jPackagePath: Path, private val jPackageConfig: JPackageConfig) {

    companion object {
        private const val SOURCE_DATE_EPOCH = "SOURCE_DATE_EPOCH"
        private const val DEFAULT_SOURCE_DATE_EPOCH = "0"
        private const val HFS_EPOCH_OFFSET_SECONDS = 2_082_844_800L
        private const val HFS_VOLUME_HEADER_OFFSET_FROM_VOLUME_START = 1024L
        private const val HFS_VOLUME_HEADER_SIZE = 512
        private const val HFS_VOLUME_HEADER_SCAN_BYTES = 1024 * 1024
        private const val HFS_SIGNATURE = 0x482b
        private const val HFS_VERSION = 0x0004
        private const val HFS_BLOCK_SIZE_OFFSET = 40
        private const val HFS_TOTAL_BLOCKS_OFFSET = 44
        private const val HFS_CATALOG_FILE_OFFSET = 272L
        private const val HFS_FORK_EXTENTS_OFFSET = 16
        private const val HFS_EXTENT_COUNT = 8
        private const val HFS_BTREE_NODE_DESCRIPTOR_SIZE = 14
        private const val HFS_BTREE_HEADER_NODE_KIND = 0x01.toByte()
        private const val HFS_BTREE_LEAF_NODE_KIND = 0xff.toByte()
        private const val HFS_CATALOG_FOLDER_RECORD = 0x0001
        private const val HFS_CATALOG_FILE_RECORD = 0x0002
        private const val HFS_CATALOG_RECORD_DATES_OFFSET = 12

        private val FIXED_HFS_VOLUME_UUID = "BisqVol1".toByteArray(Charsets.US_ASCII)
        private val FIXED_UDIF_UUID = "BisqUDIFVolume01".toByteArray(Charsets.US_ASCII)
    }

    private data class HfsExtent(val startBlock: Long, val blockCount: Long)

    private data class HfsCatalogFile(val volumeStart: Long, val blockSize: Long, val logicalSize: Long, val extents: List<HfsExtent>)

    fun createPackages() {
        val jPackageCommonArgs: List<String> = createCommonArguments(jPackageConfig.appConfig)

        val packageFormatConfigs = jPackageConfig.packageFormatConfigs
        val perPackageCommand = packageFormatConfigs.packageFormats
                .map { packageFormat ->
                    packageFormat to packageFormatConfigs.createArgumentsForJPackage(packageFormat) + listOf("--type", packageFormat.fileExtension)
                }

        perPackageCommand.forEach { (packageFormat, customCommands) ->
            val processBuilder = ProcessBuilder(jPackagePath.toAbsolutePath().toString())
                    .inheritIO()
            processBuilder.environment().putIfAbsent(SOURCE_DATE_EPOCH, DEFAULT_SOURCE_DATE_EPOCH)
            configureReproducibleRpmEnvironment(processBuilder, packageFormat)

            val allCommands = processBuilder.command()
            allCommands.addAll(jPackageCommonArgs)
            allCommands.addAll(customCommands)

            val process: Process = processBuilder.start()
            val finished = process.waitFor(15, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                throw GradleException("jpackage timed out after 15 minutes: ${allCommands.joinToString(" ")}")
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                throw GradleException("jpackage failed with exit code $exitCode: ${allCommands.joinToString(" ")}")
            }

            if (packageFormat == PackageFormat.RPM) {
                normalizeRpmPackage(jPackageConfig.appConfig)
            }
            if (packageFormat == PackageFormat.DMG) {
                normalizeDmgPackage()
            }
        }
    }

    private fun createCommonArguments(appConfig: JPackageAppConfig): List<String> =
            mutableListOf(
                    "--dest", jPackageConfig.outputDirPath.toAbsolutePath().toString(),

                    "--name", "Bisq",
                    "--description", "A decentralized bitcoin exchange network.",
                    "--copyright", "Copyright © 2013-${Year.now()} - The Bisq developers",
                    "--vendor", "Bisq",

                    "--app-version", appConfig.appVersion,

                    "--input", jPackageConfig.inputDirPath.toAbsolutePath().toString(),
                    "--main-jar", appConfig.mainJarFileName,

                    "--main-class", appConfig.mainClassName
            ).apply {
                appConfig.jvmArgs.forEach { jvmArg ->
                    add("--java-options")
                    add(jvmArg)
                }
                add("--runtime-image")
                add(jPackageConfig.runtimeImageDirPath.toAbsolutePath().toString())
            }

    private fun configureReproducibleRpmEnvironment(processBuilder: ProcessBuilder, packageFormat: PackageFormat) {
        if (packageFormat != PackageFormat.RPM) {
            return
        }

        processBuilder.environment()["HOME"] = createRpmHome().toAbsolutePath().toString()
    }

    private fun createRpmHome(): Path {
        val rpmHomePath = jPackageConfig.temporaryDirPath.resolve("rpm-home")
        Files.createDirectories(rpmHomePath)
        Files.writeString(
                rpmHomePath.resolve(".rpmmacros"),
                """
                    %use_source_date_epoch_as_buildtime 1
                    %clamp_mtime_to_source_date_epoch 1
                    %_buildhost bisq-release-builder
                """.trimIndent() + "\n"
        )
        return rpmHomePath
    }

    private fun normalizeRpmPackage(appConfig: JPackageAppConfig) {
        val rpmPath = findSinglePackageArtifact(PackageFormat.RPM)
        val repackRoot = jPackageConfig.temporaryDirPath.resolve("rpm-repack")
        repackRoot.toFile().deleteRecursively()

        val payloadRoot = repackRoot.resolve("payload")
        val topDir = repackRoot.resolve("rpmbuild")
        val specDir = topDir.resolve("SPECS")
        val buildRoot = repackRoot.resolve("buildroot")
        Files.createDirectories(payloadRoot)
        Files.createDirectories(specDir)

        runProcess(
                ProcessBuilder(
                        "sh",
                        "-c",
                        "cd ${shellSingleQuote(payloadRoot.toAbsolutePath().toString())} && " +
                                "rpm2cpio ${shellSingleQuote(rpmPath.toAbsolutePath().toString())} | cpio -idm --quiet"
                ),
                "extract RPM payload"
        )

        val specPath = specDir.resolve("bisq.spec")
        Files.writeString(specPath, createNormalizedRpmSpec(appConfig, payloadRoot))

        runProcess(
                ProcessBuilder(
                        "rpmbuild",
                        "-bb",
                        "--buildroot", buildRoot.toAbsolutePath().toString(),
                        "--define", "_topdir ${topDir.toAbsolutePath()}",
                        "--define", "_buildhost bisq-release-builder",
                        "--define", "use_source_date_epoch_as_buildtime 1",
                        "--define", "clamp_mtime_to_source_date_epoch 1",
                        "--define", "source_date_epoch_from_changelog 0",
                        "--define", "_binary_payload w9.gzdio",
                        "--define", "_build_id_links none",
                        "--define", "__os_install_post %{nil}",
                        specPath.toAbsolutePath().toString()
                ),
                "rebuild normalized RPM"
        )

        val rebuiltRpmPath = topDir
                .resolve("RPMS")
                .resolve("x86_64")
                .resolve(rpmPath.fileName)
        if (!Files.isRegularFile(rebuiltRpmPath)) {
            throw GradleException("rpmbuild did not create expected RPM: ${rebuiltRpmPath.toAbsolutePath()}")
        }

        Files.move(rebuiltRpmPath, rpmPath, StandardCopyOption.REPLACE_EXISTING)
        normalizePathTimestamps(rpmPath)
    }

    private fun normalizeDmgPackage() {
        val dmgPath = findSinglePackageArtifact(PackageFormat.DMG)
        val normalizeRoot = jPackageConfig.temporaryDirPath.resolve("dmg-normalize")
        normalizeRoot.toFile().deleteRecursively()

        val mountPoint = normalizeRoot.resolve("mount")
        val stagingRoot = normalizeRoot.resolve("staging")
        val rawImagePath = normalizeRoot.resolve("Bisq-normalized-raw.dmg")
        val compressedImagePath = normalizeRoot.resolve("Bisq-normalized.dmg")
        Files.createDirectories(mountPoint)
        Files.createDirectories(stagingRoot)

        var attached = false
        try {
            runProcess(
                    ProcessBuilder(
                            "hdiutil",
                            "attach",
                            "-readonly",
                            "-nobrowse",
                            "-mountpoint", mountPoint.toAbsolutePath().toString(),
                            dmgPath.toAbsolutePath().toString()
                    ),
                    "attach DMG",
                    rpmHome = false
            )
            attached = true

            copyDmgContents(mountPoint, stagingRoot)
        } finally {
            if (attached) {
                detachDmg(mountPoint)
            }
        }

        normalizeDmgSourceTree(stagingRoot)

        runProcess(
                ProcessBuilder(
                        "hdiutil",
                        "makehybrid",
                        "-hfs",
                        "-hfs-volume-name", "Bisq",
                        "-ov",
                        "-o", rawImagePath.toAbsolutePath().toString(),
                        stagingRoot.toAbsolutePath().toString()
                ),
                "create normalized HFS DMG",
                rpmHome = false
        )
        normalizeRawHfsImage(rawImagePath)

        runProcess(
                ProcessBuilder(
                        "hdiutil",
                        "convert",
                        "-format", "UDZO",
                        "-imagekey", "zlib-level=9",
                        "-o", compressedImagePath.toAbsolutePath().toString(),
                        rawImagePath.toAbsolutePath().toString()
                ),
                "compress normalized DMG",
                rpmHome = false
        )
        normalizeUdifTrailer(compressedImagePath)

        Files.move(compressedImagePath, dmgPath, StandardCopyOption.REPLACE_EXISTING)
        normalizePathTimestamps(dmgPath)
    }

    private fun createNormalizedRpmSpec(appConfig: JPackageAppConfig, payloadRoot: Path): String {
        val sourceDateEpoch = sourceDateEpochSeconds()
        val payloadRootPath = shellSingleQuote(payloadRoot.toAbsolutePath().toString())

        return """
            Name: bisq
            Version: ${appConfig.appVersion}
            Release: 1
            Summary: Bisq
            License: AGPLv3
            Group: Unspecified
            Vendor: Bisq
            Prefix: /opt
            BuildArch: x86_64
            AutoReqProv: no
            Requires: xdg-utils
            Provides: bisq

            %description
            A decentralized bitcoin exchange network.

            %prep

            %build

            %install
            rm -rf "%{buildroot}"
            mkdir -p "%{buildroot}"
            cp -a ${payloadRootPath}/. "%{buildroot}/"
            find "%{buildroot}" -exec touch -h -d @${sourceDateEpoch} {} +

            %pre
            package_type=rpm

            if [ "${'$'}1" = 2 ]; then
              true;
            fi

            %post
            package_type=rpm

            xdg-desktop-menu install /opt/bisq/lib/bisq-Bisq.desktop

            %preun
            package_type=rpm

            xdg-desktop-menu uninstall /opt/bisq/lib/bisq-Bisq.desktop

            %files
            %defattr(-,root,root,-)
            /opt
        """.trimIndent() + "\n"
    }

    private fun findSinglePackageArtifact(packageFormat: PackageFormat): Path {
        val extension = ".${packageFormat.fileExtension}"
        val matchingArtifacts = Files.list(jPackageConfig.outputDirPath).use { files ->
            files.filter { path ->
                Files.isRegularFile(path) && path.fileName.toString().endsWith(extension)
            }.toList()
        }

        if (matchingArtifacts.size != 1) {
            throw GradleException(
                    "Expected exactly one ${extension} package in ${jPackageConfig.outputDirPath}, " +
                            "found ${matchingArtifacts.size}: ${matchingArtifacts.joinToString(", ")}"
            )
        }

        return matchingArtifacts.single()
    }

    private fun runProcess(processBuilder: ProcessBuilder, description: String, rpmHome: Boolean = true) {
        processBuilder.inheritIO()
        processBuilder.environment().putIfAbsent(SOURCE_DATE_EPOCH, DEFAULT_SOURCE_DATE_EPOCH)
        processBuilder.environment().putIfAbsent("TZ", "UTC")
        if (rpmHome) {
            processBuilder.environment()["HOME"] = createRpmHome().toAbsolutePath().toString()
        }

        val allCommands = processBuilder.command()
        val process = processBuilder.start()
        val finished = process.waitFor(15, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            throw GradleException("${description} timed out after 15 minutes: ${allCommands.joinToString(" ")}")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            throw GradleException("${description} failed with exit code ${exitCode}: ${allCommands.joinToString(" ")}")
        }
    }

    private fun normalizePathTimestamps(path: Path) {
        val timestamp = FileTime.from(Instant.ofEpochSecond(sourceDateEpochSeconds()))
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { entry ->
                Files.setLastModifiedTime(entry, timestamp)
            }
        }
    }

    private fun detachDmg(mountPoint: Path) {
        try {
            runProcess(
                    ProcessBuilder("hdiutil", "detach", mountPoint.toAbsolutePath().toString()),
                    "detach DMG",
                    rpmHome = false
            )
        } catch (exception: GradleException) {
            runProcess(
                    ProcessBuilder("hdiutil", "detach", "-force", mountPoint.toAbsolutePath().toString()),
                    "force detach DMG",
                    rpmHome = false
            )
        }
    }

    private fun copyDmgContents(sourceRoot: Path, targetRoot: Path) {
        Files.walk(sourceRoot).use { paths ->
            paths.sorted(Comparator.comparing { path -> sourceRoot.relativize(path).toString() })
                    .forEach { source ->
                        val relativePath = sourceRoot.relativize(source)
                        if (relativePath.toString().isEmpty()) {
                            return@forEach
                        }
                        if (relativePath.getName(0).toString() == ".fseventsd") {
                            return@forEach
                        }

                        val target = targetRoot.resolve(relativePath.toString())
                        when {
                            Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) -> {
                                Files.createDirectories(target)
                            }

                            Files.isSymbolicLink(source) -> {
                                Files.createDirectories(target.parent)
                                Files.copy(source, target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING)
                            }

                            Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) -> {
                                Files.createDirectories(target.parent)
                                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                            }

                            else -> return@forEach
                        }

                        copyPosixPermissions(source, target)
                    }
        }
    }

    private fun copyPosixPermissions(source: Path, target: Path) {
        try {
            val permissions = Files.getPosixFilePermissions(source, LinkOption.NOFOLLOW_LINKS)
            Files.setPosixFilePermissions(target, permissions)
        } catch (ignored: UnsupportedOperationException) {
        }
    }

    private fun normalizeDmgSourceTree(sourceRoot: Path) {
        val timestamp = FileTime.from(Instant.ofEpochSecond(sourceDateEpochSeconds()))
        Files.walk(sourceRoot).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { path ->
                val attributeView = Files.getFileAttributeView(
                        path,
                        BasicFileAttributeView::class.java,
                        LinkOption.NOFOLLOW_LINKS
                )
                attributeView?.setTimes(timestamp, timestamp, timestamp)
            }
        }

        val setFilePath = Path.of("/usr/bin/SetFile")
        if (Files.isExecutable(setFilePath)) {
            val dateText = sourceDateEpochForSetFile()
            runProcess(
                    ProcessBuilder(
                            "find",
                            sourceRoot.toAbsolutePath().toString(),
                            "-exec",
                            setFilePath.toString(),
                            "-d", dateText,
                            "-m", dateText,
                            "{}", "+"
                    ),
                    "normalize macOS file dates",
                    rpmHome = false
            )
        }
    }

    private fun normalizeRawHfsImage(imagePath: Path) {
        val fixedDateFields = repeatBytes(hfsTimestampBytes(), 4)
        val fixedCatalogDateFields = repeatBytes(hfsTimestampBytes(), 5)
        val headerOffsets = findHfsVolumeHeaderOffsets(imagePath)
        headerOffsets.forEach { headerOffset ->
            patchFileBytes(imagePath, headerOffset + 16, fixedDateFields)
            patchFileBytes(imagePath, headerOffset + 104, FIXED_HFS_VOLUME_UUID)
        }

        normalizeHfsCatalogDateFields(imagePath, headerOffsets.minOrNull() ?: error("No HFS+ volume header found"), fixedCatalogDateFields)
    }

    private fun findHfsVolumeHeaderOffsets(imagePath: Path): Set<Long> {
        FileChannel.open(imagePath, StandardOpenOption.READ).use { channel ->
            val fileSize = channel.size()
            val offsets = linkedSetOf<Long>()
            offsets.addAll(findHfsVolumeHeaderOffsetsInRegion(channel, 0, minOf(HFS_VOLUME_HEADER_SCAN_BYTES.toLong(), fileSize)))
            if (fileSize > HFS_VOLUME_HEADER_SCAN_BYTES) {
                val tailStart = maxOf(0, fileSize - HFS_VOLUME_HEADER_SCAN_BYTES)
                offsets.addAll(findHfsVolumeHeaderOffsetsInRegion(channel, tailStart, fileSize - tailStart))
            }
            if (offsets.isEmpty()) {
                throw GradleException("Could not find HFS+ volume headers in normalized DMG: ${imagePath.toAbsolutePath()}")
            }
            return offsets
        }
    }

    private fun findHfsVolumeHeaderOffsetsInRegion(channel: FileChannel, regionStart: Long, regionSize: Long): List<Long> {
        if (regionSize < 4) {
            return emptyList()
        }

        val buffer = ByteArray(regionSize.toInt())
        readChannelFully(channel, ByteBuffer.wrap(buffer), regionStart)
        val offsets = mutableListOf<Long>()
        for (index in 0..buffer.size - 4) {
            if (readUnsignedShort(buffer, index) == HFS_SIGNATURE &&
                    readUnsignedShort(buffer, index + 2) == HFS_VERSION) {
                val headerOffset = regionStart + index
                if (isValidHfsVolumeHeaderCandidate(channel, headerOffset)) {
                    offsets += headerOffset
                }
            }
        }
        return offsets
    }

    private fun isValidHfsVolumeHeaderCandidate(channel: FileChannel, headerOffset: Long): Boolean {
        return try {
            val fileSize = channel.size()
            if (headerOffset < 0 || headerOffset + HFS_VOLUME_HEADER_SIZE > fileSize) {
                return false
            }

            val volumeHeader = readChannelBytes(channel, headerOffset, HFS_VOLUME_HEADER_SIZE)
            if (readUnsignedShort(volumeHeader, 0) != HFS_SIGNATURE ||
                    readUnsignedShort(volumeHeader, 2) != HFS_VERSION) {
                return false
            }

            val blockSize = readUnsignedInt(volumeHeader, HFS_BLOCK_SIZE_OFFSET)
            val totalBlocks = readUnsignedInt(volumeHeader, HFS_TOTAL_BLOCKS_OFFSET)
            val volumeBytes = hfsVolumeByteSize(blockSize, totalBlocks) ?: return false
            possibleHfsVolumeStarts(headerOffset, volumeBytes, fileSize).any { volumeStart ->
                hasSaneHfsCatalogFork(volumeHeader, volumeStart, blockSize, totalBlocks, fileSize)
            }
        } catch (ignored: Exception) {
            false
        }
    }

    private fun hfsVolumeByteSize(blockSize: Long, totalBlocks: Long): Long? {
        if (!isSaneHfsBlockSize(blockSize) || totalBlocks <= 0 || blockSize > Long.MAX_VALUE / totalBlocks) {
            return null
        }
        return blockSize * totalBlocks
    }

    private fun isSaneHfsBlockSize(blockSize: Long): Boolean {
        return blockSize >= 512 &&
                blockSize <= 1024 * 1024 &&
                blockSize and (blockSize - 1) == 0L
    }

    private fun possibleHfsVolumeStarts(headerOffset: Long, volumeBytes: Long, fileSize: Long): List<Long> {
        return listOf(
                headerOffset - HFS_VOLUME_HEADER_OFFSET_FROM_VOLUME_START,
                headerOffset + HFS_VOLUME_HEADER_OFFSET_FROM_VOLUME_START - volumeBytes
        ).distinct().filter { volumeStart ->
            volumeStart >= 0 &&
                    volumeBytes <= fileSize - volumeStart &&
                    (headerOffset == volumeStart + HFS_VOLUME_HEADER_OFFSET_FROM_VOLUME_START ||
                            headerOffset == volumeStart + volumeBytes - HFS_VOLUME_HEADER_OFFSET_FROM_VOLUME_START)
        }
    }

    private fun hasSaneHfsCatalogFork(
            volumeHeader: ByteArray,
            volumeStart: Long,
            blockSize: Long,
            totalBlocks: Long,
            fileSize: Long
    ): Boolean {
        val logicalSize = readLong(volumeHeader, HFS_CATALOG_FILE_OFFSET.toInt())
        if (logicalSize <= 0) {
            return false
        }

        val extents = hfsCatalogExtents(volumeHeader)
        if (extents.isEmpty()) {
            return false
        }

        val volumeBytes = blockSize * totalBlocks
        val mappedBytes = extents.fold(0L) { total, extent ->
            if (extent.startBlock >= totalBlocks ||
                    extent.blockCount > totalBlocks - extent.startBlock ||
                    extent.blockCount > Long.MAX_VALUE / blockSize) {
                return false
            }

            val extentBytes = extent.blockCount * blockSize
            val extentOffset = volumeStart + (extent.startBlock * blockSize)
            if (extentOffset < volumeStart ||
                    extentBytes > fileSize - extentOffset ||
                    total > Long.MAX_VALUE - extentBytes) {
                return false
            }
            total + extentBytes
        }

        return mappedBytes > 0 && logicalSize <= volumeBytes
    }

    private fun normalizeHfsCatalogDateFields(imagePath: Path, volumeHeaderOffset: Long, fixedCatalogDateFields: ByteArray) {
        FileChannel.open(imagePath, StandardOpenOption.READ, StandardOpenOption.WRITE).use { channel ->
            val catalogFile = readHfsCatalogFile(channel, volumeHeaderOffset)
            val headerNode = readHfsForkBytes(channel, catalogFile, 0, 64)
            if (headerNode[8] != HFS_BTREE_HEADER_NODE_KIND) {
                throw GradleException("HFS+ catalog file does not start with a B-tree header node: ${imagePath.toAbsolutePath()}")
            }

            val nodeSize = readUnsignedShort(headerNode, HFS_BTREE_NODE_DESCRIPTOR_SIZE + 18)
            val maxKeyLength = readUnsignedShort(headerNode, HFS_BTREE_NODE_DESCRIPTOR_SIZE + 20)
            val totalNodes = readUnsignedInt(headerNode, HFS_BTREE_NODE_DESCRIPTOR_SIZE + 22)
            if (nodeSize <= HFS_BTREE_NODE_DESCRIPTOR_SIZE || nodeSize > catalogFile.logicalSize) {
                throw GradleException("Unexpected HFS+ catalog B-tree node size $nodeSize in ${imagePath.toAbsolutePath()}")
            }

            var patchedRecords = 0
            var nodeNumber = 0L
            while (nodeNumber < totalNodes) {
                val nodeLogicalOffset = nodeNumber * nodeSize
                if (nodeLogicalOffset + nodeSize > catalogFile.logicalSize) {
                    break
                }

                val node = readHfsForkBytes(channel, catalogFile, nodeLogicalOffset, nodeSize)
                if (node[8] == HFS_BTREE_LEAF_NODE_KIND) {
                    patchedRecords += normalizeHfsCatalogLeafNodeDates(
                            channel,
                            catalogFile,
                            node,
                            nodeLogicalOffset,
                            nodeSize,
                            maxKeyLength,
                            fixedCatalogDateFields
                    )
                }
                normalizeHfsBTreeNodeFreeSpace(channel, catalogFile, node, nodeLogicalOffset, nodeSize)
                nodeNumber++
            }

            if (patchedRecords == 0) {
                throw GradleException("Could not find HFS+ catalog file or folder records in ${imagePath.toAbsolutePath()}")
            }
        }
    }

    private fun normalizeHfsCatalogLeafNodeDates(
            channel: FileChannel,
            catalogFile: HfsCatalogFile,
            node: ByteArray,
            nodeLogicalOffset: Long,
            nodeSize: Int,
            maxKeyLength: Int,
            fixedCatalogDateFields: ByteArray
    ): Int {
        val recordCount = readUnsignedShort(node, 10)
        var patchedRecords = 0
        repeat(recordCount) { recordIndex ->
            val recordOffsetTableIndex = nodeSize - ((recordIndex + 1) * 2)
            if (recordOffsetTableIndex < HFS_BTREE_NODE_DESCRIPTOR_SIZE) {
                return@repeat
            }

            val keyOffset = readUnsignedShort(node, recordOffsetTableIndex)
            if (keyOffset < HFS_BTREE_NODE_DESCRIPTOR_SIZE || keyOffset + 2 > nodeSize) {
                return@repeat
            }

            val keyLength = readUnsignedShort(node, keyOffset)
            if (keyLength < 6 || keyLength > maxKeyLength) {
                return@repeat
            }

            val recordOffset = keyOffset + 2 + keyLength
            if (recordOffset + HFS_CATALOG_RECORD_DATES_OFFSET + fixedCatalogDateFields.size > nodeSize) {
                return@repeat
            }

            val recordType = readUnsignedShort(node, recordOffset)
            if (recordType == HFS_CATALOG_FOLDER_RECORD || recordType == HFS_CATALOG_FILE_RECORD) {
                writeHfsForkBytes(
                        channel,
                        catalogFile,
                        nodeLogicalOffset + recordOffset + HFS_CATALOG_RECORD_DATES_OFFSET,
                        fixedCatalogDateFields
                )
                patchedRecords++
            }
        }
        return patchedRecords
    }

    private fun normalizeHfsBTreeNodeFreeSpace(
            channel: FileChannel,
            catalogFile: HfsCatalogFile,
            node: ByteArray,
            nodeLogicalOffset: Long,
            nodeSize: Int
    ) {
        val recordCount = readUnsignedShort(node, 10)
        val offsetTableStart = nodeSize - ((recordCount + 1) * 2)
        if (offsetTableStart < HFS_BTREE_NODE_DESCRIPTOR_SIZE) {
            return
        }

        val freeSpaceStart = readUnsignedShort(node, offsetTableStart)
        if (freeSpaceStart < HFS_BTREE_NODE_DESCRIPTOR_SIZE || freeSpaceStart > offsetTableStart) {
            return
        }

        val freeSpaceLength = offsetTableStart - freeSpaceStart
        if (freeSpaceLength > 0) {
            writeHfsForkBytes(channel, catalogFile, nodeLogicalOffset + freeSpaceStart, ByteArray(freeSpaceLength))
        }
    }

    private fun readHfsCatalogFile(channel: FileChannel, volumeHeaderOffset: Long): HfsCatalogFile {
        val volumeHeader = readChannelBytes(channel, volumeHeaderOffset, HFS_VOLUME_HEADER_SIZE)
        val blockSize = readUnsignedInt(volumeHeader, HFS_BLOCK_SIZE_OFFSET)
        if (!isSaneHfsBlockSize(blockSize)) {
            throw GradleException("Invalid HFS+ block size $blockSize")
        }

        val catalogFileOffset = HFS_CATALOG_FILE_OFFSET.toInt()
        val logicalSize = readLong(volumeHeader, catalogFileOffset)
        val extents = hfsCatalogExtents(volumeHeader)

        if (logicalSize <= 0 || extents.isEmpty()) {
            throw GradleException("Invalid HFS+ catalog fork metadata")
        }

        return HfsCatalogFile(
                volumeStart = volumeHeaderOffset - HFS_VOLUME_HEADER_OFFSET_FROM_VOLUME_START,
                blockSize = blockSize,
                logicalSize = logicalSize,
                extents = extents
        )
    }

    private fun hfsCatalogExtents(volumeHeader: ByteArray): List<HfsExtent> {
        val extentsOffset = HFS_CATALOG_FILE_OFFSET.toInt() + HFS_FORK_EXTENTS_OFFSET
        return (0 until HFS_EXTENT_COUNT)
                .map { extentIndex ->
                    val extentOffset = extentsOffset + (extentIndex * 8)
                    HfsExtent(
                            startBlock = readUnsignedInt(volumeHeader, extentOffset),
                            blockCount = readUnsignedInt(volumeHeader, extentOffset + 4)
                    )
                }
                .filter { extent -> extent.blockCount > 0 }
    }

    private fun readHfsForkBytes(channel: FileChannel, fork: HfsCatalogFile, logicalOffset: Long, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var destinationOffset = 0
        var currentLogicalOffset = logicalOffset
        while (destinationOffset < length) {
            val (physicalOffset, availableBytes) = hfsForkPhysicalRange(fork, currentLogicalOffset)
                    ?: throw GradleException("HFS+ catalog logical offset $currentLogicalOffset is not mapped by the first extent record")
            val bytesToRead = minOf((length - destinationOffset).toLong(), availableBytes).toInt()
            val buffer = ByteBuffer.wrap(bytes, destinationOffset, bytesToRead)
            readChannelFully(channel, buffer, physicalOffset)
            destinationOffset += bytesToRead
            currentLogicalOffset += bytesToRead
        }
        return bytes
    }

    private fun writeHfsForkBytes(channel: FileChannel, fork: HfsCatalogFile, logicalOffset: Long, bytes: ByteArray) {
        var sourceOffset = 0
        var currentLogicalOffset = logicalOffset
        while (sourceOffset < bytes.size) {
            val (physicalOffset, availableBytes) = hfsForkPhysicalRange(fork, currentLogicalOffset)
                    ?: throw GradleException("HFS+ catalog logical offset $currentLogicalOffset is not mapped by the first extent record")
            val bytesToWrite = minOf((bytes.size - sourceOffset).toLong(), availableBytes).toInt()
            val buffer = ByteBuffer.wrap(bytes, sourceOffset, bytesToWrite)
            writeChannelFully(channel, buffer, physicalOffset)
            sourceOffset += bytesToWrite
            currentLogicalOffset += bytesToWrite
        }
    }

    private fun hfsForkPhysicalRange(fork: HfsCatalogFile, logicalOffset: Long): Pair<Long, Long>? {
        var extentLogicalOffset = 0L
        fork.extents.forEach { extent ->
            val extentBytes = extent.blockCount * fork.blockSize
            if (logicalOffset >= extentLogicalOffset && logicalOffset < extentLogicalOffset + extentBytes) {
                val offsetWithinExtent = logicalOffset - extentLogicalOffset
                return Pair(
                        fork.volumeStart + (extent.startBlock * fork.blockSize) + offsetWithinExtent,
                        extentBytes - offsetWithinExtent
                )
            }
            extentLogicalOffset += extentBytes
        }
        return null
    }

    private fun normalizeUdifTrailer(imagePath: Path) {
        FileChannel.open(imagePath, StandardOpenOption.READ).use { channel ->
            val fileSize = channel.size()
            if (fileSize < 512) {
                throw GradleException("DMG is too small to contain a UDIF trailer: ${imagePath.toAbsolutePath()}")
            }
            val trailerOffset = fileSize - 512
            val trailer = ByteArray(512)
            readChannelFully(channel, ByteBuffer.wrap(trailer), trailerOffset)
            val signature = byteArrayOf('k'.code.toByte(), 'o'.code.toByte(), 'l'.code.toByte(), 'y'.code.toByte())
            if (!trailer.copyOfRange(0, 4).contentEquals(signature)) {
                throw GradleException("DMG does not contain the expected UDIF trailer: ${imagePath.toAbsolutePath()}")
            }
            patchFileBytes(imagePath, trailerOffset + 64, FIXED_UDIF_UUID)
        }
    }

    private fun patchFileBytes(path: Path, offset: Long, bytes: ByteArray) {
        FileChannel.open(path, StandardOpenOption.WRITE).use { channel ->
            writeChannelFully(channel, ByteBuffer.wrap(bytes), offset)
        }
    }

    private fun hfsTimestampBytes(): ByteArray {
        val hfsTimestamp = sourceDateEpochSeconds() + HFS_EPOCH_OFFSET_SECONDS
        return byteArrayOf(
                ((hfsTimestamp ushr 24) and 0xff).toByte(),
                ((hfsTimestamp ushr 16) and 0xff).toByte(),
                ((hfsTimestamp ushr 8) and 0xff).toByte(),
                (hfsTimestamp and 0xff).toByte()
        )
    }

    private fun repeatBytes(bytes: ByteArray, count: Int): ByteArray {
        val repeated = ByteArray(bytes.size * count)
        repeat(count) { index ->
            bytes.copyInto(repeated, destinationOffset = index * bytes.size)
        }
        return repeated
    }

    private fun readChannelBytes(channel: FileChannel, offset: Long, length: Int): ByteArray {
        val bytes = ByteArray(length)
        readChannelFully(channel, ByteBuffer.wrap(bytes), offset)
        return bytes
    }

    private fun readChannelFully(channel: FileChannel, buffer: ByteBuffer, position: Long) {
        var currentPosition = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, currentPosition)
            if (read < 0) {
                throw GradleException("Unexpected end of file while reading binary image data")
            }
            if (read == 0) {
                throw GradleException("Could not make progress while reading binary image data at offset $currentPosition")
            }
            currentPosition += read
        }
    }

    private fun writeChannelFully(channel: FileChannel, buffer: ByteBuffer, position: Long) {
        var currentPosition = position
        while (buffer.hasRemaining()) {
            val written = channel.write(buffer, currentPosition)
            if (written == 0) {
                throw GradleException("Could not make progress while writing binary image data at offset $currentPosition")
            }
            currentPosition += written
        }
    }

    private fun readUnsignedShort(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xff) shl 8) or
                (bytes[offset + 1].toInt() and 0xff)
    }

    private fun readUnsignedInt(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xff) shl 24) or
                ((bytes[offset + 1].toLong() and 0xff) shl 16) or
                ((bytes[offset + 2].toLong() and 0xff) shl 8) or
                (bytes[offset + 3].toLong() and 0xff)
    }

    private fun readLong(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xff) shl 56) or
                ((bytes[offset + 1].toLong() and 0xff) shl 48) or
                ((bytes[offset + 2].toLong() and 0xff) shl 40) or
                ((bytes[offset + 3].toLong() and 0xff) shl 32) or
                ((bytes[offset + 4].toLong() and 0xff) shl 24) or
                ((bytes[offset + 5].toLong() and 0xff) shl 16) or
                ((bytes[offset + 6].toLong() and 0xff) shl 8) or
                (bytes[offset + 7].toLong() and 0xff)
    }

    private fun sourceDateEpochSeconds(): Long {
        val sourceDateEpoch = System.getenv(SOURCE_DATE_EPOCH) ?: DEFAULT_SOURCE_DATE_EPOCH
        return sourceDateEpoch.toLongOrNull() ?: DEFAULT_SOURCE_DATE_EPOCH.toLong()
    }

    private fun sourceDateEpochForSetFile(): String {
        val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss", Locale.US)
        return Instant.ofEpochSecond(sourceDateEpochSeconds())
                .atZone(ZoneOffset.UTC)
                .format(formatter)
    }

    private fun shellSingleQuote(value: String): String {
        return "'${value.replace("'", "'\"'\"'")}'"
    }
}

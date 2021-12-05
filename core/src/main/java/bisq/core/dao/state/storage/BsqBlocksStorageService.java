/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.state.storage;

import bisq.core.dao.state.GenesisTxInfo;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.common.config.Config;
import bisq.common.file.FileUtil;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import protobuf.BaseBlock;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.io.FileUtils;

import java.net.URL;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BsqBlocksStorageService {
    public final static String NAME = "BsqBlocks";

    private final int genesisBlockHeight;
    private final File storageDir;
    private final BlocksPersistence blocksPersistence;
    @Getter
    private int chainHeightOfPersistedBlocks;

    @Inject
    public BsqBlocksStorageService(GenesisTxInfo genesisTxInfo,
                                   PersistenceProtoResolver persistenceProtoResolver,
                                   @Named(Config.STORAGE_DIR) File dbStorageDir) {
        genesisBlockHeight = genesisTxInfo.getGenesisBlockHeight();
        storageDir = new File(dbStorageDir.getAbsolutePath() + File.separator + NAME);
        blocksPersistence = new BlocksPersistence(storageDir, NAME, persistenceProtoResolver);
    }

    public void persistBlocks(List<Block> blocks) {
        long ts = System.currentTimeMillis();
        List<BaseBlock> protobufBlocks = blocks.stream()
                .map(Block::toProtoMessage)
                .collect(Collectors.toList());
        blocksPersistence.writeBlocks(protobufBlocks);

        if (!blocks.isEmpty()) {
            chainHeightOfPersistedBlocks = Math.max(chainHeightOfPersistedBlocks,
                    getHeightOfLastFullBucket(blocks));
        }
        log.info("Persist (serialize+write) {} blocks took {} ms",
                blocks.size(),
                System.currentTimeMillis() - ts);
    }

    public LinkedList<Block> readBlocks(int chainHeight) {
        long ts = System.currentTimeMillis();
        LinkedList<Block> blocks = new LinkedList<>();
        List<BaseBlock> list = blocksPersistence.readBlocks(genesisBlockHeight, chainHeight);
        list.stream().map(Block::fromProto)
                .forEach(blocks::add);
        log.info("Reading and deserializing {} blocks took {} ms", blocks.size(), System.currentTimeMillis() - ts);
        if (!blocks.isEmpty()) {
            chainHeightOfPersistedBlocks = getHeightOfLastFullBucket(blocks);
        }
        return blocks;
    }

    public LinkedList<Block> migrateBlocks(List<protobuf.BaseBlock> protobufBlocks) {
        long ts = System.currentTimeMillis();
        blocksPersistence.writeBlocks(protobufBlocks);
        LinkedList<Block> blocks = new LinkedList<>();
        protobufBlocks.forEach(protobufBlock -> blocks.add(Block.fromProto(protobufBlock)));
        if (!blocks.isEmpty()) {
            chainHeightOfPersistedBlocks = getHeightOfLastFullBucket(blocks);
        }

        log.info("Migrating blocks (write+deserialization) from DaoStateStore took {} ms", System.currentTimeMillis() - ts);
        return blocks;
    }


    void copyFromResources(String postFix) {
        long ts = System.currentTimeMillis();
        try {
            String dirName = BsqBlocksStorageService.NAME;
            String resourceDir = dirName + postFix;

            if (storageDir.exists()) {
                log.info("No resource directory was copied. {} exists already.", dirName);
                return;
            }

            URL dirUrl = getClass().getClassLoader().getResource(resourceDir);
            if (dirUrl == null) {
                log.info("Directory {} in resources does not exist.", resourceDir);
                return;
            }
            FileSystem fileSystem = FileSystems.newFileSystem(dirUrl.toURI(), Collections.emptyMap());
            List<Path> filePaths = Files.walk(fileSystem.getPath(resourceDir), 1).collect(Collectors.toList());
            if (filePaths.size() <= 1) {
                log.info("No files in directory. {}", dirUrl.toString());
                return;
            }
            if (!storageDir.exists()) {
                storageDir.mkdir();
            }
            filePaths.remove(0);        // first item is the directory name
            for (Path filePath : filePaths) {
                File destinationFile = new File(storageDir, filePath.getFileName().toString());
                FileUtil.resourceToFile(filePath.toString().substring(1), destinationFile);
            }
            log.info("Copying {} resource files took {} ms", filePaths.size(), System.currentTimeMillis() - ts);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private int getHeightOfLastFullBucket(List<Block> blocks) {
        int bucketIndex = blocks.get(blocks.size() - 1).getHeight() / BlocksPersistence.BUCKET_SIZE;
        return bucketIndex * BlocksPersistence.BUCKET_SIZE;
    }

    public void removeBlocksDirectory() {
        blocksPersistence.removeBlocksDirectory();
    }

    // We recreate the directory so that we don't fill the blocks after restart from resources
    // In copyFromResources we only check for the directory not the files inside.
    public void removeBlocksInDirectory() {
        blocksPersistence.removeBlocksDirectory();
        if (!storageDir.exists()) {
            storageDir.mkdir();
        }
    }
}

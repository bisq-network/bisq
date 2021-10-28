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
import bisq.common.proto.persistable.PersistenceProtoResolver;

import protobuf.BaseBlock;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.io.FileUtils;

import java.net.URL;

import java.io.File;

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
        log.error("Persist (serialize+write) {} blocks took {} ms",
                blocks.size(),
                System.currentTimeMillis() - ts);
    }

    public LinkedList<Block> readBlocks(int chainHeight) {
        long ts = System.currentTimeMillis();
        LinkedList<Block> blocks = new LinkedList<>();
        blocksPersistence.readBlocks(genesisBlockHeight, chainHeight).stream()
                .map(Block::fromProto)
                .forEach(blocks::add);
        log.error("Reading {} blocks took {} ms", blocks.size(), System.currentTimeMillis() - ts);
        if (!blocks.isEmpty()) {
            chainHeightOfPersistedBlocks = getHeightOfLastFullBucket(blocks);
        }
        return blocks;
    }

    public LinkedList<Block> swapBlocks(List<protobuf.BaseBlock> protobufBlocks) {
        long ts = System.currentTimeMillis();
        log.error("We have {} blocks in the daoStateAsProto", protobufBlocks.size());


        blocksPersistence.writeBlocks(protobufBlocks);
        LinkedList<Block> blocks = new LinkedList<>();
        protobufBlocks.forEach(protobufBlock -> blocks.add(Block.fromProto(protobufBlock)));
        if (!blocks.isEmpty()) {
            chainHeightOfPersistedBlocks = getHeightOfLastFullBucket(blocks);
        }

        log.error("Mapping blocks (write+deserialization) from DaoStateStore took {} ms", System.currentTimeMillis() - ts);
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
            File dir = new File(dirUrl.toURI());
            String[] fileNames = dir.list();
            if (fileNames == null) {
                log.info("No files in directory. {}", dir.getAbsolutePath());
                return;
            }
            if (!storageDir.exists()) {
                storageDir.mkdir();
            }
            for (String fileName : fileNames) {
                URL url = getClass().getClassLoader().getResource(resourceDir + File.separator + fileName);
                File resourceFile = new File(url.toURI());
                File destinationFile = new File(storageDir, fileName);
                FileUtils.copyFile(resourceFile, destinationFile);
            }
            log.error("Copying {} resource files took {} ms", fileNames.length, System.currentTimeMillis() - ts);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // todo
    private int getHeightOfLastFullBucket(List<Block> blocks) {
        int i = blocks.get(blocks.size() - 1).getHeight() / BlocksPersistence.BUCKET_SIZE;
        int i1 = i * BlocksPersistence.BUCKET_SIZE;
        //  log.error("getHeightOfLastFullBucket {}", i * BlocksPersistence.BUCKET_SIZE);
        return i1;
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
      /*  List<protobuf.BaseBlock> blocks = new ArrayList<>();
        // height, long time, String hash, String previousBlockHash
        for (int i = genesisBlockHeight; i <= chainHeightOfPersistedBlocks; i++) {
            blocks.add(new Block(i, 0, "", "").toProtoMessage());
        }
        blocksPersistence.addAll(blocks);*/
    }
}

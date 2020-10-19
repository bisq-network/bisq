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

package bisq.core.dao.node.explorer;

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.util.FormattingUtils;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.file.JsonFileManager;
import bisq.common.util.Utilities;

import com.google.inject.Inject;

import javax.inject.Named;

import java.nio.file.Paths;

import java.io.File;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExportJsonFilesService implements DaoSetupService {
    private final DaoStateService daoStateService;
    private final File storageDir;
    private boolean dumpBlockchainData;
    private JsonFileManager blockFileManager, txFileManager, txOutputFileManager,
            spentInfoMapFileManager, unspentTxOutputMapFileManager, issuanceMapFileManager,
            confiscatedLockupTxListFileManager;
    private File blockDir;

    @Inject
    public ExportJsonFilesService(DaoStateService daoStateService,
                                  @Named(Config.STORAGE_DIR) File storageDir,
                                  @Named(Config.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        this.daoStateService = daoStateService;
        this.storageDir = storageDir;
        this.dumpBlockchainData = dumpBlockchainData;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
    }

    @Override
    public void start() {
        if (!dumpBlockchainData) {
            return;
        }

        File jsonDir = new File(Paths.get(storageDir.getAbsolutePath(), "json").toString());
        File immutableDataDir = new File(Paths.get(jsonDir.getAbsolutePath(), "immutableData").toString());
        File mutableDataDir = new File(Paths.get(jsonDir.getAbsolutePath(), "mutableData").toString());
        blockDir = new File(Paths.get(immutableDataDir.getAbsolutePath(), "block").toString());
        File txDir = new File(Paths.get(immutableDataDir.getAbsolutePath(), "tx").toString());
        File txOutputDir = new File(Paths.get(immutableDataDir.getAbsolutePath(), "txo").toString());

        if (!jsonDir.mkdir())
            log.warn("Make {} directory failed", jsonDir.getAbsolutePath());

        if (!immutableDataDir.mkdir())
            log.warn("Make {} directory failed", immutableDataDir.getAbsolutePath());

        if (!mutableDataDir.mkdir())
            log.warn("Make {} directory failed", mutableDataDir.getAbsolutePath());

        if (!blockDir.mkdir())
            log.warn("Make {} directory failed", blockDir.getAbsolutePath());

        if (!txDir.mkdir())
            log.warn("Make {} directory failed", txDir.getAbsolutePath());

        if (!txOutputDir.mkdir())
            log.warn("Make {} directory failed", txOutputDir.getAbsolutePath());


        blockFileManager = new JsonFileManager(blockDir);
        txFileManager = new JsonFileManager(txDir);
        txOutputFileManager = new JsonFileManager(txOutputDir);
        spentInfoMapFileManager = new JsonFileManager(mutableDataDir);
        unspentTxOutputMapFileManager = new JsonFileManager(mutableDataDir);
        issuanceMapFileManager = new JsonFileManager(mutableDataDir);
        confiscatedLockupTxListFileManager = new JsonFileManager(mutableDataDir);
    }

    public void shutDown() {
        if (!dumpBlockchainData) {
            return;
        }

        blockFileManager.shutDown();
        txFileManager.shutDown();
        txOutputFileManager.shutDown();
        spentInfoMapFileManager.shutDown();
        unspentTxOutputMapFileManager.shutDown();
        issuanceMapFileManager.shutDown();
        confiscatedLockupTxListFileManager.shutDown();

        dumpBlockchainData = false;
    }

    public void onNewBlock(Block block) {
        if (!dumpBlockchainData) {
            return;
        }

        if (!daoStateService.isParseBlockChainComplete()) {
            // While we are syncing we ignore new blocks. We will check from missing blocks after batch processing
            // to pickup potentially newly arrived blocks.
            return;
        }

        // We do write the block on the main thread as the overhead to create a thread and risk for inconsistency is not
        // worth the potential performance gain.
        processBlock(block, false);
    }

    private void processBlock(Block block, boolean isBatchProcess) {
        if (!isBatchProcess) {
            // When batch processing we do not do that check
            int lastPersistedBlock = getLastPersistedBlock();
            if (block.getHeight() <= lastPersistedBlock) {
                return;
            }
        }
        long ts = System.currentTimeMillis();
        blockFileManager.writeToDisc(Utilities.objectToJson(block), String.valueOf(block.getHeight()));
        block.getTxs().forEach(tx -> {
            txFileManager.writeToDisc(Utilities.objectToJson(tx), tx.getId());
            tx.getTxOutputs().forEach(txOutput ->
                    txOutputFileManager.writeToDisc(Utilities.objectToJson(txOutput), txOutput.getKey().toString()));
        });

        if (!isBatchProcess) {
            log.info("Write json data for block {} took {} ms", block.getHeight(), System.currentTimeMillis() - ts);
            writeMutableData();
        }
    }

    public void onParseBlockChainComplete() {
        if (!dumpBlockchainData) {
            return;
        }

        long ts = System.currentTimeMillis();
        int lastPersistedBlock = getLastPersistedBlock();
        int chainHeight = daoStateService.getChainHeight();
        if (lastPersistedBlock < chainHeight) {
            int startFrom = lastPersistedBlock + 1;
            List<Block> blocks = daoStateService.getBlocksFromBlockHeight(startFrom, Integer.MAX_VALUE);
            // We use a thread here to write all past blocks to avoid that the main thread gets blocked for too long.
            // Blocks are immutable so threading cannot cause any issue here.
            new Thread(() -> {
                Thread.currentThread().setName("Write-blocks-to-json");
                blocks.forEach(e -> processBlock(e, true));
                log.info("Batch processing {} blocks from block {} on took {}",
                        blocks.size(),
                        startFrom,
                        FormattingUtils.formatDurationAsWords(System.currentTimeMillis() - ts,
                                true, true));

                UserThread.execute(() -> {
                    // As its mutable data we do it on the UserThread
                    writeMutableData();

                    // Once done we have to repeat our call as it might be that during batch processing
                    // we have received new blocks. As we request daoStateService data we also do it on the UserThread.
                    onParseBlockChainComplete();
                });
            }).start();
        }
    }

    private void writeMutableData() {
        long ts = System.currentTimeMillis();
        log.error("write writeMutableData {}", daoStateService.getChainHeight());
        spentInfoMapFileManager.writeToDisc(Utilities.objectToJson(daoStateService.getSpentInfoMap()), "spentInfoMap");
        unspentTxOutputMapFileManager.writeToDisc(Utilities.objectToJson(daoStateService.getUnspentTxOutputMap()), "unspentTxOutputMap");
        issuanceMapFileManager.writeToDisc(Utilities.objectToJson(daoStateService.getIssuanceMap()), "issuanceMap");
        confiscatedLockupTxListFileManager.writeToDisc(Utilities.objectToJson(daoStateService.getConfiscatedLockupTxList()), "confiscatedLockupTxList");
        log.info("Write mutableData took {}", System.currentTimeMillis() - ts);
    }

    private int getLastPersistedBlock() {
        // At start we use one block before genesis
        int result = daoStateService.getGenesisBlockHeight() - 1;
        String[] list = blockDir.list();
        if (list != null && list.length > 0) {
            List<Integer> blocks = Arrays.stream(list)
                    .filter(e -> !e.endsWith(".tmp"))
                    .map(e -> e.replace(".json", ""))
                    .map(Integer::valueOf)
                    .sorted()
                    .collect(Collectors.toList());
            if (!blocks.isEmpty()) {
                Integer lastBlockHeight = blocks.get(blocks.size() - 1);
                if (lastBlockHeight > result) {
                    result = lastBlockHeight;
                }
            }
        }
        return result;
    }
}

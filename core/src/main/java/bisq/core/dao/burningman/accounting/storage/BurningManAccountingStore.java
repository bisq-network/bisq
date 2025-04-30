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

package bisq.core.dao.burningman.accounting.storage;

import bisq.core.dao.burningman.accounting.blockchain.AccountingBlock;
import bisq.core.dao.burningman.accounting.exceptions.BlockHashNotConnectingException;
import bisq.core.dao.burningman.accounting.exceptions.BlockHeightNotConnectingException;

import bisq.common.proto.persistable.PersistableEnvelope;

import com.google.protobuf.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.dao.burningman.accounting.BurningManAccountingService.EARLIEST_BLOCK_HEIGHT;

@Slf4j
public class BurningManAccountingStore implements PersistableEnvelope {
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    // We preserve the invariant that block heights are contiguous, starting from EARLIEST_BLOCK_HEIGHT:
    private final List<AccountingBlock> blocks = new ArrayList<>();

    public BurningManAccountingStore(List<AccountingBlock> blocks) {
        AccountingBlock previousBlock = null;
        for (var block : blocks) {
            if (block.getHeight() != (previousBlock != null ? previousBlock.getHeight() + 1 : EARLIEST_BLOCK_HEIGHT)) {
                log.error("Initializing with non-connecting block at height {}. Ignoring it and all subsequent blocks.", block.getHeight());
                break;
            }
            this.blocks.add(block);
            previousBlock = block;
        }
    }

    public void addIfNewBlock(AccountingBlock newBlock) throws BlockHeightNotConnectingException, BlockHashNotConnectingException {
        Lock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            tryToAddNewBlock(newBlock);
        } finally {
            writeLock.unlock();
        }
    }

    public void forEachBlock(Consumer<AccountingBlock> consumer) {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            blocks.forEach(consumer);
        } finally {
            readLock.unlock();
        }
    }

    public void purgeLastTenBlocks() {
        Lock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            blocks.subList(Math.max(blocks.size() - 10, 0), blocks.size()).clear();
        } finally {
            writeLock.unlock();
        }
    }

    public void removeAllBlocks() {
        Lock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            blocks.clear();
        } finally {
            writeLock.unlock();
        }
    }

    public Optional<AccountingBlock> getLastBlock() {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return !blocks.isEmpty() ? Optional.of(blocks.get(blocks.size() - 1)) : Optional.empty();
        } finally {
            readLock.unlock();
        }
    }

    public Optional<AccountingBlock> getBlockAtHeight(int height) {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return height >= EARLIEST_BLOCK_HEIGHT && height < EARLIEST_BLOCK_HEIGHT + blocks.size() ?
                    Optional.of(blocks.get(height - EARLIEST_BLOCK_HEIGHT)) : Optional.empty();
        } finally {
            readLock.unlock();
        }
    }

    public List<AccountingBlock> getBlocksAtLeastWithHeight(int minHeight) {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return minHeight >= EARLIEST_BLOCK_HEIGHT && minHeight < EARLIEST_BLOCK_HEIGHT + blocks.size() ?
                    List.copyOf(blocks.subList(minHeight - EARLIEST_BLOCK_HEIGHT, blocks.size())) : List.of();
        } finally {
            readLock.unlock();
        }
    }

    private boolean contains(AccountingBlock block) {
        var blockAtSameHeight = getBlockAtHeight(block.getHeight());
        return blockAtSameHeight.isPresent() && blockAtSameHeight.get().equals(block);
    }

    private void tryToAddNewBlock(AccountingBlock newBlock) throws BlockHeightNotConnectingException, BlockHashNotConnectingException {
        if (!contains(newBlock)) {
            Optional<AccountingBlock> optionalLastBlock = getLastBlock();
            if (optionalLastBlock.isPresent()) {
                AccountingBlock lastBlock = optionalLastBlock.get();
                if (newBlock.getHeight() != lastBlock.getHeight() + 1) {
                    throw new BlockHeightNotConnectingException();
                }
                if (!Arrays.equals(newBlock.getTruncatedPreviousBlockHash(), lastBlock.getTruncatedHash())) {
                    throw new BlockHashNotConnectingException();
                }
            } else if (newBlock.getHeight() != EARLIEST_BLOCK_HEIGHT) {
                throw new BlockHeightNotConnectingException();
            }
            log.info("Add new accountingBlock at height {} at {} with {} txs", newBlock.getHeight(),
                    new Date(newBlock.getDate()), newBlock.getTxs().size());
            blocks.add(newBlock);
        } else {
            log.info("We have that block already. Height: {}", newBlock.getHeight());
        }
    }

    public Message toProtoMessage() {
        List<AccountingBlock> blocksCopy;
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            blocksCopy = new ArrayList<>(blocks);
        } finally {
            readLock.unlock();
        }
        return protobuf.PersistableEnvelope.newBuilder()
                .setBurningManAccountingStore(protobuf.BurningManAccountingStore.newBuilder()
                        .addAllBlocks(blocksCopy.stream()
                                .map(AccountingBlock::toProtoMessage)
                                .collect(Collectors.toList())))
                .build();
    }

    public static BurningManAccountingStore fromProto(protobuf.BurningManAccountingStore proto) {
        return new BurningManAccountingStore(proto.getBlocksList().stream()
                .map(AccountingBlock::fromProto)
                .collect(Collectors.toList()));
    }
}

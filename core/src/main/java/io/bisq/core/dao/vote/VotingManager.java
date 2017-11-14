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

package io.bisq.core.dao.vote;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.bisq.common.app.Version;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.proto.persistable.PersistableList;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.dao.DaoPeriodService;
import io.bisq.core.dao.compensation.CompensationRequest;
import io.bisq.core.dao.compensation.CompensationRequestManager;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.generated.protobuffer.PB;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class VotingManager implements PersistedDataHost {

    public static final String ERROR_MSG_MISSING_BYTE = "We need to have at least 1 more byte for the voting value.";
    public static final String ERROR_MSG_WRONG_SIZE = "sizeOfCompReqVotesInBytes must be 0 or multiple of 2. sizeOfCompReqVotesInBytes=";
    public static final String ERROR_MSG_INVALID_COMP_REQ_MAPS = "Bitmaps for compensation requests are invalid.";
    public static final String ERROR_MSG_INVALID_COMP_REQ_VAL = "We found an accepted vote at a not voted request.";

    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final FeeService feeService;
    private final Storage<PersistableList<VoteItemsList>> voteItemCollectionsStorage;
    private final CompensationRequestManager compensationRequestManager;
    private final DaoPeriodService daoPeriodService;
    private final VotingDefaultValues votingDefaultValues;
    private final List<VoteItemsList> voteItemsLists = new ArrayList<>();
    private VoteItemsList activeVoteItemsList;

    @Inject
    public VotingManager(BtcWalletService btcWalletService,
                         BsqWalletService bsqWalletService,
                         FeeService feeService,
                         Storage<PersistableList<VoteItemsList>> voteItemCollectionsStorage,
                         CompensationRequestManager compensationRequestManager,
                         DaoPeriodService daoPeriodService,
                         VotingDefaultValues votingDefaultValues) {
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.feeService = feeService;
        this.voteItemCollectionsStorage = voteItemCollectionsStorage;
        this.compensationRequestManager = compensationRequestManager;
        this.daoPeriodService = daoPeriodService;
        this.votingDefaultValues = votingDefaultValues;
    }

    @VisibleForTesting
    VotingManager(VotingDefaultValues votingDefaultValues) {
        this.btcWalletService = null;
        this.bsqWalletService = null;
        this.feeService = null;
        this.voteItemCollectionsStorage = null;
        this.compensationRequestManager = null;
        this.daoPeriodService = null;
        this.votingDefaultValues = votingDefaultValues;
    }

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            PersistableList<VoteItemsList> persisted = voteItemCollectionsStorage.initAndGetPersistedWithFileName("VoteItemCollections", 100);
            if (persisted != null)
                voteItemsLists.addAll(persisted.getList());
        }
    }

    public void onAllServicesInitialized() {
        if (daoPeriodService.getPhase() == DaoPeriodService.Phase.OPEN_FOR_VOTING) {
            VoteItemsList activeVoteItemsList = new VoteItemsList(votingDefaultValues);
            setActiveVoteItemsList(activeVoteItemsList);
        }
    }

    public VotingDefaultValues getVotingDefaultValues() {
        return votingDefaultValues;
    }

    public byte[] getCompensationRequestsCollection() {
        List<CompensationRequestPayload> list = compensationRequestManager.getCompensationRequestsList().stream()
                .filter(CompensationRequest::isInVotePeriod)
                .map(CompensationRequest::getCompensationRequestPayload)
                .collect(Collectors.toList());
        CompensationRequestPayload[] array = new CompensationRequestPayload[list.size()];
        list.toArray(array);
        String json = StringUtils.deleteWhitespace(Utilities.objectToJson(array));
        return json.getBytes();
    }

    public VoteItemsList getVoteItemListFromOpReturnData(byte[] opReturnData) {
        VoteItemsList voteItems = new VoteItemsList(votingDefaultValues);
        CompensationRequestVoteItemCollection compensationRequestVoteItemCollection = voteItems.getCompensationRequestVoteItemCollection();
        // Protocol allows values 0 or multiple of 2. But in implementation we limit to 0 and 2
        int sizeOfCompReqVotesInBytes = 0;
        CompensationRequestVoteItem compensationRequestVoteItem;
        VotingType votingTypeByCode = null;
        for (int i = 0; i < opReturnData.length; i++) {
            Byte currentByte = opReturnData[i];
            String info;
            if (i == 0) {
                info = "Version" + ": " + String.format("0x%02x ", currentByte);
                if (currentByte != Version.VOTING_VERSION)
                    return voteItems;
            } else if (i < 21) {
                info = "Hash of CompensationRequestsCollection";
                byte[] hashOfCompensationRequestsCollection = new byte[20];
                System.arraycopy(opReturnData, 1, hashOfCompensationRequestsCollection, 0, 20);
                voteItems.setHashOfCompensationRequestsCollection(hashOfCompensationRequestsCollection);
            } else if (i < 22) {
                info = "Num of CompensationRequests vote bytes" + ": " + currentByte;
                // TODO check conversion to int
                sizeOfCompReqVotesInBytes = currentByte.intValue();
                checkArgument(sizeOfCompReqVotesInBytes % 2 == 0,
                        ERROR_MSG_WRONG_SIZE + sizeOfCompReqVotesInBytes);
            } else if (sizeOfCompReqVotesInBytes > 0 && i < 22 + sizeOfCompReqVotesInBytes) {
                // We fill up all 8 item blocks, might be that we have less real CompensationRequestVoteItems...
                if (i % 2 == 0) {
                    info = "CompensationRequests hasVoted bitmap";
                    Byte nextByte = opReturnData[i + 1];
                    final BitSet bitSetVoted = BitSet.valueOf(new byte[]{currentByte});
                    final BitSet bitSetValue = BitSet.valueOf(new byte[]{nextByte});
                    checkArgument(bitSetVoted.length() >= bitSetValue.length(), ERROR_MSG_INVALID_COMP_REQ_MAPS);
                    for (int n = 0; n < bitSetVoted.length(); n++) {
                        compensationRequestVoteItem = new CompensationRequestVoteItem(null);
                        compensationRequestVoteItemCollection.addCompensationRequestVoteItem(compensationRequestVoteItem);
                        boolean hasVoted = bitSetVoted.get(n);
                        compensationRequestVoteItem.setHasVoted(hasVoted);
                        boolean accepted = bitSetValue.get(n);
                        if (hasVoted) {
                            if (accepted)
                                compensationRequestVoteItem.setAcceptedVote(true);
                            else
                                compensationRequestVoteItem.setDeclineVote(true);
                        } else {
                            checkArgument(!accepted, ERROR_MSG_INVALID_COMP_REQ_VAL);
                        }
                    }
                } else {
                    info = "CompensationRequests value bitmap";
                }
            } else if (i % 2 == 0) {
                checkArgument(opReturnData.length > i + 1, ERROR_MSG_MISSING_BYTE);
                info = "Param code" + ": " + String.format("0x%02x", currentByte);
                votingTypeByCode = votingDefaultValues.getVotingTypeByCode(currentByte);
            } else {
                long defaultValue = votingDefaultValues.getValueByVotingType(votingTypeByCode);
                long adjustedValue = votingDefaultValues.getAdjustedValue(defaultValue, currentByte);
                votingDefaultValues.setValueByVotingType(votingTypeByCode, adjustedValue);
                Optional<VoteItem> voteItem = voteItems.getVoteItemByVotingType(votingTypeByCode);
                checkArgument(voteItem.isPresent(), "voteItem need to be available.");
                voteItem.get().setValue(currentByte);
                info = "Param value" + ": " + currentByte;
            }
            log.info(info);
            //log.error(String.format("%8s", Integer.toBinaryString(currentByte & 0xFF)).replace(" ", "0") + " [" + info + "]");
        }
        return voteItems;
    }

    public byte[] calculateOpReturnData(VoteItemsList voteItemsList) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // First we add the version byte
            outputStream.write(Version.VOTING_VERSION);

            // Next we add the 20 bytes hash of the voter’s compensation requests collection.
            // This is needed to mark our version of the "reality" as we have only eventually consistency in the
            // P2P network we cannot guarantee that all peers have the same data.
            // In the voting result we only consider those which match the majority view.
            outputStream.write(Utils.sha256hash160(getCompensationRequestsCollection()));

            // Then we add the a multiple of 2 bytes for compensationRequest votes
            Optional<VoteItem> itemOptional = voteItemsList.getAllVoteItemList().stream()
                    .filter(e -> e instanceof CompensationRequestVoteItemCollection)
                    .findAny();
            int sizeOfCompReqVotesInBytes = 0;
            if (itemOptional.isPresent()) {
                checkArgument(itemOptional.get() instanceof CompensationRequestVoteItemCollection,
                        "Item must be CompensationRequestVoteItemCollection");
                CompensationRequestVoteItemCollection collection = (CompensationRequestVoteItemCollection) itemOptional.get();
                List<CompensationRequestVoteItem> items = collection.getCompensationRequestVoteItemsSortedByTxId();
                int itemsSize = items.size();
                // We have max 39 bytes space ((80 - 20 - 2) / 2 = 29). 29 bytes are 232 bits/items to vote on
                checkArgument(itemsSize <= 232, "itemsSize must not be more than 232. " +
                        "We got itemsSize=" + itemsSize);

                // We want groups of 8 bits so if we have less then a multiple of 8 we fill it up with 0
                int paddedBitSize = itemsSize > 0 ? ((itemsSize - 1) / 8 + 1) * 8 : 0;
                // We add the size of the bytes we use for the comp request vote data. Can be 0 or multiple of 2.
                sizeOfCompReqVotesInBytes = paddedBitSize / 8 * 2;
                outputStream.write((byte) sizeOfCompReqVotesInBytes);

                BitSet bitSetVoted = new BitSet(paddedBitSize);
                BitSet bitSetValue = new BitSet(paddedBitSize);
                for (int i = 0; i < paddedBitSize; i++) {
                    if (i < itemsSize) {
                        CompensationRequestVoteItem item = items.get(i);
                        boolean hasVoted = item.isHasVoted();
                        bitSetVoted.set(i, hasVoted);
                        if (hasVoted) {
                            boolean acceptedVote = item.isAcceptedVote();
                            checkArgument(acceptedVote == !item.isDeclineVote(), "Accepted must be opposite of declined value");
                            bitSetValue.set(i, acceptedVote);
                        } else {
                            bitSetValue.set(i, false);
                        }
                    } else {
                        bitSetVoted.set(i, false);
                        bitSetValue.set(i, false);
                    }
                }

                byte[] bitSetVotedArray = bitSetVoted.toByteArray();
                if (bitSetVotedArray.length > 0)
                    outputStream.write(bitSetVotedArray);
                else
                    outputStream.write((byte) 0);

                byte[] bitSetValueArray = bitSetValue.toByteArray();
                if (bitSetValueArray.length > 0)
                    outputStream.write(bitSetValueArray);
                else
                    outputStream.write((byte) 0);
            } else {
                // If we don't have items we set size 0
                outputStream.write((byte) 0);
            }

            // After that we add the optional parameter votes
            int freeSpace = 80 - outputStream.size();
            Set<VoteItem> items = voteItemsList.getAllVoteItemList().stream()
                    .filter(e -> !(e instanceof CompensationRequestVoteItemCollection))
                    .filter(VoteItem::isHasVoted)
                    .collect(Collectors.toSet());
            checkArgument(items.size() <= freeSpace,
                    "Size of parameter items must not exceed free space.");

            items.stream().forEach(paramItem -> {
                checkArgument(outputStream.size() % 2 == 0,
                        "Position of writing code must be at even index.");
                outputStream.write(paramItem.getVotingType().code);
                byte value = paramItem.getValue();
                outputStream.write(value);
            });

            byte[] bytes = outputStream.toByteArray();
            for (int i = 0; i < bytes.length; i++) {
                Byte currentByte = bytes[i];
                String info;
                if (i == 0)
                    info = "Version" + ": " + String.format("0x%02x ", currentByte);
                else if (i < 21)
                    info = "Hash of CompensationRequestsCollection";
                else if (i < 22)
                    info = "Num of CompensationRequests vote bytes" + ": " + currentByte;
                else if (sizeOfCompReqVotesInBytes > 0 && i < 22 + sizeOfCompReqVotesInBytes)
                    info = i % 2 == 0 ? "CompensationRequests hasVoted bitmap" : "CompensationRequests value bitmap";
                else if (i % 2 == 0)
                    info = "Param code" + ": " + String.format("0x%02x", currentByte);
                else
                    info = "Param value" + ": " + currentByte;
                log.error(String.format("%8s", Integer.toBinaryString(currentByte & 0xFF)).replace(" ", "0") + " [" + info + "]");
            }
            return bytes;
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    byte[] toBytes(int i) {
        byte[] result = new byte[4];

        result[0] = (byte) (i >> 24);
        result[1] = (byte) (i >> 16);
        result[2] = (byte) (i >> 8);
        result[3] = (byte) (i /*>> 0*/);

        return result;
    }

    public void addVoteItemCollection(VoteItemsList voteItemsList) {
        //TODO check equals code
        if (!voteItemsLists.contains(voteItemsList)) {
            voteItemsLists.add(voteItemsList);
            PersistableList<VoteItemsList> list = new PersistableList<>(voteItemsLists);
            list.setToProto(e -> PB.PersistableEnvelope.newBuilder()
                    .setVoteItemsList(PB.VoteItemsList.newBuilder()
                            .addAllVoteItem(ProtoUtil.collectionToProto(voteItemsList.getAllVoteItemList()))).build());
            voteItemCollectionsStorage.queueUpForSave(list, 500);
        }
    }

    public void setActiveVoteItemsList(VoteItemsList activeVoteItemsList) {
        this.activeVoteItemsList = activeVoteItemsList;
        addVoteItemCollection(activeVoteItemsList);
    }

    public VoteItemsList getActiveVoteItemsList() {
        return activeVoteItemsList;
    }


    //TODO
    public boolean isCompensationRequestAccepted(CompensationRequest compensationRequest) {
        return true;
    }
}

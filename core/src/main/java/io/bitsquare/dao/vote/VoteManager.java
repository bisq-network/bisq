/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.dao.vote;

import com.google.inject.Inject;
import io.bitsquare.app.Version;
import io.bitsquare.btc.provider.fee.FeeService;
import io.bitsquare.btc.wallet.BtcWalletService;
import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.dao.compensation.CompensationRequest;
import io.bitsquare.dao.compensation.CompensationRequestManager;
import io.bitsquare.dao.compensation.CompensationRequestPayload;
import io.bitsquare.storage.Storage;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class VoteManager {
    private static final Logger log = LoggerFactory.getLogger(VoteManager.class);
    private final BtcWalletService btcWalletService;
    private final SquWalletService squWalletService;
    private FeeService feeService;
    private final Storage<ArrayList<VoteItemCollection>> voteItemCollectionsStorage;
    private CompensationRequestManager compensationRequestManager;
    private ArrayList<VoteItemCollection> voteItemCollections = new ArrayList<>();
    private VoteItemCollection currentVoteItemCollection;

    @Inject
    public VoteManager(BtcWalletService btcWalletService, SquWalletService squWalletService, FeeService feeService,
                       Storage<ArrayList<VoteItemCollection>> voteItemCollectionsStorage, CompensationRequestManager compensationRequestManager) {
        this.btcWalletService = btcWalletService;
        this.squWalletService = squWalletService;
        this.feeService = feeService;
        this.voteItemCollectionsStorage = voteItemCollectionsStorage;
        this.compensationRequestManager = compensationRequestManager;

        ArrayList<VoteItemCollection> persisted = voteItemCollectionsStorage.initAndGetPersistedWithFileName("VoteItemCollections");
        if (persisted != null)
            voteItemCollections.addAll(persisted);

        checkIfOpenForVoting();
    }

    private void checkIfOpenForVoting() {
        //TODO mock
        setCurrentVoteItemCollection(new VoteItemCollection());
    }

    public byte[] getCompensationRequestsCollection() {
        List<CompensationRequestPayload> list = compensationRequestManager.getObservableCompensationRequestsList().stream()
                .filter(CompensationRequest::isInVotePeriod)
                .map(CompensationRequest::getCompensationRequestPayload)
                .collect(Collectors.toList());
        CompensationRequestPayload[] array = new CompensationRequestPayload[list.size()];
        list.toArray(array);
        String json = StringUtils.deleteWhitespace(Utilities.objectToJson(array));
        return json.getBytes();
    }

    public byte[] calculateHash(VoteItemCollection voteItemCollection) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // First we add the version byte
            outputStream.write(Version.VOTING_VERSION);

            // Next we add the 20 bytes hash of the voter’s compensation requests collection.
            // This is needed to mark our version of the "reality" as we have only eventually consistency in the 
            // P2P network we cannot guarantee that all peers have the same data.
            // In the voting result we only consider those which match the majority view.
            outputStream.write(Utils.sha256hash160(getCompensationRequestsCollection()));

            // Then we add the a multiple of 2 bytes for compensationRequest votes
            Optional<VoteItem> itemOptional = voteItemCollection.stream()
                    .filter(e -> e instanceof CompensationRequestVoteItemCollection)
                    .findAny();
            int sizeOfCompReqVotesInBytes = 0;
            if (itemOptional.isPresent()) {
                checkArgument(itemOptional.get() instanceof CompensationRequestVoteItemCollection,
                        "Item must be CompensationRequestVoteItemCollection");
                CompensationRequestVoteItemCollection collection = (CompensationRequestVoteItemCollection) itemOptional.get();
                int payloadSize = collection.code.payloadSize;
                checkArgument(payloadSize == 2, "payloadSize for CompensationRequestVoteItemCollection must be 2. " +
                        "We got payloadSize=" + payloadSize);
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
            Set<VoteItem> items = voteItemCollection.stream()
                    .filter(e -> !(e instanceof CompensationRequestVoteItemCollection))
                    .filter(VoteItem::hasVoted)
                    .collect(Collectors.toSet());
            checkArgument(items.size() <= freeSpace,
                    "Size of parameter items must not exceed free space.");

            items.stream().forEach(paramItem -> {
                checkArgument(outputStream.size() % 2 == 0,
                        "Position of writing code must be at even index.");
                outputStream.write(paramItem.code.code);
                int payloadSize = paramItem.code.payloadSize;
                checkArgument(payloadSize == 1,
                        "payloadSize is not as expected(4). payloadSize=" + payloadSize);
                byte value = paramItem.getValue();
                outputStream.write(value);
            });

            byte[] bytes = outputStream.toByteArray();
            for (int i = 0; i < bytes.length; i++) {
                byte b = bytes[i];
                String info = "";
                if (i == 0)
                    info = "Version" + ": " + String.format("0x%02x ", b);
                else if (i < 21)
                    info = "Hash of CompensationRequestsCollection";
                else if (i < 22)
                    info = "Num of CompensationRequests vote bytes" + ": " + b;
                else if (sizeOfCompReqVotesInBytes > 0 && i < 22 + sizeOfCompReqVotesInBytes)
                    info = i % 2 == 0 ? "CompensationRequests hasVoted bitmap" : "CompensationRequests value bitmap";
                else if (i % 2 == 0)
                    info = "Param code" + ": " + String.format("0x%02x", b);
                else
                    info = "Param value" + ": " + b;
                log.error(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(" ", "0") + " [" + info + "]");
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

    public void addVoteItemCollection(VoteItemCollection voteItemCollection) {
        //TODO check equals code
        if (!voteItemCollections.contains(voteItemCollection)) {
            voteItemCollections.add(voteItemCollection);
            voteItemCollectionsStorage.queueUpForSave(voteItemCollections, 500);
        }
    }

    public void setCurrentVoteItemCollection(VoteItemCollection currentVoteItemCollection) {
        this.currentVoteItemCollection = currentVoteItemCollection;
        addVoteItemCollection(currentVoteItemCollection);
    }

    public VoteItemCollection getCurrentVoteItemCollection() {
        return currentVoteItemCollection;
    }
}

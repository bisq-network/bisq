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
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.exceptions.WalletException;
import io.bitsquare.btc.wallet.BtcWalletService;
import io.bitsquare.btc.wallet.ChangeBelowDustException;
import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.storage.Storage;
import org.bitcoinj.core.InsufficientMoneyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class VoteManager {
    private static final Logger log = LoggerFactory.getLogger(VoteManager.class);
    private final BtcWalletService btcWalletService;
    private final SquWalletService squWalletService;
    private final Storage<ArrayList<VoteItemCollection>> voteItemCollectionStorage;
    private ArrayList<VoteItemCollection> voteItemCollections = new ArrayList<>();
    private VoteItemCollection currentVoteItemCollection;

    @Inject
    public VoteManager(BtcWalletService btcWalletService, SquWalletService squWalletService, Storage<ArrayList<VoteItemCollection>> voteItemCollectionStorage) {
        this.btcWalletService = btcWalletService;
        this.squWalletService = squWalletService;
        this.voteItemCollectionStorage = voteItemCollectionStorage;

        ArrayList<VoteItemCollection> persisted = voteItemCollectionStorage.initAndGetPersistedWithFileName("VoteItemCollections");
        if (persisted != null)
            voteItemCollections.addAll(persisted);

        checkIfOpenForVoting();
    }

    private void checkIfOpenForVoting() {
        //TODO mock
        setCurrentVoteItemCollection(new VoteItemCollection());
    }

    public void vote(VoteItemCollection voteItemCollection) throws InsufficientMoneyException, ChangeBelowDustException, TransactionVerificationException, WalletException {
        log.error(voteItemCollection.toString());
        byte[] hash = calculateHash(voteItemCollection);

        //todo
        squWalletService.getPreparedVotingTx(null);
        // btcWalletService
        squWalletService.signAndBroadcastVotingTx(null, null);
        
    }

    private byte[] calculateHash(VoteItemCollection voteItemCollection) {
        List<Byte> bytes = new ArrayList<>();
        voteItemCollection.stream().forEach(e -> {

            if (e instanceof CompensationRequestVoteItemCollection) {

            } else {
                if (e.hasVoted()) {
                    bytes.add(e.code);
                    int value = e.getValue();

                }
            }
        });
        return new byte[0];
    }

    byte[] toBytes(int i) {
        byte[] result = new byte[4];

        result[0] = (byte) (i >> 24);
        result[1] = (byte) (i >> 16);
        result[2] = (byte) (i >> 8);
        result[3] = (byte) (i /*>> 0*/);

        return result;
    }

    public void setVoteItemCollections(VoteItemCollection voteItemCollection) {
        //TODO check equals code
        if (!voteItemCollections.contains(voteItemCollection)) {
            voteItemCollections.add(voteItemCollection);
            voteItemCollectionStorage.queueUpForSave(voteItemCollections, 500);
        }
    }

    public void setCurrentVoteItemCollection(VoteItemCollection currentVoteItemCollection) {
        this.currentVoteItemCollection = currentVoteItemCollection;
        setVoteItemCollections(currentVoteItemCollection);
    }

    public VoteItemCollection getCurrentVoteItemCollection() {
        return currentVoteItemCollection;
    }
}

package io.bisq.gui.util.validation.params;

import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.MonetaryFormat;

public class TerracoinParams extends NetworkParameters {

    private static TerracoinParams instance;

    public static synchronized TerracoinParams get() {
        if (instance == null) {
            instance = new TerracoinParams();
        }
        return instance;
    }

    // We only use the properties needed for address validation
    public TerracoinParams() {
        super();
        addressHeader = 0;
        p2shHeader = 5;
        acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
    }

    // default dummy implementations, not used...
    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_MAINNET;
    }

    @Override
    public void checkDifficultyTransitions(StoredBlock storedPrev, Block next, BlockStore blockStore) throws VerificationException, BlockStoreException {
    }

    @Override
    public Coin getMaxMoney() {
        return null;
    }

    @Override
    public Coin getMinNonDustOutput() {
        return null;
    }

    @Override
    public MonetaryFormat getMonetaryFormat() {
        return null;
    }

    @Override
    public String getUriScheme() {
        return null;
    }

    @Override
    public boolean hasMaxMoney() {
        return false;
    }

    @Override
    public BitcoinSerializer getSerializer(boolean parseRetain) {
        return null;
    }

    @Override
    public int getProtocolVersionNum(ProtocolVersion version) {
        return 0;
    }
    }

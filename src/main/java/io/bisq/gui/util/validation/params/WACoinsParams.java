package io.bisq.gui.util.validation.params;

import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.MonetaryFormat;

public class WACoinsParams extends NetworkParameters {

    private static WACoinsParams instance;

    public static synchronized WACoinsParams get() {
        if (instance == null) {
            instance = new WACoinsParams();
        }
        return instance;
    }

    // We only use the properties needed for address validation
    public WACoinsParams() {
        super();
        addressHeader = 73;
        p2shHeader = 3;
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

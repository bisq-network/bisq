package io.bitsquare.btc.provider.fee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeeData {
    private static final Logger log = LoggerFactory.getLogger(FeeData.class);

    public final long txFee;
    public final long createOfferFee;
    public final long takeOfferFee;

    public FeeData(long txFee, long createOfferFee, long takeOfferFee) {
        this.txFee = txFee;
        this.createOfferFee = createOfferFee;
        this.takeOfferFee = takeOfferFee;
    }
}

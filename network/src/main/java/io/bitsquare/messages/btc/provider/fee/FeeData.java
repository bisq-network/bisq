package io.bitsquare.messages.btc.provider.fee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeeData {
    private static final Logger log = LoggerFactory.getLogger(FeeData.class);

    public final long txFeePerByte;
    public final long createOfferFee;
    public final long takeOfferFee;

    public FeeData(long txFeePerByte, long createOfferFee, long takeOfferFee) {
        this.txFeePerByte = txFeePerByte;
        this.createOfferFee = createOfferFee;
        this.takeOfferFee = takeOfferFee;
    }
}

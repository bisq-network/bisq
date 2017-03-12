package io.bisq.messages.btc.provider.fee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeeData {
    private static final Logger log = LoggerFactory.getLogger(FeeData.class);

    public final long txFeePerByte;

    public FeeData(long txFeePerByte) {
        this.txFeePerByte = txFeePerByte;
    }
}

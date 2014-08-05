package io.bitsquare.trade.protocol.offerer;

import com.google.bitcoin.core.Coin;
import io.bitsquare.trade.protocol.TradeMessage;
import java.io.Serializable;

public class BankTransferInitedMessage implements Serializable, TradeMessage
{
    private static final long serialVersionUID = -3479634129543632523L;
    private final String tradeId;

    private String depositTxAsHex;
    private String offererSignatureR;
    private String offererSignatureS;
    private Coin offererPaybackAmount;
    private Coin takerPaybackAmount;
    private String offererPayoutAddress;

    public BankTransferInitedMessage(String tradeId,
                                     String depositTxAsHex,
                                     String offererSignatureR,
                                     String offererSignatureS,
                                     Coin offererPaybackAmount,
                                     Coin takerPaybackAmount,
                                     String offererPayoutAddress)
    {
        this.tradeId = tradeId;
        this.depositTxAsHex = depositTxAsHex;
        this.offererSignatureR = offererSignatureR;
        this.offererSignatureS = offererSignatureS;
        this.offererPaybackAmount = offererPaybackAmount;
        this.takerPaybackAmount = takerPaybackAmount;
        this.offererPayoutAddress = offererPayoutAddress;
    }

    @Override
    public String getTradeId()
    {
        return tradeId;
    }

    public String getDepositTxAsHex()
    {
        return depositTxAsHex;
    }

    public String getOffererPayoutAddress()
    {
        return offererPayoutAddress;
    }

    public String getOffererSignatureS()
    {
        return offererSignatureS;
    }

    public Coin getOffererPaybackAmount()
    {
        return offererPaybackAmount;
    }

    public Coin getTakerPaybackAmount()
    {
        return takerPaybackAmount;
    }

    public String getOffererSignatureR()
    {
        return offererSignatureR;
    }
}

package bisq.core.dao.governance.proposal;

import bisq.core.dao.state.model.blockchain.Tx;

import org.bitcoinj.core.Coin;

import lombok.Getter;

import javax.annotation.Nullable;

@Getter
public class ProposalValidationException extends Exception {
    @Nullable
    private Coin requestedBsq;
    @Nullable
    private Coin minRequestAmount;
    @Nullable
    private Tx tx;

    public ProposalValidationException(String message, Coin requestedBsq, Coin minRequestAmount) {
        super(message);
        this.requestedBsq = requestedBsq;
        this.minRequestAmount = minRequestAmount;
    }

    public ProposalValidationException(Throwable cause) {
        super(cause);
    }

    public ProposalValidationException(String message) {
        super(message);
    }

    public ProposalValidationException(String message, Throwable throwable) {
        super(message, throwable);

    }

    public ProposalValidationException(String message, Tx tx) {
        super(message);
        this.tx = tx;
    }

    public ProposalValidationException(Throwable cause, Tx tx) {
        super(cause);
        this.tx = tx;
    }

    @Override
    public String toString() {
        return "ProposalValidationException{" +
                "\n     requestedBsq=" + requestedBsq +
                ",\n     minRequestAmount=" + minRequestAmount +
                ",\n     tx=" + tx +
                "\n} " + super.toString();
    }
}

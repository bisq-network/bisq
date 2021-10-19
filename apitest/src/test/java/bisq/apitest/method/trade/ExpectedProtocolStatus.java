package bisq.apitest.method.trade;

import bisq.core.trade.model.bisq_v1.Trade;

/**
 * A test fixture encapsulating expected trade protocol status.
 * Status flags should be cleared via init() before starting a new trade protocol.
 */
public class ExpectedProtocolStatus {
    Trade.State state;
    Trade.Phase phase;
    boolean isDepositPublished;
    boolean isDepositConfirmed;
    boolean isFiatSent;
    boolean isFiatReceived;
    boolean isPayoutPublished;
    boolean isWithdrawn;

    public ExpectedProtocolStatus setState(Trade.State state) {
        this.state = state;
        return this;
    }

    public ExpectedProtocolStatus setPhase(Trade.Phase phase) {
        this.phase = phase;
        return this;
    }

    public ExpectedProtocolStatus setDepositPublished(boolean depositPublished) {
        isDepositPublished = depositPublished;
        return this;
    }

    public ExpectedProtocolStatus setDepositConfirmed(boolean depositConfirmed) {
        isDepositConfirmed = depositConfirmed;
        return this;
    }

    public ExpectedProtocolStatus setFiatSent(boolean fiatSent) {
        isFiatSent = fiatSent;
        return this;
    }

    public ExpectedProtocolStatus setFiatReceived(boolean fiatReceived) {
        isFiatReceived = fiatReceived;
        return this;
    }

    public ExpectedProtocolStatus setPayoutPublished(boolean payoutPublished) {
        isPayoutPublished = payoutPublished;
        return this;
    }

    public ExpectedProtocolStatus setWithdrawn(boolean withdrawn) {
        isWithdrawn = withdrawn;
        return this;
    }

    public void init() {
        state = null;
        phase = null;
        isDepositPublished = false;
        isDepositConfirmed = false;
        isFiatSent = false;
        isFiatReceived = false;
        isPayoutPublished = false;
        isWithdrawn = false;
    }
}

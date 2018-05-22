package network.bisq.api.model;

import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;
import bisq.core.trade.closed.ClosedTradableManager;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

@Slf4j
public class ClosedTradableConverter {

    private final ClosedTradableManager closedTradableManager;

    @Inject
    public ClosedTradableConverter(ClosedTradableManager closedTradableManager) {
        this.closedTradableManager = closedTradableManager;
    }

    public ClosedTradableDetails convert(Tradable tradable) {
        final ClosedTradableDetails details = new ClosedTradableDetails();
        details.id = tradable.getId();
        details.amount = getAmout(tradable);
        details.date = tradable.getDate().getTime();
        details.direction = getDirection(tradable);
        details.currencyCode = tradable.getOffer().getCurrencyCode();
        details.price = getPrice(tradable);
        details.status = getStatus(tradable);
        details.volume = getVolume(tradable);
        return details;
    }

    private String getStatus(Tradable tradable) {
        if (tradable instanceof Trade) {
            Trade trade = (Trade) tradable;

            if (trade.isWithdrawn() || trade.isPayoutPublished()) {
                return Res.get("portfolio.closed.completed");
            } else if (trade.getDisputeState() == Trade.DisputeState.DISPUTE_CLOSED) {
                return Res.get("portfolio.closed.ticketClosed");
            } else {
                log.error("That must not happen. We got a pending state but we are in the closed trades list.");
                return trade.getState().toString();
            }
        } else if (tradable instanceof OpenOffer) {
            OpenOffer.State state = ((OpenOffer) tradable).getState();
            switch (state) {
                case AVAILABLE:
                case RESERVED:
                case CLOSED:
                    log.error("Invalid state {}", state);
                    return state.toString();
                case CANCELED:
                    return Res.get("portfolio.closed.canceled");
                case DEACTIVATED:
                    log.error("Invalid state {}", state);
                    return state.toString();
                default:
                    log.error("Unhandled state {}", state);
                    return state.toString();
            }
        }

        return null;
    }

    private OfferPayload.Direction getDirection(Tradable tradable) {
        final Offer offer = tradable.getOffer();
        return closedTradableManager.wasMyOffer(offer) ? offer.getDirection() : offer.getMirroredDirection();
    }

    private Long getVolume(Tradable item) {
        if (item instanceof Trade) {
            final Volume volume = ((Trade) item).getTradeVolume();
            return null == volume ? null : volume.getValue();
        }
        return null;
    }

    private Long getPrice(Tradable tradable) {
        if (tradable instanceof Trade)
            return ((Trade) tradable).getTradePrice().getValue();
        else {
            final Price price = tradable.getOffer().getPrice();
            return null == price ? null : price.getValue();
        }
    }

    private Long getAmout(Tradable item) {
        if (item != null && item instanceof Trade) {
            final Coin tradeAmount = ((Trade) item).getTradeAmount();
            return null == tradeAmount ? null : tradeAmount.getValue();
        }
        return null;
    }
}

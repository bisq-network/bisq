package bisq.httpapi.facade;

import bisq.core.trade.closed.ClosedTradableManager;

import bisq.httpapi.model.ClosedTradableConverter;
import bisq.httpapi.model.ClosedTradableDetails;

import javax.inject.Inject;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class ClosedTradableFacade {

    private final ClosedTradableConverter closedTradableConverter;
    private final ClosedTradableManager closedTradableManager;

    @Inject
    public ClosedTradableFacade(ClosedTradableConverter closedTradableConverter, ClosedTradableManager closedTradableManager) {
        this.closedTradableConverter = closedTradableConverter;
        this.closedTradableManager = closedTradableManager;
    }

    public List<ClosedTradableDetails> getClosedTradableList() {
        return closedTradableManager.getClosedTradables().stream()
                .sorted((o1, o2) -> o2.getDate().compareTo(o1.getDate()))
                .map(closedTradableConverter::convert)
                .collect(toList());
    }
}

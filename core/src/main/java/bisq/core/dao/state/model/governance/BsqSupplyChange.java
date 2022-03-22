package bisq.core.dao.state.model.governance;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class BsqSupplyChange {
    @Getter
    long time;

    @Getter
    long value;
}

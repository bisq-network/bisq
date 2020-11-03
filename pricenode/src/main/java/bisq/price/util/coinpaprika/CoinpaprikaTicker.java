package bisq.price.util.coinpaprika;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinpaprikaTicker {

    // All other json fields can be ignored, we don't need them

    private BigDecimal price;

}

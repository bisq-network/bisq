package bisq.price.util.coinpaprika;


import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinpaprikaMarketData {

    // All other json fields can be ignored, we don't need them

    private Map<String, CoinpaprikaTicker> quotes;

}

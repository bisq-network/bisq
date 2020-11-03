package bisq.price.util.coingecko;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinGeckoTicker {

    private String name;

    private String unit;

    private BigDecimal value;

    private String type;

}

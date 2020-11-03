package bisq.price.util.bitpay;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BitpayTicker {

    private String code;

    private String name;

    private BigDecimal rate;

}

package bisq.asset;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When a new PaymentAccount is created for given asset, this annotation tells UI to show user a disclaimer message
 * with requirements needed to be fulfilled when conducting trade given payment method.
 *
 * I.e. in case of Monero user must use official Monero GUI wallet or Monero CLI wallet with certain options enabled,
 * user needs to keep tx private key, tx hash, recipient's address, etc.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AltCoinAccountDisclaimer {

    /**
     * Translation key of the message to show, i.e. "account.altcoin.popup.xmr.msg"
     * @return translation key
     */
    String value();

}

package bisq.asset.coins;

 import bisq.asset.AltCoinAccountDisclaimer;
import bisq.asset.Coin;
import bisq.asset.EtherAddressValidator;
import bisq.asset.RegexAddressValidator;

 @AltCoinAccountDisclaimer("account.altcoin.popup.smilo.msg")
public class Smilo extends Coin {

     public Smilo() {

         super("Smilo", "XSM",  new EtherAddressValidator());


     }
}

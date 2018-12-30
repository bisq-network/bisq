package bisq.asset.coins;

import bisq.asset.AddressValidationResult;
import bisq.asset.Base58BitcoinAddressValidator;
import bisq.asset.Coin;
import bisq.asset.NetworkParametersAdapter;

public class Qbase extends Coin {
    public Qbase() {
        super("Qbase", "QBS", new Qbase.QbaseAddressValidator());
    }


    public static class QbaseAddressValidator extends Base58BitcoinAddressValidator {

        public QbaseAddressValidator() {
            super(new Qbase.QbaseParams());
        }

        @Override
        public AddressValidationResult validate(String address) {
            if (!address.matches("^[B][a-km-zA-HJ-NP-Z1-9]{25,34}$"))
                return AddressValidationResult.invalidStructure();

            return super.validate(address);
        }
    }


    public static class QbaseParams extends NetworkParametersAdapter {

        public QbaseParams() {
            addressHeader = 25;
            p2shHeader = 5;
            acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
        }
    }
}

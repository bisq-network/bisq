package bisq.common.monetary;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Maker;
import com.natpryce.makeiteasy.Property;
import org.bitcoinj.utils.Fiat;

import static com.natpryce.makeiteasy.MakeItEasy.a;

public class VolumeMaker {

    public static final Property<Volume, String> currencyCode = new Property<>();
    public static final Property<Volume, String> volumeString = new Property<>();

    public static final Instantiator<Volume> FiatVolume = lookup ->
            new Volume(Fiat.parseFiat(lookup.valueOf(currencyCode, "USD"), lookup.valueOf(volumeString,"100")));

    public static final Instantiator<Volume> AltcoinVolume = lookup ->
            new Volume(Altcoin.parseAltcoin(lookup.valueOf(currencyCode, "LTC"), lookup.valueOf(volumeString, "100")));

    public static final Maker<Volume> usdVolume = a(FiatVolume);
    public static final Maker<Volume> ltcVolume = a(AltcoinVolume);
}

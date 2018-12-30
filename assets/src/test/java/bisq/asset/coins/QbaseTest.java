package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class QbaseTest extends AbstractAssetTest {
    public QbaseTest() {
        super(new Qbase());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("BBrv1uUkQxpWayMvaVSw9Gr4X7CcdWUtcC");
        assertValidAddress("BNMFjkDk9qqcF2rtoAbqbqWiHa41GPkQ2G");
        assertValidAddress("B73WdFQXx8VGNg8h1BeJj6H2BEa1xrbtsT");
        assertValidAddress("BGq4DH2BnS4kFWuNNQqfmiDLZvjaWtvnWX");
        assertValidAddress("B9b8iTbVVcQrohrEnJ9ho4QUftHS3svB84");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("bBrv1uUkQxpWayMvaVSw9Gr4X7CcdWUtcC");
        assertInvalidAddress("B3rv1uUkQxpWayMvaVSw9Gr4X7CcdWUtcC");
        assertInvalidAddress("PXP75NnwDryYswQb9RaPFBchqLRSvBmDP");
        assertInvalidAddress("PKr3vQ7S");
    }
}

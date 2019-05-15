package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class CatalansCoinTest extends AbstractAssetTest {

    public CatalansCoinTest() {
        super(new CatalansCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("cat1GkrKKPqRxD762hoWdi5iFyy7Bsj99Db6NebJzsZKEGcUmk6FrJngR4YgXNk2n36kedFz8ypPUDShG127H4pj2beFhY3Ruv");
        assertValidAddress("cat16eiHGG4Yadi577tYUYhNkYz5N1gB1W9YGwkuuCfH7SWNMA4pwAHAVWsztaQFxQiDD35AQpGwGTsxab1CaRCk7pxur8oN24");
        assertValidAddress("cat161fvhRkfEacpeqyKRGT7VR9MGLxHVZmNyx35QvtJhKTTrhowHpjb88eurQin34WHSg7fXHvn2csbDo3V9e791putRRofoo");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("cat1Rxo2rYpNXD6r1P8ELvF3EUv9vAhYf2ec3V2YWkZbYM3t78tn67Pcjx5oFsYq6h4Pygm3955cF9p");
        assertInvalidAddress("");
        assertInvalidAddress("cat15Vbg5JNTq9ue5WXsx2NmeMyEz1DbFSpZDdAdTa9dLmmMdBN2BWLAoWjHSrHPc2GtcJcmtUW8KWqw5FnynDCyAJLSMe9caKh1");
        assertInvalidAddress("cat15c6yXRqN6j4ewuqy9NL7oUXhyXFZ8NUnYDSaTFhhbL2eJB24pgzZMNJceTtEhPFsqGWegZFXNJqEpfb4C2cy1iKUcPTygA7s9C#RoPOWRwpsx1F");
        assertInvalidAddress("cat1XPhcJzAZS85E9CfKzTJDMcQdxhAe1XnKhnSv14UEH8zFQeeGHfFicFVz9HjQNUDArKDzzVTdiTKMkxaqMi8y8yMiy5Ezzw8s3SBY");
        assertInvalidAddress("1jRo3rcp9fjdfjdSGpx");
        assertInvalidAddress("GDARp92UtmTWDjZatG8sduRockSteadyWasHere3atrHSXr9vJzjHq2TfPrjateDz9Wc8ZJKuDayqJ$%");
        assertInvalidAddress("F3xQ8Gv6xnvDhUrM57z71bfFvu9HeofXtXpZRLnrCN2s2cKvkQowrWjJTGz4676ymKvU4NzYT5Aadgsdhsdfhg4gfJwL2yhhkJ7");
    }
} 


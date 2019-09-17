package monero.wallet.model;

/**
 * Models the result of initializing a multisig wallet which results in the
 * multisig wallet's address xor another multisig hex to share with
 * participants to create the wallet.
 */
public class MoneroMultisigInitResult {

  private String address;
  private String multisigHex;
  
  public String getAddress() {
    return address;
  }
  
  public void setAddress(String address) {
    this.address = address;
  }
  
  public String getMultisigHex() {
    return multisigHex;
  }
  
  public void setMultisigHex(String multisigHex) {
    this.multisigHex = multisigHex;
  }
}

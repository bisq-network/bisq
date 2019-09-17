package monero.wallet.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base class for results from checking a transaction or reserve proof.
 */
public class MoneroCheck {

  public Boolean isGood;

  @JsonProperty("isGood")
  public Boolean isGood() {
    return isGood;
  }

  public void setIsGood(Boolean isGood) {
    this.isGood = isGood;
  }
}

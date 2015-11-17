package io.nucleo.net;

public enum PredefinedDisconnectReason implements DisconnectReason {
  TIMEOUT("due to timed out", false, true),
  CONNECTION_CLOSED("as ordered", true),
  RESET("due to remote reset (EOF)", false, true),
  UNKNOWN("for unknown reasons", false);

  private Boolean remote;
  private final boolean graceful;
  private final String description;

  private PredefinedDisconnectReason(String description, boolean graceful) {
    this.description = description;
    this.graceful = graceful;
  }

  private PredefinedDisconnectReason(String description, boolean graceful, boolean remote) {
    this.description = description;
    this.graceful = graceful;
    this.remote = remote;
  }

  public static PredefinedDisconnectReason createReason(PredefinedDisconnectReason reason, boolean remote) {
    reason.remote = remote;
    return reason;
  }

  @Override
  public boolean isGraceful() {
    return graceful;
  }

  @Override
  public boolean isRemote() {
    if (remote == null)
      return false;
    return remote;

  }

  public String toString() {
    StringBuilder bld = new StringBuilder("Connection closed ");
    if (remote != null)
      bld.append(remote ? "remotely " : "locally ");
    bld.append(description).append(" (");
    bld.append(graceful ? "graceful" : "irregular").append(" disconnect)");
    return bld.toString();

  }

}
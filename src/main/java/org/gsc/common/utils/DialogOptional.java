package org.gsc.common.utils;

import java.util.Optional;
import org.gsc.db.UndoStore.Dialog;

public final class DialogOptional {


  private Optional<Dialog> value;

  private DialogOptional() {
    this.value = Optional.empty();
  }

  public synchronized DialogOptional setValue(Dialog value) {
    if (!this.value.isPresent()) {
      this.value = Optional.of(value);
    }
    return this;
  }

  public synchronized boolean valid() {
    return value.isPresent();
  }

  public synchronized void reset() {
    value.ifPresent(Dialog::destroy);
    value = Optional.empty();
  }

}

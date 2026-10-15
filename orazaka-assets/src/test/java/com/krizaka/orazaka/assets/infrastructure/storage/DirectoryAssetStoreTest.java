package com.krizaka.orazaka.assets.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.orazaka.assets.domain.port.DurableAssetStore;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class DirectoryAssetStoreTest extends DurableAssetStoreContract {

  @Override
  DurableAssetStore store() {
    return new DirectoryAssetStore(work.resolve("store"));
  }

  @Test
  void aKeyCannotClimbOutOfTheStore() throws IOException {
    DurableAssetStore store = store();
    assertThatThrownBy(() -> store.put("../escape", file("a", "x")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

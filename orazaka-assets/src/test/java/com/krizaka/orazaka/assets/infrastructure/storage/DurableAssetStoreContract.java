package com.krizaka.orazaka.assets.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.orazaka.assets.domain.port.DurableAssetStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What every adapter of {@link DurableAssetStore} must do; each adapter's test extends this. */
abstract class DurableAssetStoreContract {

  @TempDir Path work;

  abstract DurableAssetStore store();

  @Test
  void aStoredFileComesBackByteForByte() throws IOException {
    Path source = file("a.bin", "sealed-envelope-bytes");
    store().put("t/x/u/1/jobs/j1/image.png", source);

    Path target = work.resolve("restored/image.png");
    assertThat(store().fetch("t/x/u/1/jobs/j1/image.png", target)).isTrue();
    assertThat(Files.readString(target)).isEqualTo("sealed-envelope-bytes");
  }

  @Test
  void anAbsentObjectIsNotAnError() throws IOException {
    Path target = work.resolve("none.png");
    assertThat(store().fetch("t/x/u/1/jobs/missing/image.png", target)).isFalse();
    assertThat(target).doesNotExist();
  }

  @Test
  void putReplacesThePreviousCopy() throws IOException {
    store().put("t/x/u/1/jobs/j2/f", file("a", "one"));
    store().put("t/x/u/1/jobs/j2/f", file("b", "two"));
    Path target = work.resolve("f");
    assertThat(store().fetch("t/x/u/1/jobs/j2/f", target)).isTrue();
    assertThat(Files.readString(target)).isEqualTo("two");
  }

  @Test
  void deletingAnObjectRemovesIt_andDeletingTwiceIsFine() throws IOException {
    store().put("t/x/u/1/jobs/j3/f", file("a", "x"));
    store().delete("t/x/u/1/jobs/j3/f");
    store().delete("t/x/u/1/jobs/j3/f");
    assertThat(store().fetch("t/x/u/1/jobs/j3/f", work.resolve("g"))).isFalse();
  }

  @Test
  void deletingAPrefixRemovesEverythingUnderIt_andNothingElse() throws IOException {
    store().put("t/x/u/1/jobs/j4/a", file("a", "a"));
    store().put("t/x/u/1/jobs/j4/b", file("b", "b"));
    store().put("t/x/u/1/jobs/j5/a", file("c", "c"));
    store().delete("t/x/u/1/jobs/j4/");
    assertThat(store().fetch("t/x/u/1/jobs/j4/a", work.resolve("o1"))).isFalse();
    assertThat(store().fetch("t/x/u/1/jobs/j4/b", work.resolve("o2"))).isFalse();
    assertThat(store().fetch("t/x/u/1/jobs/j5/a", work.resolve("o3"))).isTrue();
  }

  @Test
  void isEnabled() {
    assertThat(store().enabled()).isTrue();
  }

  Path file(String name, String content) throws IOException {
    Path path = work.resolve("src-" + name);
    Files.writeString(path, content);
    return path;
  }
}

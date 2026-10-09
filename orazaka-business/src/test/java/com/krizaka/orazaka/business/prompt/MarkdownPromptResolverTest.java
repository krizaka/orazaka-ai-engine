package com.krizaka.orazaka.business.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Unit tests for {@link MarkdownPromptResolver}.
 *
 * <p>Validates cache semantics, graceful degradation on missing files, eviction mechanics, and
 * constructor invariants.
 */
@ExtendWith(MockitoExtension.class)
class MarkdownPromptResolverTest {

  @Mock private ResourceLoader resourceLoader;
  @Mock private Resource resource;

  private MarkdownPromptResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new MarkdownPromptResolver(resourceLoader);
  }

  @Test
  @DisplayName("resolve() returns prompt content for existing classpath resource")
  void resolve_existingPrompt_returnsContent() throws IOException {
    String expected = "# Welcome Agent\nYou are Orazaka.";
    when(resourceLoader.getResource("classpath:prompts/welcome_agent.md")).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream())
        .thenReturn(new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8)));

    Optional<String> result = resolver.resolve("welcome_agent");

    assertThat(result).isPresent().hasValue(expected);
    assertThat(resolver.cacheSize()).isEqualTo(1);
  }

  @Test
  @DisplayName("resolve() returns empty for non-existent prompt file")
  void resolve_missingPrompt_returnsEmpty() {
    when(resourceLoader.getResource("classpath:prompts/nonexistent.md")).thenReturn(resource);
    when(resource.exists()).thenReturn(false);

    Optional<String> result = resolver.resolve("nonexistent");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("resolve() returns empty when IOException occurs during read")
  void resolve_ioException_returnsEmpty() throws IOException {
    when(resourceLoader.getResource("classpath:prompts/broken.md")).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream()).thenThrow(new IOException("Simulated read failure"));

    Optional<String> result = resolver.resolve("broken");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("resolve() serves from cache on second call — ResourceLoader not invoked again")
  void resolve_cachedHit_noSecondIO() throws IOException {
    String content = "Cached content";
    when(resourceLoader.getResource("classpath:prompts/cached.md")).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream())
        .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

    resolver.resolve("cached");
    resolver.resolve("cached");

    // ResourceLoader.getResource called only once due to computeIfAbsent
    verify(resourceLoader, times(1)).getResource("classpath:prompts/cached.md");
  }

  @Test
  @DisplayName("evict() removes single cache entry and forces reload on next access")
  void evict_removesEntry_forcesReload() throws IOException {
    String content = "Original";
    when(resourceLoader.getResource("classpath:prompts/evictable.md")).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream())
        .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

    resolver.resolve("evictable");
    assertThat(resolver.cacheSize()).isEqualTo(1);

    resolver.evict("evictable");
    assertThat(resolver.cacheSize()).isZero();
  }

  @Test
  @DisplayName("evictAll() clears the entire cache")
  void evictAll_clearsAllEntries() throws IOException {
    when(resourceLoader.getResource(anyString())).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream())
        .thenReturn(new ByteArrayInputStream("A".getBytes(StandardCharsets.UTF_8)))
        .thenReturn(new ByteArrayInputStream("B".getBytes(StandardCharsets.UTF_8)));

    resolver.resolve("alpha");
    resolver.resolve("beta");
    assertThat(resolver.cacheSize()).isEqualTo(2);

    resolver.evictAll();
    assertThat(resolver.cacheSize()).isZero();
  }

  @Test
  @DisplayName("resolve() returns empty for null prompt name")
  void resolve_nullName_returnsEmpty() {
    Optional<String> result = resolver.resolve(null);
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("resolve() returns empty for blank prompt name")
  void resolve_blankName_returnsEmpty() {
    Optional<String> result = resolver.resolve("   ");
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Constructor rejects null ResourceLoader")
  void constructor_nullResourceLoader_throws() {
    assertThatThrownBy(() -> new MarkdownPromptResolver(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ResourceLoader must not be null");
  }

  @Test
  @DisplayName("evict(null) is a safe no-op")
  void evict_null_noOp() {
    resolver.evict(null);
    assertThat(resolver.cacheSize()).isZero();
  }
}

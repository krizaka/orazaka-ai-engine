package com.krizaka.orazaka.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ResourceLoader;

class BusinessAutoConfigurationTest {

  @Test
  @DisplayName("Should create MarkdownPromptResolver bean successfully")
  void registersMarkdownPromptResolverBean() {
    var config = new BusinessAutoConfiguration();
    var resourceLoader = mock(ResourceLoader.class);
    var resolver = config.markdownPromptResolver(resourceLoader);
    assertThat(resolver).isNotNull();
  }
}

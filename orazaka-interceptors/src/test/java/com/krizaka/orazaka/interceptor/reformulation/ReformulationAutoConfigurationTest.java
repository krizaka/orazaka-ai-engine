package com.krizaka.orazaka.interceptor.reformulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.orazaka.core.application.pipeline.PipelineOptionsRegistry;
import com.krizaka.orazaka.core.infrastructure.config.CoreProperties;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

class ReformulationAutoConfigurationTest {

  @Test
  @DisplayName(
      "ReformulationAutoConfiguration registers refiner, router and Semantic router interceptor beans")
  void registersBeans() {
    ReformulationAutoConfiguration config = new ReformulationAutoConfiguration();
    CoreProperties props = mock(CoreProperties.class);
    PipelineOptionsRegistry optionsRegistry = mock(PipelineOptionsRegistry.class);
    Resource systemRefinement = mock(Resource.class);
    Resource contextEnvelope = mock(Resource.class);
    Resource routerSystem = mock(Resource.class);

    RefinerInterceptor refiner =
        config.refinerInterceptor(
            Collections.emptyMap(), props, optionsRegistry, systemRefinement, contextEnvelope);
    assertThat(refiner).isNotNull();

    RouterInterceptor router =
        config.routerInterceptor(Collections.emptyMap(), props, optionsRegistry, routerSystem);
    assertThat(router).isNotNull();

    SemanticRouterInterceptor semanticRouter = config.semanticRouterInterceptor();
    assertThat(semanticRouter).isNotNull();
  }
}

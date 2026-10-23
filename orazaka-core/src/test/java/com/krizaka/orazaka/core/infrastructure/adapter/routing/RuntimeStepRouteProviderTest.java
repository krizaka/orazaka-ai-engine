package com.krizaka.orazaka.core.infrastructure.adapter.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.orazaka.core.application.routing.PolicyStepRouter;
import com.krizaka.orazaka.core.domain.model.RoutingPolicy;
import com.krizaka.orazaka.core.domain.model.StepModelCandidate;
import com.krizaka.orazaka.core.domain.model.StepRoutingRequest;
import com.krizaka.orazaka.core.infrastructure.provider.DynamicModelRouter;
import com.krizaka.orazaka.core.infrastructure.provider.model.RoutedChatModel;
import com.krizaka.orazaka.persistence.domain.model.CatalogModelDto;
import com.krizaka.orazaka.persistence.domain.ports.inbound.CatalogModelManager;
import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.ObjectMapper;

class RuntimeStepRouteProviderTest {

  private final RuntimeConfigProvider config = mock(RuntimeConfigProvider.class);
  private final CatalogModelManager catalog = mock(CatalogModelManager.class);
  private final RuntimeStepRouteProvider provider =
      new RuntimeStepRouteProvider(config, catalog, new ObjectMapper());

  @ParameterizedTest
  @EnumSource(RoutingPolicy.class)
  void readsOnlyTheDeclaredPolicyInAdminOrder(RoutingPolicy policy) {
    when(config.getString("routing.text.generate." + policy.name(), null))
        .thenReturn("[\"secondary\", \"primary\"]");
    when(catalog.getAllModels())
        .thenReturn(
            List.of(
                model("primary", "local", "available"),
                model("secondary", "provider", "available")));

    var routes = provider.candidates("text.generate", policy);

    assertEquals(
        List.of("secondary", "primary"),
        routes.stream().map(StepModelCandidate::modelName).toList());
    assertFalse(routes.getFirst().sovereignLocal());
    assertTrue(routes.getLast().sovereignLocal());
  }

  @Test
  void missingPolicyDoesNotUseTheCatalogDefault() {
    assertTrue(provider.candidates("text.generate", RoutingPolicy.SOVEREIGN_LOCAL_ONLY).isEmpty());
  }

  @ParameterizedTest
  @EnumSource(RoutingPolicy.class)
  void connectionFailureUsesDeclaredSecondaryAndRecordsItsName(RoutingPolicy policy) {
    when(config.getString("routing.text.generate." + policy.name(), null))
        .thenReturn("[\"primary\",\"secondary\",\"hosted\"]");
    when(catalog.getAllModels())
        .thenReturn(
            List.of(
                model("primary", "local", "available"),
                model("secondary", "local", "available"),
                model("hosted", "provider", "available")));
    var models = mock(DynamicModelRouter.class);
    var primary = mock(ChatModel.class);
    var secondary = mock(ChatModel.class);
    var response = mock(ChatResponse.class);
    when(models.chatModel("primary")).thenReturn(primary);
    when(models.chatModel("secondary")).thenReturn(secondary);
    when(primary.call(any(Prompt.class)))
        .thenThrow(new RuntimeException(new java.net.ConnectException("Connection refused")));
    when(secondary.call(any(Prompt.class))).thenReturn(response);
    var routed = new RoutedChatModel(models);
    var selectedModels = new HashMap<String, String>();

    var result =
        new PolicyStepRouter(provider)
            .execute(
                new StepRoutingRequest("step-42", "text.generate", policy),
                selectedModels::put,
                name ->
                    routed.call(new Prompt("hi", ChatOptions.builder().model(name).build())));

    assertEquals(response, result);
    assertEquals("secondary", selectedModels.get("step-42"));
    verify(models, never()).chatModel("hosted");
  }

  @Test
  void undeclaredLocationAndUnavailableOrMissingModelsFailClosed() {
    when(config.getString("routing.text.generate.SOVEREIGN_LOCAL_ONLY", null))
        .thenReturn("[\"unknown-location\", \"planned\", \"missing\"]");
    when(catalog.getAllModels())
        .thenReturn(
            List.of(
                model("unknown-location", null, "available"),
                model("planned", "local", "planned")));

    var routes = provider.candidates("text.generate", RoutingPolicy.SOVEREIGN_LOCAL_ONLY);

    assertFalse(routes.getFirst().sovereignLocal());
    assertFalse(routes.get(1).available());
    assertFalse(routes.getLast().available());
  }

  @Test
  void adminEditsAreReadOnTheNextInvocation() {
    when(config.getString("routing.text.generate.COST_OPTIMIZED", null))
        .thenReturn("[\"primary\"]", "[\"secondary\", \"primary\"]");
    when(catalog.getAllModels())
        .thenReturn(List.of(model("primary", "local", "available")))
        .thenReturn(
            List.of(
                model("primary", "provider", "available"),
                model("secondary", "local", "available")));

    assertTrue(
        provider
            .candidates("text.generate", RoutingPolicy.COST_OPTIMIZED)
            .getFirst()
            .sovereignLocal());
    var updated = provider.candidates("text.generate", RoutingPolicy.COST_OPTIMIZED);
    assertEquals("secondary", updated.getFirst().modelName());
    assertFalse(updated.getLast().sovereignLocal());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"not-json", "null", "{}", "[1]", "[null]", "[\"\"]", "[\"a\", \"a\"]"})
  void invalidDeclarationsAreRejected(String declaration) {
    when(config.getString("routing.text.generate.LATENCY_FIRST", null)).thenReturn(declaration);

    assertThrows(
        IllegalStateException.class,
        () -> provider.candidates("text.generate", RoutingPolicy.LATENCY_FIRST));
  }

  private static CatalogModelDto model(String name, String runsOn, String status) {
    var model = mock(CatalogModelDto.class);
    when(model.modelName()).thenReturn(name);
    when(model.runsOn()).thenReturn(runsOn);
    when(model.status()).thenReturn(status);
    return model;
  }
}

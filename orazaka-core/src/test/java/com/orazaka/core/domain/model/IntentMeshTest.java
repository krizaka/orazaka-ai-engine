package com.orazaka.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IntentMeshTest {

  @Test
  void validConstruction() {
    var mesh = new IntentMesh(0.5, 0.3, 0.8);
    assertEquals(0.5, mesh.codeGenerationWeight());
    assertEquals(0.3, mesh.mediaInferenceWeight());
    assertEquals(0.8, mesh.generalChatWeight());
  }

  @Test
  void boundaryValues() {
    assertDoesNotThrow(() -> new IntentMesh(0.0, 0.0, 0.0));
    assertDoesNotThrow(() -> new IntentMesh(1.0, 1.0, 1.0));
  }

  @Test
  void negativeCodeWeight_throws() {
    assertThrows(IllegalArgumentException.class, () -> new IntentMesh(-0.1, 0.5, 0.5));
  }

  @Test
  void codeWeightOver1_throws() {
    assertThrows(IllegalArgumentException.class, () -> new IntentMesh(1.1, 0.5, 0.5));
  }

  @Test
  void negativeMediaWeight_throws() {
    assertThrows(IllegalArgumentException.class, () -> new IntentMesh(0.5, -0.1, 0.5));
  }

  @Test
  void negativeChatWeight_throws() {
    assertThrows(IllegalArgumentException.class, () -> new IntentMesh(0.5, 0.5, -0.1));
  }
}

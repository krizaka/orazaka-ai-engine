package com.orazaka.core.application.processing;

import static org.junit.jupiter.api.Assertions.*;

import com.orazaka.core.domain.model.IntentMesh;
import org.junit.jupiter.api.Test;

class IntentClassifierTest {

  private final IntentClassifier classifier = new IntentClassifier();

  @Test
  void nullInput_defaultsToChat() {
    IntentMesh result = classifier.classify(null);
    assertEquals(0.0, result.codeGenerationWeight());
    assertEquals(0.0, result.mediaInferenceWeight());
    assertEquals(1.0, result.generalChatWeight());
  }

  @Test
  void blankInput_defaultsToChat() {
    IntentMesh result = classifier.classify("   ");
    assertEquals(1.0, result.generalChatWeight());
  }

  @Test
  void codeKeyword_setsCodeWeight() {
    IntentMesh result = classifier.classify("Create a React component");
    assertEquals(0.95, result.codeGenerationWeight());
  }

  @Test
  void mediaKeyword_setsMediaWeight() {
    IntentMesh result = classifier.classify("Generate an image of sunset");
    assertEquals(0.90, result.mediaInferenceWeight());
  }

  @Test
  void hybridInput_setsBothWeights() {
    IntentMesh result = classifier.classify("Create a video player page");
    assertEquals(0.95, result.codeGenerationWeight());
    assertEquals(0.90, result.mediaInferenceWeight());
  }

  @Test
  void generalQuery_defaultsToChat() {
    IntentMesh result = classifier.classify("What is the meaning of life?");
    assertEquals(0.0, result.codeGenerationWeight());
    assertEquals(0.0, result.mediaInferenceWeight());
    assertEquals(1.0, result.generalChatWeight());
  }
}

package com.orazaka.core.application.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orazaka.core.domain.model.AdaptiveDag;
import com.orazaka.core.domain.model.IntentMesh;
import org.junit.jupiter.api.Test;

/**
 * Isolated unit testing suite verifying the ORAZAKA Core SIM-DAG compiler, intent matrix weights,
 * and adaptive hardware backpressure self-healing configurations.
 */
class DagCompilerTest {

  @Test
  void testIntentClassifierNormalChat() {
    IntentClassifier classifier = new IntentClassifier();
    IntentMesh mesh = classifier.classify("Hello there, how are you?");

    assertEquals(0.0, mesh.codeGenerationWeight());
    assertEquals(0.0, mesh.mediaInferenceWeight());
    assertEquals(1.0, mesh.generalChatWeight());
  }

  @Test
  void testIntentClassifierHybridMesh() {
    IntentClassifier classifier = new IntentClassifier();
    // Prompt targets both Next.js code and local C++ media video rendering
    IntentMesh mesh = classifier.classify("Create a nextjs-app and generate a demo video of it.");

    assertEquals(0.95, mesh.codeGenerationWeight());
    assertEquals(0.90, mesh.mediaInferenceWeight());
    assertEquals(0.1, mesh.generalChatWeight());
  }

  @Test
  void testDagCompilerLocalSafetyRoute() {
    IntentClassifier classifier = new IntentClassifier();
    IntentMesh mesh = classifier.classify("Create a nextjs-app and generate a demo video of it.");

    DagCompiler compiler = new DagCompiler();
    // simulated memory usage of 70% (below 85% backpressure threshold)
    AdaptiveDag dag = compiler.compile(mesh, 70.0);

    assertTrue(dag.tasks().contains("feature-to-code"));
    assertTrue(dag.tasks().contains("local-media-generation"));
    assertTrue(dag.dependencies().contains("local-media-generation -> feature-to-code"));
    assertFalse(dag.offloaded());
    assertFalse(dag.throttled());
  }

  @Test
  void testDagCompilerBackpressureMitigation() {
    IntentClassifier classifier = new IntentClassifier();
    IntentMesh mesh = classifier.classify("Create a nextjs-app and generate a demo video of it.");

    DagCompiler compiler = new DagCompiler();
    // simulated memory usage of 90% (above 85% threshold)
    AdaptiveDag dag = compiler.compile(mesh, 90.0);

    assertTrue(dag.tasks().contains("feature-to-code"));
    assertTrue(dag.tasks().contains("local-media-generation"));
    assertTrue(dag.offloaded()); // Rerouted code scaffolding task
    assertTrue(dag.throttled()); // Locked local media queue
  }
}

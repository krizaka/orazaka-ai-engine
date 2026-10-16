package com.krizaka.orazaka.business.architecture;

import static com.krizaka.orazaka.test.architecture.GovernanceRules.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.krizaka.orazaka.test.architecture.GovernanceRules;
import com.krizaka.orazaka.test.architecture.PackPurityRules;
import com.krizaka.orazaka.test.architecture.SourceFileScanner;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Governance (naming + structural hygiene) for the orazaka-business module, reusing the shared
 * {@link com.krizaka.orazaka.test.architecture.GovernanceRules} so the framework enforces one
 * contract.
 *
 * <p>Note: business wires its beans through explicit {@code @Bean} methods in {@code
 * BusinessAutoConfiguration} (no classpath scanning), so its {@code *Impl} classes are public on
 * purpose — [ERR-105] (package-private impls) is a scanning-module rule and does not apply here.
 * External modules still couple only to the {@code api} interfaces.
 */
class BusinessGovernanceTest {

  /**
   * The rules below are repository-wide, not module-scoped: the worst pack coupling lives in the
   * Python media worker, which is in no Maven reactor, so a per-module scan could never see it.
   */
  private static final Path REPOSITORY_ROOT =
      PackPurityRules.locateRepositoryRoot(Path.of(System.getProperty("user.dir")));

  private static final String PKG = "com.krizaka.orazaka.business";
  private static JavaClasses businessClasses;

  @BeforeAll
  static void importClasses() {
    businessClasses = importProductionClasses(PKG);
  }

  @Test
  @DisplayName("[ERR-103] One top-level class per file")
  void oneClassPerFile() {
    assertDoesNotThrow(
        () -> {
          assertOneTopLevelClassPerFile(businessClasses, PKG);
        });
  }

  @Test
  @DisplayName("[ERR-104] No redundant 'Orazaka' prefix")
  void noRedundantPrefix() {
    assertDoesNotThrow(
        () -> {
          assertNoRedundantPrefix(businessClasses, PKG);
        });
  }

  @Test
  @DisplayName("[ERR-112] No web controllers outside router")
  void noWebControllers() {
    assertDoesNotThrow(
        () -> {
          assertNoWebControllers(businessClasses, PKG);
        });
  }

  @Test
  @DisplayName("[ERR-130] Domain holds no transport DTOs (*Request/*Response)")
  void domainHasNoTransportDtos() {
    assertDoesNotThrow(
        () -> {
          assertDomainHasNoTransportDtos(businessClasses, PKG + ".domain");
        });
  }

  @Test
  @DisplayName("[ADR-009] Instance fields in concrete classes are private")
  void fieldsArePrivate() {
    assertDoesNotThrow(
        () -> {
          assertFieldsPrivate(businessClasses);
        });
  }

  @Test
  @DisplayName("[ADR-007] Collection fields are private final")
  void collectionFieldsPrivateFinal() {
    assertDoesNotThrow(
        () -> {
          assertCollectionFieldsPrivateFinal(businessClasses);
        });
  }

  @Test
  @DisplayName("[GOV-001] No anonymous classes in production")
  void noAnonymousClasses() {
    assertDoesNotThrow(
        () -> {
          assertNoAnonymousClasses(businessClasses, PKG);
        });
  }

  @Test
  @DisplayName("[GOV-004] No standard streams (use SLF4J)")
  void noStandardStreams() {
    assertDoesNotThrow(
        () -> {
          assertNoStandardStreams(businessClasses);
        });
  }

  @Test
  @DisplayName("[PACK-002] no pack, studio or pack-capability key is a literal in engine code")
  void noPackKeyLiteralsInEngineCode() {
    assertDoesNotThrow(
        () -> {
          GovernanceRules.assertNoPackKeyLiterals(REPOSITORY_ROOT);
        });
  }

  @Test
  @DisplayName("[PACK-003] engine code never branches on a pack identifier")
  void noPackKeyConditionalsInEngineCode() {
    assertDoesNotThrow(
        () -> {
          GovernanceRules.assertNoPackKeyConditionals(REPOSITORY_ROOT);
        });
  }

  @Test
  @DisplayName("[EXEC-001] every in-process capability's handler_key has an executor")
  void everyCapabilityHasAnExecutor() {
    assertDoesNotThrow(
        () -> {
          GovernanceRules.assertEveryCapabilityHasAnExecutor(REPOSITORY_ROOT);
        });
  }

  @Test
  @DisplayName("[EXEC-002] every capability's routing_key is drained by a declared worker")
  void everyCapabilityIsDrained() {
    assertDoesNotThrow(
        () -> {
          GovernanceRules.assertEveryCapabilityIsDrained(REPOSITORY_ROOT);
        });
  }

  @Test
  @DisplayName("[ERR-113] No Environment injection in production beans")
  void noEnvironmentInjection() {
    assertDoesNotThrow(
        () -> {
          SourceFileScanner.assertNoEnvironmentInjection(Path.of("src", "main", "java"));
        });
  }
}

package com.orazaka.persistence.bridge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.orazaka.test.architecture.GovernanceRules;
import com.orazaka.test.architecture.SourceFileScanner;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture governance for {@code orazaka-persistence-bridge}: the pack-private {@code @Service}
 * adapters that implement the core's DB-backed outbound ports by delegating to the persistence
 * inbound ports. This is a deliberately flat adapter package (no hexagonal domain/application/
 * infrastructure sub-layers), so it reuses only the layout-agnostic shared {@link GovernanceRules}
 * plus the bridge-specific naming/visibility invariants below.
 */
class PersistenceBridgeGovernanceTest {

  private static final String BASE = "com.orazaka.persistence.bridge";

  private final JavaClasses classes = GovernanceRules.importProductionClasses(BASE);

  @Test
  @DisplayName("[ERR-103] one top-level class per file")
  void oneTopLevelClassPerFile() {
    GovernanceRules.assertOneTopLevelClassPerFile(classes, BASE);
  }

  @Test
  @DisplayName("[ERR-104] no redundant Orazaka prefix")
  void noRedundantPrefix() {
    GovernanceRules.assertNoRedundantPrefix(classes, BASE);
  }

  @Test
  @DisplayName("[ERR-107] mappers are final + package-private")
  void mappersFinalAndPackagePrivate() {
    GovernanceRules.assertMappersFinal(classes, BASE);
    GovernanceRules.assertMappersPackagePrivate(classes, BASE);
  }

  @Test
  @DisplayName("[ADR-009] instance fields are private")
  void fieldsPrivate() {
    GovernanceRules.assertFieldsPrivate(classes);
  }

  @Test
  @DisplayName("[GOV-005] no @Autowired field injection")
  void noFieldInjection() {
    GovernanceRules.assertNoFieldInjection(classes, BASE);
  }

  @Test
  @DisplayName("[GOV-001] no anonymous classes")
  void noAnonymousClasses() {
    GovernanceRules.assertNoAnonymousClasses(classes, BASE);
  }

  @Test
  @DisplayName("[GOV-004] no System.out/err")
  void noStandardStreams() {
    GovernanceRules.assertNoStandardStreams(classes);
  }

  @Test
  @DisplayName("[ERR-112] no web controllers in a library")
  void noWebControllers() {
    GovernanceRules.assertNoWebControllers(classes, BASE);
  }

  @Test
  @DisplayName("[ERR-130] bridge holds only *Adapter + *Mapper — one package, one component kind")
  void onlyAdaptersAndMappers() {
    classes()
        .that()
        .resideInAPackage(BASE + "..")
        .and()
        .areTopLevelClasses()
        .should()
        .haveSimpleNameEndingWith("Adapter")
        .orShould()
        .haveSimpleNameEndingWith("Mapper")
        .because(
            "the persistence-bridge holds core outbound-port adapters (*Adapter) and their mappers"
                + " (*Mapper) only [ERR-130]")
        .check(classes);
  }

  @Test
  @DisplayName(
      "[ERR-105] adapters are package-private (wired by component scan through their port)")
  void adaptersPackagePrivate() {
    classes()
        .that()
        .resideInAPackage(BASE + "..")
        .and()
        .haveSimpleNameEndingWith("Adapter")
        .should()
        .notBePublic()
        .because(
            "bridge adapters are discovered by the host service's component scan and consumed only"
                + " through their core outbound port — never part of a public API [ERR-105]")
        .check(classes);
  }

  @Test
  @DisplayName("[ERR-113] No Environment injection in production beans")
  void noEnvironmentInjection() {
    SourceFileScanner.assertNoEnvironmentInjection(Path.of("src", "main", "java"));
  }
}

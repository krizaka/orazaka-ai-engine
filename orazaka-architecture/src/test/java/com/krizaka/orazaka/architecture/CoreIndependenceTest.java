package com.krizaka.orazaka.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * ADR-092 §4 rule 3, orazaka-ai-engine#122: the core knows no pack, no studio and no product. What
 * a pack or a studio needs is a port of the core, implemented at the edge; a change to a pack lands
 * with no diff in {@code orazaka-core}. Strict, not a ratchet: the core has no such dependency
 * today and never gets one.
 */
class CoreIndependenceTest {

  @Test
  void coreDependsOnNoPackStudioOrProduct() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.orazaka");

    noClasses()
        .that()
        .resideInAPackage("com.krizaka.orazaka.core..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.krizaka.orazaka.pack..",
            "com.krizaka.orazaka.packs..",
            "com.krizaka.orazaka.studio..",
            "com.krizaka.orazaka.studios..",
            "com.krizaka.orazaka.tools..",
            "com.krizaka.orazaka.interceptor..",
            "com.krizaka.orochia..",
            "com.krizaka.yasakia..",
            "com.krizaka.yomizaka..",
            "com.krizaka.yororis..",
            "com.krizaka.kachizaka..",
            "com.krizaka.akinaza..")
        .because(
            "the core is the engine; packs, studios and products depend on it, never the reverse")
        .check(classes);
  }
}

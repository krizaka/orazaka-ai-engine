package com.krizaka.orazaka.tools.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.orazaka.tools.application.port.law.RuleProposalStore;
import com.krizaka.orazaka.tools.application.service.RuleProposalService;
import com.krizaka.orazaka.tools.architecture.fixture.ProposalWriterThatSkipsTheService;
import com.krizaka.orazaka.tools.domain.model.law.RuleProposalVet;
import com.krizaka.orazaka.tools.domain.model.law.VettedProposal;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A proposal is never written without passing {@code vet} (ADR-089 §3): the store takes only a
 * {@link VettedProposal}, only {@link RuleProposalVet} makes one, and only {@link
 * RuleProposalService} reaches the store. Each rule is also run on a planted violation, so it is
 * known to fail when it should.
 */
class RuleProposalRulesTest {

  private static JavaClasses production;

  @BeforeAll
  static void importClasses() {
    production =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.orazaka.tools");
  }

  /** [LAW-003] Only the vet constructs a vetted proposal. */
  static ArchRule onlyTheVetMakesAVettedProposal() {
    return noClasses()
        .that()
        .doNotHaveFullyQualifiedName(RuleProposalVet.class.getName())
        .should()
        .callConstructorWhere(target(VettedProposal.class))
        .because(
            "a rule_proposal row is written only from a VettedProposal; one made outside"
                + " RuleProposalVet would be a proposal that skipped vet [LAW-003]");
  }

  /** [LAW-004] Only the proposal service writes to the store. */
  static ArchRule onlyTheServiceWritesProposals() {
    return noClasses()
        .that()
        .doNotHaveFullyQualifiedName(RuleProposalService.class.getName())
        .should()
        .callMethodWhere(intoTheStoreFromOutside())
        .because(
            "RuleProposalService is the one door to rule_proposal: every write and read goes"
                + " through it, so no route can skip its checks [LAW-004]");
  }

  /**
   * A call into a store from a class that is not itself a store (its own private helpers are not
   * doors).
   */
  private static DescribedPredicate<JavaCall<?>> intoTheStoreFromOutside() {
    return DescribedPredicate.describe(
        "target is RuleProposalStore, called from outside a store",
        call ->
            call.getTargetOwner().isAssignableTo(RuleProposalStore.class)
                && !call.getOriginOwner().isAssignableTo(RuleProposalStore.class));
  }

  private static DescribedPredicate<JavaCall<?>> target(Class<?> owner) {
    return DescribedPredicate.describe(
        "target is " + owner.getSimpleName(), call -> call.getTargetOwner().isAssignableTo(owner));
  }

  @Test
  @DisplayName("[LAW-003] only RuleProposalVet constructs a VettedProposal")
  void onlyTheVetMakesAVettedProposalInProduction() {
    onlyTheVetMakesAVettedProposal().check(production);
  }

  @Test
  @DisplayName("[LAW-004] only RuleProposalService calls RuleProposalStore")
  void onlyTheServiceWritesProposalsInProduction() {
    onlyTheServiceWritesProposals().check(production);
  }

  @Test
  @DisplayName("[LAW-003] and [LAW-004] fail on their planted violations")
  void theRulesFailOnTheirPlantedViolations() throws ClassNotFoundException {
    JavaClasses planted =
        new ClassFileImporter()
            .importClasses(
                Class.forName("com.krizaka.orazaka.tools.domain.model.law.PlantedVetBypass"),
                ProposalWriterThatSkipsTheService.class);

    assertThatThrownBy(() -> onlyTheVetMakesAVettedProposal().check(planted))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("PlantedVetBypass");
    assertThatThrownBy(() -> onlyTheServiceWritesProposals().check(planted))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("ProposalWriterThatSkipsTheService");
  }
}

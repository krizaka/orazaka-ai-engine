package com.krizaka.orazaka.tools.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the law watcher (ADR-089, krizaka/orazaka#400). Off unless {@code enabled} is set.
 *
 * @param enabled whether the scheduled watch runs
 * @param cron the schedule, Spring cron (sec min hour day month weekday); weekly by default
 * @param quebec the Données Québec snapshot
 * @param github the rulesets repository and the App token that opens the review requests
 * @param targets the articles followed; the defaults are the C.c.Q. lease articles, the QST law
 *     (art. 16 and 201) and the Loi 25 instrument, which no pack cites by article yet
 */
@ConfigurationProperties(prefix = "orazaka.law.watcher")
public record LawWatchProperties(
    boolean enabled, String cron, Quebec quebec, GitHub github, List<Scope> targets) {

  private static final List<Scope> DEFAULT_TARGETS =
      List.of(
          new Scope("QC", "CCQ-1991", List.of("1851-2000")),
          new Scope("QC", "T-0.1", List.of("16", "201")),
          new Scope("QC", "P-39.1", List.of()));

  public LawWatchProperties {
    cron = cron == null || cron.isBlank() ? "0 0 6 * * MON" : cron;
    quebec = quebec == null ? new Quebec(null) : quebec;
    github = github == null ? new GitHub(null, null, null, null) : github;
    targets = targets == null || targets.isEmpty() ? DEFAULT_TARGETS : List.copyOf(targets);
  }

  /**
   * The Données Québec snapshot.
   *
   * @param datasetUrl the URL of the statutes zip; the current snapshot by default
   */
  public record Quebec(String datasetUrl) {

    public Quebec {
      datasetUrl =
          datasetUrl == null || datasetUrl.isBlank()
              ? "https://www.donneesquebec.ca/recherche/dataset/c8433300-f752-4815-8ea2-69cad416dd80/resource/d94786e8-7b04-4ed8-a2ac-8a148b7969d3/download/20261021_lois.zip"
              : datasetUrl;
    }
  }

  /**
   * The rulesets repository.
   *
   * @param token the GitHub App installation token of krizaka-agents; required when enabled
   * @param owner the owner, {@code krizaka} by default
   * @param repository the repository, {@code orazaka-packs} by default
   * @param baseBranch the branch requests are opened against, {@code main} by default
   */
  public record GitHub(String token, String owner, String repository, String baseBranch) {

    public GitHub {
      owner = owner == null || owner.isBlank() ? "krizaka" : owner;
      repository = repository == null || repository.isBlank() ? "orazaka-packs" : repository;
      baseBranch = baseBranch == null || baseBranch.isBlank() ? "main" : baseBranch;
    }
  }

  /**
   * One followed instrument.
   *
   * @param jurisdiction {@code CA} or {@code QC}
   * @param instrument the instrument code
   * @param refs the article references, or ranges such as {@code 1851-2000}
   */
  public record Scope(String jurisdiction, String instrument, List<String> refs) {

    public Scope {
      refs = refs == null ? List.of() : List.copyOf(refs);
    }
  }
}

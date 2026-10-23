package com.krizaka.orazaka.tools.application.service;

import com.krizaka.orazaka.core.domain.model.research.Citation;
import com.krizaka.orazaka.core.domain.model.research.Claim;
import com.krizaka.orazaka.core.domain.model.research.ClaimReason;
import com.krizaka.orazaka.core.domain.model.research.ClaimStatus;
import com.krizaka.orazaka.core.domain.model.research.GroundedAnswer;
import com.krizaka.orazaka.core.domain.model.research.QuoteNormalizer;
import com.krizaka.orazaka.core.domain.model.research.VerifyMode;
import com.krizaka.orazaka.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.orazaka.tools.domain.model.research.FetchedDocument;
import com.krizaka.orazaka.tools.domain.model.research.ReferenceScanner;
import com.krizaka.orazaka.tools.domain.port.ResearchStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * The deterministic verifier of ADR-087 §3. For each claim and each of its citations: the document
 * the citation names (URL + content hash) must be one research read, and the quote — at most {@code
 * research.quote.max-chars} — must appear in it once both are normalised ({@link QuoteNormalizer}:
 * NFC, case, French typography). A claim with one such citation is {@code VERIFIED}; any other is
 * {@code TO_VALIDATE} with its reason — or dropped in {@code STRICT}. Then every URL, repository
 * and statute stated in the prose that no verified claim covers becomes a {@code TO_VALIDATE} line
 * of its own ({@code UNLISTED_REFERENCE}). A claim is never rewritten: only its status moves.
 */
@Service
public class CitationVerificationService implements CitationVerifier {

  private final ResearchStore store;
  private final RuntimeConfigProvider config;

  /**
   * Wires the verifier.
   *
   * @param store the documents research read
   * @param config the settings ({@code research.quote.max-chars})
   */
  public CitationVerificationService(ResearchStore store, RuntimeConfigProvider config) {
    this.store = Objects.requireNonNull(store, "ResearchStore cannot be null");
    this.config = Objects.requireNonNull(config, "RuntimeConfigProvider cannot be null");
  }

  @Override
  public GroundedAnswer verify(GroundedAnswer answer, VerifyMode mode) {
    int maxQuote = config.getInt("research.quote.max-chars", 300);
    List<Claim> checked = new ArrayList<>();
    for (Claim claim : answer.claims()) {
      checked.add(check(claim, maxQuote));
    }
    List<Claim> verified =
        checked.stream().filter(c -> c.status() == ClaimStatus.VERIFIED).toList();
    List<Claim> result = new ArrayList<>(mode == VerifyMode.STRICT ? verified : checked);
    for (String reference : ReferenceScanner.references(answer.answer())) {
      if (!coveredBy(reference, verified)) {
        result.add(
            new Claim(
                reference, ClaimStatus.TO_VALIDATE, ClaimReason.UNLISTED_REFERENCE, List.of()));
      }
    }
    String prose =
        mode == VerifyMode.STRICT
            ? String.join(" ", verified.stream().map(Claim::text).toList())
            : answer.answer();
    return new GroundedAnswer(prose, result, answer.sources());
  }

  private Claim check(Claim claim, int maxQuote) {
    if (claim.citations().isEmpty()) {
      return downgrade(claim, ClaimReason.NO_SOURCE);
    }
    ClaimReason reason = ClaimReason.URL_UNRESOLVED;
    for (Citation citation : claim.citations()) {
      Optional<FetchedDocument> read = store.document(citation.url(), citation.contentHash());
      if (read.isEmpty()) {
        continue;
      }
      reason = ClaimReason.QUOTE_NOT_FOUND;
      if (citation.quote().length() <= maxQuote
          && QuoteNormalizer.contains(read.get().text(), citation.quote())) {
        return new Claim(claim.text(), ClaimStatus.VERIFIED, null, claim.citations());
      }
    }
    return downgrade(claim, reason);
  }

  private static Claim downgrade(Claim claim, ClaimReason reason) {
    return new Claim(claim.text(), ClaimStatus.TO_VALIDATE, reason, claim.citations());
  }

  private static final Pattern NUMBERS = Pattern.compile("\\d[\\d.-]*");

  /**
   * A reference is covered when a verified claim states it or cites it — a statute by its numbers
   * (« art. 1904 » is covered by a verified « L'article 1904 … »), a URL or a repository by its
   * text.
   */
  private static boolean coveredBy(String reference, List<Claim> verified) {
    String ref = QuoteNormalizer.normalize(reference);
    List<String> numbers = new ArrayList<>();
    Matcher m = NUMBERS.matcher(ref);
    while (m.find()) {
      numbers.add(m.group());
    }
    boolean statute = !ref.startsWith("http") && !ref.contains("/") && !numbers.isEmpty();
    for (Claim claim : verified) {
      String text = QuoteNormalizer.normalize(claim.text());
      if (text.contains(ref) || (statute && numbers.stream().allMatch(text::contains))) {
        return true;
      }
      for (Citation c : claim.citations()) {
        if (QuoteNormalizer.normalize(c.url()).startsWith(ref)
            || ref.startsWith(QuoteNormalizer.normalize(c.url()))) {
          return true;
        }
        if (QuoteNormalizer.normalize(c.quote()).contains(ref)) {
          return true;
        }
      }
    }
    return false;
  }
}

package com.krizaka.orazaka.interceptor.validation;

import com.krizaka.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.orazaka.core.domain.model.PromptContext;
import com.krizaka.orazaka.core.domain.model.research.Citation;
import com.krizaka.orazaka.core.domain.model.research.Claim;
import com.krizaka.orazaka.core.domain.model.research.ClaimStatus;
import com.krizaka.orazaka.core.domain.model.research.GroundedAnswer;
import com.krizaka.orazaka.core.domain.model.research.GroundedSource;
import com.krizaka.orazaka.core.domain.model.research.VerifyMode;
import com.krizaka.orazaka.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The verifier stage of grounded research (ADR-087 §3): an answer in the citation shape is checked
 * claim by claim against the documents research read <b>for this turn</b> — the ones the grounding
 * stage recorded in the context ({@link PromptContext#researchRead()}) — by the {@link
 * CitationVerifier}, before it is served; the served answer is the one the verifier renders from
 * the claims, never the model's prose. Any other answer passes through untouched — this interceptor
 * only reads what it can verify. The mode is {@code research.verify.mode} ({@code MARK} by
 * default), read at each answer.
 *
 * <p>Its {@code pipeline_interceptor_config} row ships disabled; the {@code grounding} flag of
 * slice 5 turns it on per request.
 */
public class CitationVerifierInterceptor implements PromptContextInterceptor {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final CitationVerifier verifier;
  private final RuntimeConfigProvider config;

  /**
   * Wires the stage.
   *
   * @param verifier the deterministic verifier
   * @param config the settings
   */
  public CitationVerifierInterceptor(CitationVerifier verifier, RuntimeConfigProvider config) {
    this.verifier = Objects.requireNonNull(verifier, "CitationVerifier cannot be null");
    this.config = Objects.requireNonNull(config, "RuntimeConfigProvider cannot be null");
  }

  @Override
  public String screenResponse(PromptContext context, String responseText) {
    JsonNode root = parse(responseText);
    if (root == null
        || !root.isObject()
        || !root.path("claims").isArray()
        || !root.path("answer").isString()) {
      return responseText;
    }
    VerifyMode mode =
        "STRICT".equalsIgnoreCase(config.getString("research.verify.mode", "MARK"))
            ? VerifyMode.STRICT
            : VerifyMode.MARK;
    return write(verifier.verify(read(root), mode, context.researchRead()));
  }

  private static JsonNode parse(String text) {
    if (text == null || text.isBlank() || text.strip().charAt(0) != '{') {
      return null;
    }
    try {
      return MAPPER.readTree(text);
    } catch (JacksonException notJson) {
      return null;
    }
  }

  static GroundedAnswer read(JsonNode root) {
    List<Claim> claims = new ArrayList<>();
    for (JsonNode c : root.path("claims")) {
      List<Citation> citations = new ArrayList<>();
      for (JsonNode cite : c.path("citations")) {
        citations.add(
            new Citation(
                cite.path("url").asString(""),
                cite.path("quote").asString(""),
                cite.path("contentHash").asString(""),
                cite.path("fetchedAt").asString("")));
      }
      // The model's own status is not trusted: the verifier sets it.
      claims.add(new Claim(c.path("text").asString(""), ClaimStatus.TO_VALIDATE, null, citations));
    }
    List<GroundedSource> sources = new ArrayList<>();
    for (JsonNode s : root.path("sources")) {
      sources.add(
          new GroundedSource(
              s.path("url").asString(""),
              s.path("title").asString(""),
              s.path("sourceKey").asString("")));
    }
    return new GroundedAnswer(root.path("answer").asString(""), claims, sources);
  }

  static String write(GroundedAnswer answer) {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("answer", answer.answer());
    ArrayNode claims = root.putArray("claims");
    for (Claim claim : answer.claims()) {
      ObjectNode c = claims.addObject();
      c.put("text", claim.text());
      c.put("status", claim.status().name());
      if (claim.reason() != null) {
        c.put("reason", claim.reason().name());
      }
      ArrayNode citations = c.putArray("citations");
      for (Citation cite : claim.citations()) {
        ObjectNode n = citations.addObject();
        n.put("url", cite.url());
        n.put("quote", cite.quote());
        n.put("contentHash", cite.contentHash());
        n.put("fetchedAt", cite.fetchedAt());
      }
    }
    ArrayNode sources = root.putArray("sources");
    for (GroundedSource s : answer.sources()) {
      sources
          .addObject()
          .put("url", s.url())
          .put("title", s.title())
          .put("sourceKey", s.sourceKey());
    }
    return root.toString();
  }
}

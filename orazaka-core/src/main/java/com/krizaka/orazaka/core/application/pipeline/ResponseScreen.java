package com.krizaka.orazaka.core.application.pipeline;

import com.krizaka.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.orazaka.core.domain.model.PromptContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the output screens ({@link PromptContextInterceptor#screenResponse}) on an answer about to
 * be served — a fresh model answer and a cached one take exactly the same path.
 *
 * <p>A refusal ({@link PipelineShortCircuitException}) always propagates. Any other failure follows
 * the posture the interceptor declared (ADR-064): a control ({@code failsClosed}) stops the turn,
 * an enrichment is logged and the answer passes through that screen unchanged.
 */
public final class ResponseScreen {

  private static final Logger logger = LoggerFactory.getLogger(ResponseScreen.class);

  private ResponseScreen() {}

  /**
   * Screens an answer.
   *
   * @param interceptors the engine's interceptors, in order
   * @param context the turn's final prompt context (null when no pipeline ran: nothing to read)
   * @param responseText the answer about to be served
   * @return the text to serve
   * @throws PipelineShortCircuitException when a screen refuses the answer
   */
  public static String apply(
      List<PromptContextInterceptor> interceptors, PromptContext context, String responseText) {
    if (context == null || responseText == null) {
      return responseText;
    }
    String text = responseText;
    for (PromptContextInterceptor interceptor : interceptors) {
      try {
        String screened = interceptor.screenResponse(context, text);
        text = screened == null ? text : screened;
      } catch (PipelineShortCircuitException refusal) {
        throw refusal;
      } catch (RuntimeException failure) {
        if (interceptor.failsClosed()) {
          throw failure;
        }
        logger.error(
            "Output screen '{}' failed — serving the answer as it was.",
            interceptor.getClass().getSimpleName(),
            failure);
      }
    }
    return text;
  }

  /**
   * Tells every interceptor a fresh answer passed the screens and is served ({@code
   * responseServed}). A failure is logged and never reaches the user.
   */
  public static void served(
      List<PromptContextInterceptor> interceptors, PromptContext context, String servedText) {
    if (context == null || servedText == null) {
      return;
    }
    for (PromptContextInterceptor interceptor : interceptors) {
      try {
        interceptor.responseServed(context, servedText);
      } catch (RuntimeException failure) {
        logger.warn(
            "responseServed of '{}' failed — the turn is unaffected.",
            interceptor.getClass().getSimpleName(),
            failure);
      }
    }
  }
}

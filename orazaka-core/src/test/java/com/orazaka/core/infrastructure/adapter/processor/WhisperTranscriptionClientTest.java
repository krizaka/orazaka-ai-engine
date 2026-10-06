package com.orazaka.core.infrastructure.adapter.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orazaka.core.application.processing.ProcessedAudioPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class WhisperTranscriptionClientTest {

  private RestClient.Builder restClientBuilder;
  private RestClient restClient;
  private ObjectMapper objectMapper;
  private WhisperTranscriptionClient client;

  @BeforeEach
  void setUp() {
    restClientBuilder = mock(RestClient.Builder.class);
    restClient = mock(RestClient.class);
    objectMapper = new ObjectMapper();

    when(restClientBuilder.clone()).thenReturn(restClientBuilder);
    when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
    when(restClientBuilder.requestFactory(any())).thenReturn(restClientBuilder);
    when(restClientBuilder.build()).thenReturn(restClient);

    client = new WhisperTranscriptionClient(restClientBuilder, objectMapper);
  }

  @Test
  void constructorRejectsNullBuilder() {
    assertThatThrownBy(() -> new WhisperTranscriptionClient(null, objectMapper))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorRejectsNullObjectMapper() {
    assertThatThrownBy(() -> new WhisperTranscriptionClient(restClientBuilder, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void transcribeRejectsNullFilename() {
    assertThatThrownBy(
            () -> client.transcribe("http://localhost:8085", new byte[10], null, "whisper-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("audio files");
  }

  @Test
  void transcribeRejectsNonAudioFile() {
    assertThatThrownBy(
            () ->
                client.transcribe("http://localhost:8085", new byte[10], "video.mp4", "whisper-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Rejected: video.mp4");
  }

  @Test
  @SuppressWarnings("unchecked")
  void transcribeReturnsTextFromJsonResponse() {
    RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.post()).thenReturn(uriSpec);
    when(uriSpec.uri("/v1/audio/transcriptions")).thenReturn(bodySpec);
    when(bodySpec.contentType(any())).thenReturn(bodySpec);
    when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
    when(bodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(String.class)).thenReturn("{\"text\":\"Hello world\"}");

    ProcessedAudioPayload result =
        client.transcribe("http://localhost:8085", new byte[] {1, 2, 3}, "audio.mp3", "whisper-1");
    assertThat(result.transcript()).isEqualTo("Hello world");
    assertThat(result.durationSeconds())
        .as("a response without a duration measures nothing")
        .isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void transcribeReportsTheSourceDurationWhenTheProviderGivesOne() {
    // verbose_json's `duration` IS the quantity AUDIO_MINUTE prices. Without it a transcription
    // reports nothing, its hold is released, and the work is served free (ADR-066, audit #22).
    RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.post()).thenReturn(uriSpec);
    when(uriSpec.uri("/v1/audio/transcriptions")).thenReturn(bodySpec);
    when(bodySpec.contentType(any())).thenReturn(bodySpec);
    when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
    when(bodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(String.class)).thenReturn("{\"text\":\"Bonjour\",\"duration\":132.48}");

    ProcessedAudioPayload result =
        client.transcribe("http://localhost:8085", new byte[] {1}, "speech.mp3", "whisper-base");

    assertThat(result.transcript()).isEqualTo("Bonjour");
    assertThat(result.durationSeconds()).isEqualByComparingTo("132.48");
  }

  @Test
  @SuppressWarnings("unchecked")
  void transcribeReturnsRawBodyWhenNoTextKey() {
    RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.post()).thenReturn(uriSpec);
    when(uriSpec.uri("/v1/audio/transcriptions")).thenReturn(bodySpec);
    when(bodySpec.contentType(any())).thenReturn(bodySpec);
    when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
    when(bodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(String.class)).thenReturn("plain text transcript");

    ProcessedAudioPayload result =
        client.transcribe("http://localhost:8085", new byte[] {1}, "speech.wav", "whisper-1");
    assertThat(result.transcript()).isEqualTo("plain text transcript");
  }

  @Test
  @SuppressWarnings("unchecked")
  void transcribeThrowsWhenResponseIsEmpty() {
    RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.post()).thenReturn(uriSpec);
    when(uriSpec.uri("/v1/audio/transcriptions")).thenReturn(bodySpec);
    when(bodySpec.contentType(any())).thenReturn(bodySpec);
    when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
    when(bodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(String.class)).thenReturn(null);

    assertThatThrownBy(
            () ->
                client.transcribe(
                    "http://localhost:8085", new byte[] {1}, "audio.mp3", "whisper-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void transcribeAcceptsAllAudioFormats() {
    // Just verify no exception on valid audio extensions
    for (String ext : new String[] {".mp3", ".wav", ".m4a", ".ogg", ".flac", ".aac", ".webm"}) {
      // Will fail at RestClient mock level, not at validation
      assertThatCode(
              () -> {
                try {
                  client.transcribe(
                      "http://localhost:8085", new byte[] {1}, "file" + ext, "whisper-1");
                } catch (NullPointerException e) {
                  // Expected — RestClient mock not fully wired for these calls
                }
              })
          .doesNotThrowAnyException();
    }
  }

  @Test
  void namedByteArrayResourceProvidesFilename() {
    var resource =
        new WhisperTranscriptionClient.NamedByteArrayResource(new byte[] {1, 2}, "test.mp3");
    assertThat(resource.getFilename()).isEqualTo("test.mp3");
  }

  @Test
  void namedByteArrayResourceEquality() {
    var a = new WhisperTranscriptionClient.NamedByteArrayResource(new byte[] {1}, "a.mp3");
    var b = new WhisperTranscriptionClient.NamedByteArrayResource(new byte[] {1}, "a.mp3");
    var c = new WhisperTranscriptionClient.NamedByteArrayResource(new byte[] {1}, "b.mp3");

    assertThat(a).isEqualTo(b).isNotEqualTo(c).hasSameHashCodeAs(b);
  }
}

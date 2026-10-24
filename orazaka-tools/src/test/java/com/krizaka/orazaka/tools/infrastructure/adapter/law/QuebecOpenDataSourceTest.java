package com.krizaka.orazaka.tools.infrastructure.adapter.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.krizaka.orazaka.tools.domain.model.law.LawArticle;
import com.krizaka.orazaka.tools.domain.model.law.LawTarget;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class QuebecOpenDataSourceTest {

  private static final String DATASET = "https://www.donneesquebec.test/lois.zip";

  @Test
  void reads_only_the_targeted_instruments_from_the_snapshot_and_never_parses_the_others()
      throws Exception {
    byte[] ccq = resource("/law/CCQ-1991_FR-slice.xml");
    byte[] zip =
        zip(
            "Statutes\\CCQ-1991\\FR\\CCQ-1991_FR.xml",
            ccq,
            // not targeted: if it were parsed, this would not be XML
            "Statutes\\M-30\\FR\\M-30_FR.xml",
            "not xml".getBytes(StandardCharsets.UTF_8));
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(DATASET))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(zip, MediaType.APPLICATION_OCTET_STREAM));
    QuebecOpenDataSource source =
        new QuebecOpenDataSource(builder.build(), DATASET, new LawStatuteXmlParser());

    List<LawArticle> articles =
        source.fetch("QC", List.of(LawTarget.of("QC", "CCQ-1991", List.of("1942"))));

    server.verify();
    assertThat(articles).isNotEmpty().allMatch(a -> "CCQ-1991".equals(a.instrument()));
    assertThat(articles).allMatch(a -> "fr".equals(a.language()));
    assertThat(articles).extracting(LawArticle::ref).contains("1942");
    assertThat(source.serves("QC")).isTrue();
    assertThat(source.serves("CA")).isFalse();
  }

  private static byte[] resource(String name) throws Exception {
    try (InputStream in = QuebecOpenDataSourceTest.class.getResourceAsStream(name)) {
      return Objects.requireNonNull(in, name).readAllBytes();
    }
  }

  private static byte[] zip(Object... nameAndBytes) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (int i = 0; i < nameAndBytes.length; i += 2) {
        zip.putNextEntry(new ZipEntry((String) nameAndBytes[i]));
        zip.write((byte[]) nameAndBytes[i + 1]);
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }
}

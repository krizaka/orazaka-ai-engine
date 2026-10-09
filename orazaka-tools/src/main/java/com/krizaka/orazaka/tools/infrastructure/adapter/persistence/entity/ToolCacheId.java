package com.krizaka.orazaka.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/** Embeddable composite primary key record for {@link ToolCacheEntity}. */
@Embeddable
public record ToolCacheId(
    @Column(name = "tool_id", length = 255) String toolId,
    @Column(name = "cache_key") String cacheKey)
    implements Serializable {

  private static final long serialVersionUID = 1L;
}

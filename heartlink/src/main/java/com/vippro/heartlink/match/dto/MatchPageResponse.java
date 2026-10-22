package com.vippro.heartlink.match.dto;

import java.util.List;

public record MatchPageResponse(List<MatchResponse> items,
                               int limit,
                               long offset,
                               long total) {
}

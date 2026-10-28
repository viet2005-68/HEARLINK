package com.example.heartlink.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MessagePageResponse(List<MessageResponse> items,
                                  @JsonProperty("next_before") Long nextBefore) {}

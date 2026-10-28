package com.example.heartlink.chat;

import com.example.heartlink.chat.dto.*;
import com.example.heartlink.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/matches/{matchId}/messages")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {
    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a text message", description = "Only participants of an active match can send messages. Content: 1-2000 characters.")
    public MessageResponse send(
            @Parameter(hidden = true) @RequestAttribute(CurrentUser.ID) Long userId,
            @PathVariable @Min(1) Long matchId,
            @Valid @RequestBody SendMessageRequest request) {
        return chat.send(userId, matchId, request);
    }

    @GetMapping
    @Operation(summary = "Message history", description = "Newest ID first. Pass next_before as before to read older messages; omit before to refresh. Active match participants only.")
    public MessagePageResponse list(
            @Parameter(hidden = true) @RequestAttribute(CurrentUser.ID) Long userId,
            @PathVariable @Min(1) Long matchId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long before) {
        return chat.list(userId, matchId, limit, before);
    }
}

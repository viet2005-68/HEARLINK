package com.example.heartlink.chat;

import com.example.heartlink.chat.dto.*;
import com.example.heartlink.common.error.ApiException;
import com.example.heartlink.entity.*;
import com.example.heartlink.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service
public class ChatService {
    private final MatchRepository matches;
    private final MessageRepository messages;
    private final UserRepository users;
    private final Clock clock;

    public ChatService(MatchRepository matches, MessageRepository messages, UserRepository users, Clock clock) {
        this.matches = matches;
        this.messages = messages;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public MessageResponse send(Long userId, Long matchId, SendMessageRequest request) {
        if (request == null || request.content() == null || request.content().isBlank()
                || request.content().length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Content must contain 1-2000 characters");
        }
        requireUser(userId);
        var match = matches.findByIdForMessage(matchId).orElseThrow(this::notFound);
        requireParticipant(match, userId);
        var sender = match.getUserA().getId().equals(userId) ? match.getUserA() : match.getUserB();
        var message = messages.saveAndFlush(Message.builder().match(match).sender(sender)
                .content(request.content()).createdAt(clock.instant()).build());
        return MessageResponse.from(message);
    }

    @Transactional(readOnly = true)
    public MessagePageResponse list(Long userId, Long matchId, int limit, Long before) {
        if (limit < 1 || limit > 50 || (before != null && before < 1)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid limit or before cursor");
        }
        requireUser(userId);
        requireParticipant(matches.findById(matchId).orElseThrow(this::notFound), userId);
        // Fetch one extra row to determine whether older messages remain without a count query.
        var page = PageRequest.of(0, limit + 1);
        var rows = before == null ? messages.findByMatchIdOrderByIdDesc(matchId, page)
                : messages.findByMatchIdAndIdLessThanOrderByIdDesc(matchId, before, page);
        var items = rows.stream().limit(limit).map(MessageResponse::from).toList();
        Long nextBefore = rows.size() > limit ? items.getLast().id() : null;
        return new MessagePageResponse(items, nextBefore);
    }

    private void requireParticipant(Match match, Long userId) {
        if (!match.getUserA().getId().equals(userId) && !match.getUserB().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MATCH_FORBIDDEN", "Only match participants can access messages");
        }
        if (match.getStatus() != MatchStatus.ACTIVE) {
            throw notFound();
        }
    }

    private void requireUser(Long userId) {
        if (!users.existsById(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User does not exist");
        }
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "Active match was not found");
    }
}

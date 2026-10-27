package com.example.heartlink.swipe;

import com.example.heartlink.common.error.ApiException;
import com.example.heartlink.entity.Match;
import com.example.heartlink.entity.Swipe;
import com.example.heartlink.entity.SwipeAction;
import com.example.heartlink.entity.User;
import com.example.heartlink.match.MatchService;
import com.example.heartlink.repository.SwipeRepository;
import com.example.heartlink.repository.UserRepository;
import com.example.heartlink.swipe.dto.SwipeRequest;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class SwipeServiceTests {
    final UserRepository users = mock(UserRepository.class);
    final SwipeRepository swipes = mock(SwipeRepository.class);
    final MatchService matches = mock(MatchService.class);
    final SwipeService service = new SwipeService(users, swipes, matches);

    void givenUsers() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));
        when(swipes.saveAndFlush(any())).thenAnswer(i -> {
            Swipe s = i.getArgument(0); s.setId(10L); return s;
        });
    }

    @Test
    void selfSwipeAndInvalidActionDoNotWrite() {
        assertThatThrownBy(() -> service.swipe(1L, new SwipeRequest(1L, "like")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("SELF_SWIPE"));
        assertThatThrownBy(() -> service.swipe(1L, new SwipeRequest(2L, "undo")))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(users, swipes, matches);
    }

    @Test
    void missingTargetAndDeletedCallerHaveDifferentErrors() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.swipe(2L, new SwipeRequest(1L, "like")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.swipe(1L, new SwipeRequest(2L, "like")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(401));
        verifyNoInteractions(swipes, matches);
    }

    @Test
    void duplicateSwipeDoesNotOverwriteExistingAction() {
        givenUsers();
        when(swipes.existsBySwiperIdAndTargetId(1L, 2L)).thenReturn(true);
        assertThatThrownBy(() -> service.swipe(1L, new SwipeRequest(2L, "pass")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
        verify(swipes, never()).saveAndFlush(any());
        verifyNoInteractions(matches);
    }

    @Test
    void passSkipsReverseLikeLookup() {
        givenUsers();
        var result = service.swipe(1L, new SwipeRequest(2L, "pass"));
        assertThat(result.matched()).isFalse();
        assertThat(result.match()).isNull();
        verify(swipes, never()).existsBySwiperIdAndTargetIdAndAction(any(), any(), any());
        verifyNoInteractions(matches);
    }

    @Test
    void reverseLikeCreatesSortedPairAndLocksInAscendingOrder() {
        givenUsers();
        when(swipes.existsBySwiperIdAndTargetIdAndAction(1L, 2L, SwipeAction.LIKE)).thenReturn(true);
        when(matches.createForMutualLike(any(), any())).thenAnswer(i -> {
            User first = i.getArgument(0);
            User second = i.getArgument(1);
            assertThat(first.getId()).isEqualTo(1L);
            assertThat(second.getId()).isEqualTo(2L);
            return Match.builder().id(20L).userA(first).userB(second).build();
        });
        var result = service.swipe(2L, new SwipeRequest(1L, "like"));
        assertThat(result.match().id()).isEqualTo(20L);
        assertThat(result.match().withUserId()).isEqualTo(1L);
        var order = inOrder(users, swipes);
        order.verify(users).findByIdForUpdate(1L);
        order.verify(users).findByIdForUpdate(2L);
        order.verify(swipes).existsBySwiperIdAndTargetId(2L, 1L);
    }
}

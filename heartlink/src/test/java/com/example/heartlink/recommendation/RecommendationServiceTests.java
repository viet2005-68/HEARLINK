package com.example.heartlink.recommendation;

import com.example.heartlink.common.error.ApiException;
import com.example.heartlink.entity.User;
import com.example.heartlink.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationServiceTests {
    final UserRepository users = mock(UserRepository.class);
    final RecommendationService service = new RecommendationService(users);

    @Test
    void rejectsOutOfRangeLimitsBeforeQuerying() {
        for (int limit : new int[]{-1, 0, 51}) {
            assertThatThrownBy(() -> service.list(1L, limit)).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus().value()).isEqualTo(400));
        }
        verifyNoInteractions(users);
    }

    @Test
    void deletedUserCannotReadRecommendations() {
        assertThatThrownBy(() -> service.list(1L, 20)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(401));
    }

    @Test
    void mapsPublicProfileAndSupportsEmptyList() {
        when(users.existsById(1L)).thenReturn(true);
        when(users.findRecommendations(1L, PageRequest.of(0, 1))).thenReturn(List.of(
                User.builder().id(2L).name("Binh").bio("Hello").email("private@example.com").build()));
        var result = service.list(1L, 1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().id()).isEqualTo(2L);
        when(users.findRecommendations(1L, PageRequest.of(0, 1))).thenReturn(List.of());
        assertThat(service.list(1L, 1).items()).isEmpty();
    }
}

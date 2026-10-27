package com.example.heartlink.user;

import com.example.heartlink.auth.dto.LoginRequest;
import com.example.heartlink.auth.dto.RegisterRequest;
import com.example.heartlink.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateProfileRequestTests {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void absentBioIsNotAChange() throws Exception {
        var request = mapper.readValue("{}", UpdateProfileRequest.class);
        assertFalse(request.bioProvided());
        assertFalse(request.hasChanges());
    }

    @Test
    void explicitNullBioClears() throws Exception {
        var request = mapper.readValue("{\"bio\":null}", UpdateProfileRequest.class);
        assertTrue(request.hasChanges());
        assertTrue(request.bioProvided());
        assertNull(request.bio());
    }

    @Test
    void stringBioReplaces() throws Exception {
        var request = mapper.readValue("{\"bio\":\"hi\"}", UpdateProfileRequest.class);
        assertEquals("hi", request.bio());
    }

    @Test
    void emailIsTrimmedBeforeValidation() {
        assertEquals("a@b.com", new RegisterRequest("  a@b.com ", "123456", "A", null).email());
        assertEquals("a@b.com", new LoginRequest(" a@b.com\n", "x").email());
    }
}

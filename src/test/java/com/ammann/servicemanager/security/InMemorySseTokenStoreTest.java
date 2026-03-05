/* (C)2026 */
package com.ammann.servicemanager.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InMemorySseTokenStore")
class InMemorySseTokenStoreTest {

    InMemorySseTokenStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySseTokenStore();
    }

    @Nested
    @DisplayName("createToken")
    class CreateToken {

        @Test
        @DisplayName("should return a non-blank UUID string")
        void shouldReturnNonBlankUuid() {
            String uuid = store.createToken("raw.jwt.token");
            assertThat(uuid).isNotBlank();
            // Verify it parses as a UUID
            assertThat(UUID.fromString(uuid)).isNotNull();
        }

        @Test
        @DisplayName("should make the token immediately retrievable")
        void shouldMakeTokenRetrievable() {
            String uuid = store.createToken("raw.jwt.token");
            assertThat(store.getToken(uuid)).contains("raw.jwt.token");
        }

        @Test
        @DisplayName("should create independent entries for distinct raw tokens")
        void shouldCreateIndependentEntries() {
            String uuid1 = store.createToken("token.one");
            String uuid2 = store.createToken("token.two");
            assertThat(uuid1).isNotEqualTo(uuid2);
            assertThat(store.getToken(uuid1)).contains("token.one");
            assertThat(store.getToken(uuid2)).contains("token.two");
        }
    }

    @Nested
    @DisplayName("getToken")
    class GetToken {

        @Test
        @DisplayName("should return token on first read (non-consuming)")
        void shouldReturnTokenOnFirstRead() {
            String uuid = store.createToken("raw.jwt");
            Optional<String> result = store.getToken(uuid);
            assertThat(result).contains("raw.jwt");
        }

        @Test
        @DisplayName("should return token on repeated reads within TTL (reconnect support)")
        void shouldReturnTokenOnRepeatedReads() {
            String uuid = store.createToken("raw.jwt");
            assertThat(store.getToken(uuid)).contains("raw.jwt");
            assertThat(store.getToken(uuid)).contains("raw.jwt");
            assertThat(store.getToken(uuid)).contains("raw.jwt");
        }

        @Test
        @DisplayName("should return empty for unknown UUID")
        void shouldReturnEmptyForUnknownUuid() {
            assertThat(store.getToken(UUID.randomUUID().toString())).isEmpty();
        }

        @Test
        @DisplayName("should return empty for null UUID")
        void shouldReturnEmptyForNullUuid() {
            assertThat(store.getToken(null)).isEmpty();
        }

        @Test
        @DisplayName("should return empty for blank UUID")
        void shouldReturnEmptyForBlankUuid() {
            assertThat(store.getToken("   ")).isEmpty();
            assertThat(store.getToken("")).isEmpty();
        }

        @Test
        @DisplayName("should return empty and remove entry for expired token")
        void shouldReturnEmptyForExpiredToken() {
            String uuid = UUID.randomUUID().toString();
            store.putExpiredToken(uuid, "expired.token");
            assertThat(store.getToken(uuid)).isEmpty();
        }

        @Test
        @DisplayName("should return empty after MAX_READS exceeded")
        void shouldReturnEmptyAfterMaxReadsExceeded() {
            String uuid = store.createToken("rate.limited.token");
            // Exhaust the read budget
            for (int i = 0; i < InMemorySseTokenStore.MAX_READS; i++) {
                store.getToken(uuid);
            }
            // The next read exceeds MAX_READS
            assertThat(store.getToken(uuid)).isEmpty();
        }
    }

    @Nested
    @DisplayName("cleanup")
    class Cleanup {

        @Test
        @DisplayName("should remove expired entries")
        void shouldRemoveExpiredEntries() {
            String expiredUuid = UUID.randomUUID().toString();
            store.putExpiredToken(expiredUuid, "old.token");

            store.cleanup();

            assertThat(store.getToken(expiredUuid)).isEmpty();
        }

        @Test
        @DisplayName("should retain valid entries")
        void shouldRetainValidEntries() {
            String validUuid = store.createToken("valid.token");

            store.cleanup();

            assertThat(store.getToken(validUuid)).contains("valid.token");
        }

        @Test
        @DisplayName("should only remove expired, not valid entries")
        void shouldOnlyRemoveExpired() {
            String validUuid = store.createToken("valid.token");
            String expiredUuid = UUID.randomUUID().toString();
            store.putExpiredToken(expiredUuid, "old.token");

            store.cleanup();

            assertThat(store.getToken(validUuid)).contains("valid.token");
            assertThat(store.getToken(expiredUuid)).isEmpty();
        }
    }
}
/* (C)2026 */
package com.ammann.servicemanager.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ApiProperties")
class ApiPropertiesTest {

    @Test
    @DisplayName("should have correct base URL v1")
    void shouldHaveCorrectBaseUrlV1() {
        assertThat(ApiProperties.BASE_URL_V1).isEqualTo("/api/v1");
    }

    @Test
    @DisplayName("should have correct base URL v2")
    void shouldHaveCorrectBaseUrlV2() {
        assertThat(ApiProperties.BASE_URL_V2).isEqualTo("/api/v2");
    }

    @Test
    @DisplayName("should have correct container base path")
    void shouldHaveCorrectContainerBasePath() {
        assertThat(ApiProperties.Container.BASE).isEqualTo("/containers");
    }

    @Test
    @DisplayName("should construct full container path")
    void shouldConstructFullContainerPath() {
        String fullPath = ApiProperties.BASE_URL_V1 + ApiProperties.Container.BASE;
        assertThat(fullPath).isEqualTo("/api/v1/containers");
    }

    @Test
    @DisplayName("should have private constructor for ApiProperties")
    void shouldHavePrivateConstructorForApiProperties() throws Exception {
        Constructor<ApiProperties> constructor = ApiProperties.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();

        // Verify we can still instantiate via reflection (for coverage)
        constructor.setAccessible(true);
        ApiProperties instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }

    @Test
    @DisplayName("should have private constructor for Container inner class")
    void shouldHavePrivateConstructorForContainerClass() throws Exception {
        Constructor<ApiProperties.Container> constructor =
                ApiProperties.Container.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();

        // Verify we can still instantiate via reflection (for coverage)
        constructor.setAccessible(true);
        ApiProperties.Container instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }
}

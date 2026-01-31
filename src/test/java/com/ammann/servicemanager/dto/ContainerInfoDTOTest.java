/* (C)2026 */
package com.ammann.servicemanager.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ContainerInfoDTO")
class ContainerInfoDTOTest {

    @Test
    @DisplayName("should create record with all fields")
    void shouldCreateRecordWithAllFields() {
        ContainerInfoDTO dto =
                new ContainerInfoDTO(
                        "abc123", "my-container", "nginx:latest", "running", "Up 5 hours");

        assertThat(dto.id()).isEqualTo("abc123");
        assertThat(dto.name()).isEqualTo("my-container");
        assertThat(dto.image()).isEqualTo("nginx:latest");
        assertThat(dto.state()).isEqualTo("running");
        assertThat(dto.status()).isEqualTo("Up 5 hours");
    }

    @Test
    @DisplayName("should support equality")
    void shouldSupportEquality() {
        ContainerInfoDTO dto1 =
                new ContainerInfoDTO("id1", "name1", "image1", "running", "status1");
        ContainerInfoDTO dto2 =
                new ContainerInfoDTO("id1", "name1", "image1", "running", "status1");
        ContainerInfoDTO dto3 = new ContainerInfoDTO("id2", "name2", "image2", "exited", "status2");

        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1).isNotEqualTo(dto3);
    }

    @Test
    @DisplayName("should support hashCode")
    void shouldSupportHashCode() {
        ContainerInfoDTO dto1 =
                new ContainerInfoDTO("id1", "name1", "image1", "running", "status1");
        ContainerInfoDTO dto2 =
                new ContainerInfoDTO("id1", "name1", "image1", "running", "status1");

        assertThat(dto1.hashCode()).hasSameHashCodeAs(dto2);
    }

    @Test
    @DisplayName("should support toString")
    void shouldSupportToString() {
        ContainerInfoDTO dto =
                new ContainerInfoDTO(
                        "abc123", "my-container", "nginx:latest", "running", "Up 5 hours");

        String toString = dto.toString();

        assertThat(toString).contains("abc123");
        assertThat(toString).contains("my-container");
        assertThat(toString).contains("nginx:latest");
        assertThat(toString).contains("running");
        assertThat(toString).contains("Up 5 hours");
    }

    @Test
    @DisplayName("should handle null values")
    void shouldHandleNullValues() {
        ContainerInfoDTO dto = new ContainerInfoDTO(null, null, null, null, null);

        assertThat(dto.id()).isNull();
        assertThat(dto.name()).isNull();
        assertThat(dto.image()).isNull();
        assertThat(dto.state()).isNull();
        assertThat(dto.status()).isNull();
    }
}

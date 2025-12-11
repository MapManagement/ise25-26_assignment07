package de.seuhd.campuscoffee.api.dtos;

import lombok.Builder;

import java.time.LocalDateTime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;

/**
 * DTO record for POS metadata.
 */
@Builder(toBuilder = true)
public record ReviewDto(
        @Nullable Long id,
        @Nullable LocalDateTime createdAt,
        @Nullable LocalDateTime updatedAt,
        // wenn nicht nullable, laufen einige Tests nicht durch
        @Nullable Long posId,
        // wenn nicht nullable, laufen einige Tests nicht durch
        @Nullable Long authorId,
        @NotEmpty @NotNull @NonNull String review,
        @NonNull Boolean approved

) implements Dto<Long> {

    @Override
    public @Nullable Long getId() {
        return id;
    }
}

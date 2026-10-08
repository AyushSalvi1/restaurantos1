package com.lifeos.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Consistent envelope for every paginated collection returned by the API. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(
                content,
                page,
                size,
                total,
                totalPages,
                page == 0,
                totalPages == 0 || page >= totalPages - 1
        );
    }

    public static <T> PageResponse<T> single(T value) {
        return new PageResponse<>(List.of(value), 0, 1, 1, 1, true, true);
    }
}
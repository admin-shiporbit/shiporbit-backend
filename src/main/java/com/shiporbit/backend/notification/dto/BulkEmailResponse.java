package com.shiporbit.backend.notification.dto;

import java.util.List;

public record BulkEmailResponse(int total, int sent, int failed, List<RecipientResult> results) {

    public static BulkEmailResponse of(List<RecipientResult> results) {
        int sent = (int) results.stream().filter(r -> r.response().isSuccess()).count();
        return new BulkEmailResponse(results.size(), sent, results.size() - sent, List.copyOf(results));
    }
}

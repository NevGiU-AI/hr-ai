package com.nevgiu.hrai.candidate;

import jakarta.validation.constraints.NotNull;

public record UpdateLegalHoldRequest(@NotNull Boolean held) {
}

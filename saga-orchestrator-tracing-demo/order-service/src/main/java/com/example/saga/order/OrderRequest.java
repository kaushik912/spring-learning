package com.example.saga.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderRequest(@NotBlank String sku, @Min(1) int qty, @Min(1) long amount) {
}

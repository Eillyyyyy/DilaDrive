package com.diladrive.dto;

import com.diladrive.entity.InvoiceSourceType;
import jakarta.validation.constraints.NotNull;

public record InvoiceSourceRequest(@NotNull InvoiceSourceType sourceType) {
}

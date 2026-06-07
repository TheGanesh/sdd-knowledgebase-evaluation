package com.ganesh.commerce.order.exception;

/** Stable error envelope. {@code code} is for machines; {@code message} is for humans. */
public record ApiError(String code, String message) {
}

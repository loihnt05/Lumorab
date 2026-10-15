package com.lumora.common.web;

import java.util.Map;

public record ApiError(
    String code, String message, Map<String, String> details, String requestId) {}

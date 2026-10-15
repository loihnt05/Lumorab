package com.lumora.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Type-safe CORS settings for browser clients allowed to call the Lumora API. */
@ConfigurationProperties("app.cors")
public record LumoraCorsProperties(List<String> allowedOrigins) {}

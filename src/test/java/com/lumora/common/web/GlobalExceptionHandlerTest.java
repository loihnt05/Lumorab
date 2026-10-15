package com.lumora.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {
  MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc =
        MockMvcBuilders.standaloneSetup(new FixtureController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter())
            .build();
  }

  @Test
  void validationUsesSharedErrorContract() throws Exception {
    mvc.perform(post("/fixture").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details.name").exists())
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void missingResourceUsesSharedErrorContract() throws Exception {
    mvc.perform(get("/fixture"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void malformedJsonUsesSharedErrorContract() throws Exception {
    mvc.perform(post("/fixture").contentType(MediaType.APPLICATION_JSON).content("{bad"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @RestController
  static class FixtureController {
    @PostMapping("/fixture")
    void create(@Valid @RequestBody Input input) {}

    @GetMapping("/fixture")
    void missing() {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  record Input(@NotBlank String name) {}
}

package com.example.restapi.controller;

import com.example.restapi.exception.ProductNotFoundException;
import com.example.restapi.model.Product;
import com.example.restapi.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class)
class ProductControllerValidationTest {

    @TestConfiguration
    static class TestCacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @Test
    void givenBlankName_whenCreateProduct_thenReturns400() throws Exception {
        // Given
        Product product = new Product("", "desc", BigDecimal.TEN);

        // When / Then
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenNegativePrice_whenCreateProduct_thenReturns400() throws Exception {
        // Given
        Product product = new Product("Widget", "desc", BigDecimal.valueOf(-5));

        // When / Then
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenMissingPrice_whenCreateProduct_thenReturns400() throws Exception {
        // Given
        Product product = new Product("Widget", "desc", null);

        // When / Then
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenValidInput_whenCreateProduct_thenReturns201() throws Exception {
        // Given
        Product product = new Product("Widget", "desc", BigDecimal.TEN);
        given(productService.create(any(Product.class))).willReturn(product);

        // When / Then
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated());
    }

    @Test
    void givenNonExistentId_whenGetById_thenReturns404() throws Exception {
        // Given
        given(productService.getById(eq(99L))).willThrow(new ProductNotFoundException(99L));

        // When / Then
        mockMvc.perform(get("/products/{id}", 99L))
                .andExpect(status().isNotFound());
    }

    @Test
    void givenNonNumericId_whenGetById_thenReturns400() throws Exception {
        // When / Then
        mockMvc.perform(get("/products/{id}", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenNonExistentId_whenDelete_thenReturns404() throws Exception {
        // Given
        org.mockito.BDDMockito.willThrow(new ProductNotFoundException(99L))
                .given(productService).delete(99L);

        // When / Then
        mockMvc.perform(delete("/products/{id}", 99L))
                .andExpect(status().isNotFound());
    }
}

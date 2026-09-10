package com.example.restapi.service;

import com.example.restapi.model.Product;
import com.example.restapi.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @CacheEvict(value = "products", allEntries = true)
    public Product create(Product product) {
        log.info("Creating product: {}", product.getName());
        return repository.save(product);
    }

    @Cacheable(value = "products")
    public List<Product> getAll() {
        log.info("Fetching all products from DB...");
        return repository.findAll();
    }

    @Cacheable(value = "products", key = "#id")
    public Product getById(Long id) {
        log.info("Fetching product {} from DB...", id);
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    @CacheEvict(value = "products", allEntries = true)
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
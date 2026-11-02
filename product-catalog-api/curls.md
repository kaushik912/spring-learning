# REST API Curls

```bash
# Create a product
curl -X POST http://localhost:8080/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptop","description":"Gaming laptop","price":1299.99}'

# Get all products
curl http://localhost:8080/products

# Get product by ID
curl http://localhost:8080/products/1

# Delete product
curl -X DELETE http://localhost:8080/products/1

# H2 console (open in browser)
# http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:products
```
## Some sample curls
curl -X POST http://localhost:8060/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 49.99,
    "paymentMethod": "PAYPAL",
    "orderId": 1,
    "orderReference": "some-ref-1234",
    "customer": {
      "id": "abc123",
      "firstname": "Ada",
      "lastname": "Lovelace",
      "email": "ada@example.com"
    }
  }'


  curl -X POST http://localhost:8050/api/v1/products/purchase \
  -H "Content-Type: application/json" \
  -d '[{ "productId": 1, "quantity": 2 }]'

  curl -i -X POST http://localhost:8050/api/v1/products/purchase \
  -H "Content-Type: application/json" \
  -d '[{ "productId": 1, "quantity": 9999 }]'


  curl -i -X POST http://localhost:8222/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "reference": "order-reference-1",
    "amount": 199.98,
    "paymentMethod": "PAYPAL",
    "customerId": "6a9e7362d746e51d6ac8373c",
    "products": [ { "productId": 1, "quantity": 2 } ]
  }'

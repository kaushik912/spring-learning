package com.example.dbioexhaustiondemo.fixed;

import com.example.dbioexhaustiondemo.shared.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FixedOrderRepository extends JpaRepository<CustomerOrder, Long> {

    // Plain equality predicate lets H2 use idx_customer_orders_email directly.
    List<CustomerOrder> findByCustomerEmail(String customerEmail);
}

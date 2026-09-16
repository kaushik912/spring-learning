package com.example.dbioexhaustiondemo.buggy;

import com.example.dbioexhaustiondemo.shared.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BuggyOrderRepository extends JpaRepository<CustomerOrder, Long> {

    // Comparing via a scalar function (SLOW_EMAIL_MATCH) instead of a plain
    // equality predicate means H2 can't use idx_customer_orders_email at all -
    // it has to full-scan and invoke the function on every row.
    @Query(value = "SELECT * FROM customer_orders WHERE SLOW_EMAIL_MATCH(customer_email, :email)", nativeQuery = true)
    List<CustomerOrder> findByEmailIgnoreCaseSlow(@Param("email") String email);
}

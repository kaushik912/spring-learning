package com.example.dbioexhaustiondemo.mysql;

import com.example.dbioexhaustiondemo.shared.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MySqlBuggyOrderRepository extends JpaRepository<CustomerOrder, Long> {

    // MySQL's own SLEEP() stands in for a slow query the same way
    // buggy.SlowEmailMatcher does on H2 - but this time the calling thread's
    // wait is a real network round trip to mysqld, not in-process JVM work,
    // so it shows up as jdk.SocketRead in JFR instead of jdk.ExecutionSample.
    @Query(value = "SELECT * FROM customer_orders WHERE customer_email = :email AND SLEEP(:delaySeconds) = 0",
           nativeQuery = true)
    List<CustomerOrder> findByEmailWithArtificialDelay(@Param("email") String email,
                                                         @Param("delaySeconds") double delaySeconds);
}

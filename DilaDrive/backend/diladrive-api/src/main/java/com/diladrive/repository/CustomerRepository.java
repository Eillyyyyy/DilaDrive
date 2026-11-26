package com.diladrive.repository;

import com.diladrive.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository
        extends JpaRepository<Customer, Long> {

    @Query("select c from Customer c where lower(trim(c.custEmail)) = lower(trim(:email))")
    Optional<Customer> findByCustEmailIgnoreCase(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.custId = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);

}

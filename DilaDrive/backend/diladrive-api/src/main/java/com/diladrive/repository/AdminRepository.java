package com.diladrive.repository;

import com.diladrive.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @org.springframework.data.jpa.repository.Query(value = """
            select admin_id from admin where upper(admin_role) = 'SUPER_ADMIN'
            and upper(admin_status) = 'ACTIVE' order by admin_id for update
            """, nativeQuery = true)
    java.util.List<Long> lockActiveSuperAdminIds();
}

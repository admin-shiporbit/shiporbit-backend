package com.shiporbit.backend.repository;

import com.shiporbit.backend.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<Users, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<Users> findByEmailIgnoreCase(String email);
}

package com.example.demo.repository;

import com.example.demo.model.PermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionOverrideRepository extends JpaRepository<PermissionOverride, Long> {
    List<PermissionOverride> findAllByResource(String resource);
    Optional<PermissionOverride> findByResourceAndRoleName(String resource, String roleName);
}

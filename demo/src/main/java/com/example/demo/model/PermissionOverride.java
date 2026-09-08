package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Permission-matrix override (BACKEND_SPEC §5.8 / §6.7 row 20-21).
 * row defaults come from PermissionDefaults; this table only stores the
 * (resource, role) cells an admin changed. level ∈ none|view|edit.
 */
@Entity
@Table(name = "permission_overrides",
        uniqueConstraints = @UniqueConstraint(name = "uq_perm_override_cell",
                columnNames = {"resource", "role_name"}))
public class PermissionOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String resource;

    @Column(name = "role_name", nullable = false, length = 20)
    private String roleName;

    @Column(nullable = false, length = 8)
    private String level;

    public PermissionOverride() {
    }

    public PermissionOverride(String resource, String roleName, String level) {
        this.resource = resource;
        this.roleName = roleName;
        this.level = level;
    }

    public Long getId() { return id; }
    public String getResource() { return resource; }
    public String getRoleName() { return roleName; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
}

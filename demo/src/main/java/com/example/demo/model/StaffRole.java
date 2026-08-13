package com.example.demo.model;

/**
 * Facility-plane staff roles (BACKEND_SPEC.md §5.6).
 * These double as both job-title vocabulary and access-control roles.
 * The web dashboard routes on admin|doctor; nurse/front-desk/read-only
 * exist in the permission model and will map to workspaces in a later phase.
 */
public enum StaffRole {
    ADMIN,
    DOCTOR,
    NURSE,
    FRONT_DESK,
    READ_ONLY
}

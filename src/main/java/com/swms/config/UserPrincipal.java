package com.swms.config;

import java.security.Principal;

public class UserPrincipal implements Principal {
    private final String username;
    private final String role;
    private final String hostelName;

    public UserPrincipal(String username, String role, String hostelName) {
        this.username = username;
        this.role = role;
        this.hostelName = hostelName;
    }

    @Override
    public String getName() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String getHostelName() {
        return hostelName;
    }
}

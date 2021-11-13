package com.example.usermanagementwithcassandra.services;

import com.example.usermanagementwithcassandra.entities.Role;
import com.example.usermanagementwithcassandra.repositories.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoleService {
    private final RoleRepository roleRepository;

    @Autowired
    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<String> getAll() {
        List<Role> roles = roleRepository.findAll();
        return roles.stream().map(r -> r.getName()).collect(Collectors.toList());
    }
}

package com.example.usermanagementwithcassandra.repositories;

import com.example.usermanagementwithcassandra.entities.Role;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends CassandraRepository<Role, String> {

    Optional<Role> findByUid(String uid);

    Optional<Role> findByName(String name);

}

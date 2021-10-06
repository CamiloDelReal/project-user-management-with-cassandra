package com.example.usermanagementwithcassandra.repositories;

import com.example.usermanagementwithcassandra.entities.User;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends CassandraRepository<User, String> {

    Optional<User> findByUid(String uid);

    Optional<User> findByEmail(String email);

}

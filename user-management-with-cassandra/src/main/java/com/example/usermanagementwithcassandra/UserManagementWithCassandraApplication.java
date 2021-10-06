package com.example.usermanagementwithcassandra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

@SpringBootApplication
@EnableCassandraRepositories("com.example.usermanagementwithcassandra.repositories")
public class UserManagementWithCassandraApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserManagementWithCassandraApplication.class, args);
    }

}

package com.example.usermanagementwithcassandra.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.Indexed;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table("users")
public class User {
    @PrimaryKeyColumn(name = "uid", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    @Indexed
    private String uid;
    @Column("first_name")
    private String firstName;
    @Column("last_name")
    private String lastName;
    @PrimaryKeyColumn(name = "email", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    @Indexed
    private String email;
    @Column("protected_password")
    private String protectedPassword;
    @Column("roles")
    private Set<String> roles;

    public User(String firstName, String lastName, String email, String protectedPassword, Set<String> roles) {
        this.uid = UUID.randomUUID().toString();
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.protectedPassword = protectedPassword;
        this.roles = roles;
    }

}

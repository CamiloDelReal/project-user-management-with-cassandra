package com.example.usermanagementwithcassandra.entities;

import lombok.*;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table("roles")
public class Role {
    public static final String GUEST = "Guest";
    public static final String ADMINISTRATOR = "Administrator";

    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED)
    @Indexed
    private String uid;
    @PrimaryKeyColumn(name = "name", ordinal = 0)
    @Indexed
    private String name;

    public Role(String name) {
        this.uid = UUID.randomUUID().toString();
        this.name = name;
    }
}

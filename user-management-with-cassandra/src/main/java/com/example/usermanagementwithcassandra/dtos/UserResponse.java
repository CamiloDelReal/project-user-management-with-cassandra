package com.example.usermanagementwithcassandra.dtos;

import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UserResponse {
    private String uid;
    private String firstName;
    private String lastName;
    private String email;
    private Set<String> roles;
}

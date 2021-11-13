package com.example.usermanagementwithcassandra.dtos;

import lombok.*;

import java.util.List;

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
    private List<String> roles;
}

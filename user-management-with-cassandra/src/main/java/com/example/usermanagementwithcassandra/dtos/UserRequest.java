package com.example.usermanagementwithcassandra.dtos;

import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UserRequest {
    @NotNull(message = "First name cannot be empty")
    private String firstName;
    @NotNull(message = "Last name cannot be empty")
    private String lastName;
    @NotNull(message = "Email cannot be empty")
    private String email;
    @NotNull(message = "Password cannot be empty")
    private String password;
    private List<String> roles;
}

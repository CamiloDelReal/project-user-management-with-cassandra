package com.example.usermanagementwithcassandra.controllers;

import com.example.usermanagementwithcassandra.dtos.LoginRequest;
import com.example.usermanagementwithcassandra.dtos.LoginResponse;
import com.example.usermanagementwithcassandra.dtos.UserRequest;
import com.example.usermanagementwithcassandra.dtos.UserResponse;
import com.example.usermanagementwithcassandra.entities.User;
import com.example.usermanagementwithcassandra.services.RoleService;
import com.example.usermanagementwithcassandra.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;


@RestController
@RequestMapping(path = "/users")
public class UserManagementController {

    private final Logger logger = LoggerFactory.getLogger(UserManagementController.class);
    private final UserService userService;
    private final RoleService roleService;

    @Autowired
    public UserManagementController(UserService userService, RoleService roleService) {
        this.userService = userService;
        this.roleService = roleService;
    }

    @GetMapping(path = "/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> getAllRoles() {
        ResponseEntity<List<String>> response = null;
        try {
            List<String> roles = roleService.getAll();
            response = new ResponseEntity<>(roles, HttpStatus.OK);
        } catch (Exception ex) {
            response = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated() and hasAuthority('Administrator')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        ResponseEntity<List<UserResponse>> response = null;
        try {
            List<UserResponse> users = userService.getAllUsers();
            response = ResponseEntity.ok(users);
        } catch (Exception ex) {
            logger.error("Exception captured", ex);
            response = ResponseEntity.internalServerError().build();
        }
        return response;
    }

    @GetMapping(path = "/{uid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated and hasAuthority('Administrator') or isAuthenticated() and principal.uid == #uid")
    public ResponseEntity<UserResponse> getUserById(@PathVariable("uid") String uid) {
        ResponseEntity<UserResponse> response = null;
        try {
            UserResponse user = userService.getUserById(uid);
            if(user != null) {
                response = ResponseEntity.ok(user);
            }
            else {
                response = ResponseEntity.notFound().build();
            }
        } catch (Exception ex) {
            logger.error("Exception captured", ex);
            response = ResponseEntity.internalServerError().build();
        }
        return response;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest userRequest) {
        ResponseEntity<UserResponse> response = null;
        try {
            if(userService.isEmailAvailable(userRequest.getEmail())) {
                boolean wannaCreateAdminUser = userService.createUserRequestHasAdminRole(userRequest);
                Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                if (!wannaCreateAdminUser || (principal != null && principal instanceof User && userService.createUserRequestHasAdminRole((User) principal))) {
                    UserResponse user = userService.createUser(userRequest);
                    response = new ResponseEntity<>(user, HttpStatus.CREATED);
                } else {
                    response = new ResponseEntity<>(HttpStatus.FORBIDDEN);
                }
            } else {
                response = new ResponseEntity<>(HttpStatus.CONFLICT);
            }
        } catch (Exception ex) {
            logger.error("Exception captured", ex);
            response = ResponseEntity.internalServerError().build();
        }
        return response;
    }

    @PutMapping(path = "/{uid}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated and hasAuthority('Administrator') or isAuthenticated() and principal.uid == #uid")
    public ResponseEntity<UserResponse> editUser(@PathVariable("uid") String uid, @Valid @RequestBody UserRequest userRequest) {
        ResponseEntity<UserResponse> response = null;
        try {
            boolean wannaCreateAdminUser = userService.createUserRequestHasAdminRole(userRequest);
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if(!wannaCreateAdminUser || (principal != null && principal instanceof User && userService.createUserRequestHasAdminRole((User)principal))) {
                UserResponse user = userService.editUser(uid, userRequest);
                if(user != null) {
                    response = ResponseEntity.ok(user);
                } else {
                    response = ResponseEntity.notFound().build();
                }
            } else {
                response = new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }
        } catch(Exception ex) {
            logger.error("Exception captured", ex);
            response = ResponseEntity.internalServerError().build();
        }
        return response;
    }

    @DeleteMapping(path = "/{uid}")
    @PreAuthorize("isAuthenticated() and hasAuthority('Administrator') or isAuthenticated() and principal.uid == #uid")
    public ResponseEntity<Void> deleteUser(@PathVariable("uid") String uid) {
        ResponseEntity<Void> response = null;
        System.out.println("APPLOGGER - deleteUser " + uid);
        try {
            boolean success = userService.deleteUser(uid);
            if(success) {
                response = ResponseEntity.ok().build();
            } else {
                response = ResponseEntity.notFound().build();
            }
        } catch (Exception ex) {
            logger.error("Exception captured", ex);
            response = ResponseEntity.internalServerError().build();
        }
        return response;
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        ResponseEntity<LoginResponse> response = null;
        try {
            LoginResponse loginResponse = userService.login(loginRequest);
            if(loginResponse != null) {
                response = ResponseEntity.ok(loginResponse);
            } else {
                response = new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception ex) {
            logger.error("Exception captured");
            response = ResponseEntity.internalServerError().build();
        }
        return response;
    }

}

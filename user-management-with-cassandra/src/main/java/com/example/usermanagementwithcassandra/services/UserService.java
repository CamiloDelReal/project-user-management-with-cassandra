package com.example.usermanagementwithcassandra.services;

import com.example.usermanagementwithcassandra.dtos.LoginRequest;
import com.example.usermanagementwithcassandra.dtos.LoginResponse;
import com.example.usermanagementwithcassandra.dtos.UserRequest;
import com.example.usermanagementwithcassandra.dtos.UserResponse;
import com.example.usermanagementwithcassandra.entities.Role;
import com.example.usermanagementwithcassandra.entities.User;
import com.example.usermanagementwithcassandra.repositories.RoleRepository;
import com.example.usermanagementwithcassandra.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService implements UserDetailsService {

    private final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final AuthenticationManager authenticationManager;
    private final ModelMapper mapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${security.token-key}")
    private String tokenKey;
    @Value("${security.token-type}")
    private String tokenType;
    @Value("${security.separator}")
    private String separator;
    @Value("${security.validity}")
    private Long validity;
    @Value("${security.authorities-key}")
    private String authoritiesKey;

    @Autowired
    public UserService(@Lazy AuthenticationManager authenticationManager, ModelMapper mapper, UserRepository userRepository, RoleRepository roleRepository, BCryptPasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.mapper = mapper;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User getUserByEmail(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        return user;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username).orElse(null);
        UserDetails userDetails = null;
        if(user != null) {
            Set<GrantedAuthority> authorities = user.getRoles().stream().map(r -> new SimpleGrantedAuthority(r)).collect(Collectors.toSet());
            userDetails = new org.springframework.security.core.userdetails.User(user.getEmail(), user.getProtectedPassword(), true, true, true, true, authorities);
        }
        return userDetails;
    }

    public List<UserResponse> getAllUsers() {
        List<UserResponse> response = null;
        List<User> users = userRepository.findAll();
        if(users != null && !users.isEmpty()) {
            response = users.stream().map(user -> mapper.map(user, UserResponse.class)).collect(Collectors.toList());
        } else {
            response = new ArrayList<>();
        }
        return response;
    }

    public boolean isEmailAvailable(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        return user == null;
    }

    public UserResponse createUser(UserRequest userRequest) {
        User user = mapper.map(userRequest, User.class);
        user.setUid(UUID.randomUUID().toString());
        user.setProtectedPassword(passwordEncoder.encode(userRequest.getPassword()));
        if(userRequest.getRoles() == null || userRequest.getRoles().isEmpty()) {
            Role guestRole = roleRepository.findByName("Guest").orElse(null);
            user.setRoles(Set.of(guestRole.getName()));
        } else {
            Set<String> roles = userRequest.getRoles().stream().map(it -> roleRepository.findByUid(it).orElse(null)).filter(Objects::nonNull).map(Role::getName).collect(Collectors.toSet());
            user.setRoles(roles);
        }
        userRepository.save(user);
        UserResponse response = mapper.map(user, UserResponse.class);
        return response;
    }

    public LoginResponse login(LoginRequest loginRequest) {
        LoginResponse response = null;
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
            User user = userRepository.findByEmail(loginRequest.getEmail()).orElse(null);
            if(user != null) {
                String rolesClaim = user.getRoles().stream().collect(Collectors.joining(separator));
                Claims claims = Jwts.claims();
                claims.put(authoritiesKey, rolesClaim);
                String subject = String.join(separator, String.valueOf(user.getUid()), user.getEmail());
                long currentTime = System.currentTimeMillis();
                String token = Jwts.builder()
                        .setClaims(claims)
                        .setSubject(subject)
                        .setIssuedAt(new Date(currentTime))
                        .setExpiration(new Date(currentTime + validity))
                        .signWith(SignatureAlgorithm.HS256, tokenKey)
                        .compact();
                response = new LoginResponse(user.getEmail(), tokenType, token);
            }
        } catch (Exception ex) {
            logger.error("Exception captured", ex);
        }
        return response;
    }

    public UserResponse getUserById(String id) {
        UserResponse response = null;
        try {
            User user = userRepository.findByUid(id).orElse(null);
            if (user != null) {
                response = mapper.map(user, UserResponse.class);
            }
        } catch (Exception ex) {
            logger.error("Exception captured. Id not found", ex);
        }
        return response;
    }

    public boolean createUserRequestHasAdminRole(UserRequest userRequest) {
        Role adminRole = roleRepository.findByName("Administrator").orElse(null);
        return userRequest.getRoles() != null && userRequest.getRoles().stream().anyMatch(it -> Objects.equals(it, adminRole.getName()));
    }

    public boolean createUserRequestHasAdminRole(User user) {
        Role adminRole = roleRepository.findByName("Administrator").orElse(null);
        return user.getRoles() != null && user.getRoles().stream().anyMatch(it -> Objects.equals(it, adminRole.getName()));
    }

    public UserResponse editUser(String id, UserRequest userRequest) {
        User user = userRepository.findByUid(id).orElse(null);
        UserResponse response = null;
        if(user != null) {
            user.setFirstName(userRequest.getFirstName());
            user.setLastName(userRequest.getLastName());
            user.setEmail(userRequest.getEmail());
            user.setProtectedPassword(passwordEncoder.encode(userRequest.getPassword()));
            if(userRequest.getRoles() != null && !userRequest.getRoles().isEmpty()) {
                Set<Role> roles = userRequest.getRoles().stream().map(it -> roleRepository.findByName(it).orElse(null)).filter(Objects::nonNull).collect(Collectors.toSet());
                user.setRoles(roles.stream().map(Role::getName).collect(Collectors.toSet()));
            }
            userRepository.save(user);
            response = mapper.map(user, UserResponse.class);
        }
        return response;
    }

    public boolean deleteUser(String id) {
        boolean success = false;
        User user = userRepository.findByUid(id).orElse(null);
        if(user != null) {
            userRepository.delete(user);
            success = true;
        }
        return success;
    }
}

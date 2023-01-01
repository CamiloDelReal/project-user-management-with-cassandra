package com.example.usermanagementwithcassandra.integrations;

import com.example.usermanagementwithcassandra.dtos.LoginRequest;
import com.example.usermanagementwithcassandra.dtos.LoginResponse;
import com.example.usermanagementwithcassandra.dtos.UserRequest;
import com.example.usermanagementwithcassandra.dtos.UserResponse;
import com.example.usermanagementwithcassandra.entities.Role;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testcontainers.containers.CassandraContainer;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertTrue;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
@RunWith(SpringRunner.class)
@ContextConfiguration(initializers = UserManagementControllerClientSideTests.Initializer.class)
public class UserManagementControllerClientSideTests extends AbstractTestNGSpringContextTests {

    @Value("${security.token-type}")
    private String TOKEN_TYPE;

    public static final CassandraContainer cassandraContainer = new CassandraContainer<>("cassandra:4.1")
            .withInitScript("database-init.sql")
            .withReuse(true);

    @Autowired
    private TestRestTemplate restTemplate;

    private String adminToken;
    private UserResponse userCreatedWithDefaultRole;
    private String userCreatedWithDefaultRolePassword;

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            cassandraContainer.start();

            String cassandraPort = String.format("spring.data.cassandra.port=%d", cassandraContainer.getFirstMappedPort());
            String cassandraUsername = String.format("spring.data.cassandra.username=%s", cassandraContainer.getUsername());
            String cassandraPassword = String.format("spring.data.cassandra.password=%s", cassandraContainer.getPassword());

            TestPropertyValues.of(
                    cassandraPort, cassandraUsername, cassandraPassword
            ).applyTo(applicationContext.getEnvironment());
        }
    }

    @Test
    public void loginRoot_success() {
        LoginRequest loginRequest = new LoginRequest("root@gmail.com", "root");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<LoginResponse> response = restTemplate.exchange("/users/login", HttpMethod.POST, request, LoginResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        LoginResponse loginResponse = response.getBody();

        assertNotNull(loginResponse.getEmail());
        assertEquals(loginRequest.getEmail(), loginResponse.getEmail());
        assertNotNull(loginResponse.getTokenType());
        assertEquals(this.TOKEN_TYPE, loginResponse.getTokenType());
        assertNotNull(loginResponse.getToken());

        this.adminToken = loginResponse.getToken();
    }

    @Test
    public void loginRoot_failByInvalidPassword() {
        LoginRequest loginRequest = new LoginRequest("root@gmail.com", "invalid");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<LoginResponse> response = restTemplate.exchange("/users/login", HttpMethod.POST, request, LoginResponse.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void login_failByInvalidCredentials() {
        LoginRequest loginRequest = new LoginRequest("invalid@gmail.com", "12345");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<LoginResponse> response = restTemplate.exchange("/users/login", HttpMethod.POST, request, LoginResponse.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void createUserWithDefaultRole_success() {
        String testPassword = "qwerty";
        UserRequest userRequest = new UserRequest("John", "Doe", "johndoe@gmail.com", testPassword, List.of(Role.GUEST));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<UserRequest> request = new HttpEntity<>(userRequest, headers);

        ResponseEntity<UserResponse> response = restTemplate.exchange("/users", HttpMethod.POST, request, UserResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());

        UserResponse userResponse = response.getBody();

        assertNotNull(userResponse.getUid());
        assertEquals(userRequest.getFirstName(), userResponse.getFirstName());
        assertEquals(userRequest.getLastName(), userResponse.getLastName());
        assertEquals(userRequest.getEmail(), userResponse.getEmail());
        assertNotNull(userResponse.getRoles());
        assertEquals(1, userResponse.getRoles().size());
        assertTrue(userResponse.getRoles().stream().anyMatch(r -> r.equals(Role.GUEST)));

        this.userCreatedWithDefaultRole = userResponse;
        this.userCreatedWithDefaultRolePassword = testPassword;
    }

    @Test
    public void createUserWithAdminRole_failByNoAdminCredentials() {
        UserRequest userRequest = new UserRequest("Jane", "Doe", "janedoe@gmail.com", "qwerty", List.of(Role.ADMINISTRATOR));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<UserRequest> request = new HttpEntity<>(userRequest, headers);

        ResponseEntity<UserResponse> response = restTemplate.exchange("/users", HttpMethod.POST, request, UserResponse.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test(dependsOnMethods = {"loginRoot_success"})
    public void createUserWithAdminRole_success() {
        assertNotNull(this.adminToken);

        UserRequest userRequest = new UserRequest("Kath", "Doe", "kathdoe@gmail.com", "qwerty", List.of(Role.ADMINISTRATOR));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, this.adminToken));
        HttpEntity<UserRequest> request = new HttpEntity<>(userRequest, headers);

        ResponseEntity<UserResponse> response = restTemplate.exchange("/users", HttpMethod.POST, request, UserResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());

        UserResponse userResponse = response.getBody();

        assertNotNull(userResponse.getUid());
        assertEquals(userRequest.getFirstName(), userResponse.getFirstName());
        assertEquals(userRequest.getLastName(), userResponse.getLastName());
        assertEquals(userRequest.getEmail(), userResponse.getEmail());
        assertNotNull(userResponse.getRoles());
        assertEquals(1, userResponse.getRoles().size());
        assertTrue(userResponse.getRoles().stream().anyMatch(r -> r.equals(Role.ADMINISTRATOR)));
    }

    @Test
    public void createUser_failByEmailDuplicity() {
        UserRequest userRequest = new UserRequest("Root2 First Name", "Root2 Last Name", "root@gmail.com", "qwerty", List.of(Role.GUEST));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<UserRequest> request = new HttpEntity<>(userRequest, headers);

        ResponseEntity<UserResponse> response = restTemplate.exchange("/users", HttpMethod.POST, request, UserResponse.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUserWithUserCredentials_success() {
        String testPassword = "12345";
        LoginRequest loginEditRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);
        UserRequest userEditRequest = new UserRequest("Anna", "Doe", "annadoe@gmail.com", testPassword, List.of(Role.GUEST));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<LoginRequest> loginRequest = new HttpEntity<>(loginEditRequest, headers);

        ResponseEntity<LoginResponse> loginEditResponse = restTemplate.exchange("/users/login", HttpMethod.POST, loginRequest, LoginResponse.class);

        assertEquals(HttpStatus.OK, loginEditResponse.getStatusCode());

        String token = loginEditResponse.getBody().getToken();

        headers.add(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token));

        HttpEntity<UserRequest> editRequest = new HttpEntity<>(userEditRequest, headers);

        ResponseEntity<UserResponse> editResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getUid()), HttpMethod.PUT, editRequest, UserResponse.class);

        assertEquals(HttpStatus.OK, editResponse.getStatusCode());
        assertNotNull(editResponse.getBody());

        UserResponse userResponse = editResponse.getBody();

        assertNotNull(userResponse.getUid());
        assertEquals(this.userCreatedWithDefaultRole.getUid(), userResponse.getUid());
        assertEquals(userEditRequest.getFirstName(), userResponse.getFirstName());
        assertEquals(userEditRequest.getLastName(), userResponse.getLastName());
        assertEquals(userEditRequest.getEmail(), userResponse.getEmail());
        assertNotNull(userResponse.getRoles());
        assertEquals(1, userResponse.getRoles().size());
        assertTrue(userResponse.getRoles().stream().anyMatch(r -> r.equals(this.userCreatedWithDefaultRole.getRoles().get(0))));

        this.userCreatedWithDefaultRole = userResponse;
        this.userCreatedWithDefaultRolePassword = testPassword;

        LoginRequest loginCheckRequest = new LoginRequest(userEditRequest.getEmail(), userEditRequest.getPassword());

        headers.remove(HttpHeaders.AUTHORIZATION);

        HttpEntity<LoginRequest> checkRequest = new HttpEntity<>(loginCheckRequest, headers);

        ResponseEntity<LoginResponse> loginCheckResponse = restTemplate.exchange("/users/login", HttpMethod.POST, checkRequest, LoginResponse.class);

        assertEquals(HttpStatus.OK, loginCheckResponse.getStatusCode());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUserWithUserCredentials_failByWrongId() {
        LoginRequest loginEditRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);
        UserRequest userEditRequest = new UserRequest("Mia", "Doe", "miadoe@gmail.com", "asdfg", List.of(Role.GUEST));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<LoginRequest> loginRequest = new HttpEntity<>(loginEditRequest, headers);

        ResponseEntity<LoginResponse> loginEditResponse = restTemplate.exchange("/users/login", HttpMethod.POST, loginRequest, LoginResponse.class);

        assertEquals(HttpStatus.OK, loginEditResponse.getStatusCode());

        String token = loginEditResponse.getBody().getToken();

        headers.add(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token));

        HttpEntity<UserRequest> editRequest = new HttpEntity<>(userEditRequest, headers);

        ResponseEntity<UserResponse> editResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getUid() + "1"), HttpMethod.PUT, editRequest, UserResponse.class);

        assertEquals(HttpStatus.FORBIDDEN, editResponse.getStatusCode());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUserToAdminWithUserCredentials_failByNoAdminCredentials() {
        LoginRequest loginEditRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);
        UserRequest userEditRequest = new UserRequest("Beth", "Doe", "bethdoe@gmail.com", "poiuy", List.of(Role.ADMINISTRATOR));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<LoginRequest> loginRequest = new HttpEntity<>(loginEditRequest, headers);

        ResponseEntity<LoginResponse> loginEditResponse = restTemplate.exchange("/users/login", HttpMethod.POST, loginRequest, LoginResponse.class);

        assertEquals(HttpStatus.OK, loginEditResponse.getStatusCode());

        String token = loginEditResponse.getBody().getToken();

        headers.add(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token));

        HttpEntity<UserRequest> editRequest = new HttpEntity<>(userEditRequest, headers);

        ResponseEntity<UserResponse> editResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getUid()), HttpMethod.PUT, editRequest, UserResponse.class);

        assertEquals(HttpStatus.FORBIDDEN, editResponse.getStatusCode());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUser_failByNoUserCredentials() {
        UserRequest userEditRequest = new UserRequest("Anna", "Doe", "annadoe@gmail.com", "12345", List.of(Role.GUEST));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<UserRequest> editRequest = new HttpEntity<>(userEditRequest, headers);

        ResponseEntity<UserResponse> editResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getUid()), HttpMethod.PUT, editRequest, UserResponse.class);

        assertEquals(HttpStatus.FORBIDDEN, editResponse.getStatusCode());
    }

    @Test(dependsOnMethods = {"loginRoot_success", "createUserWithDefaultRole_success"})
    public void editUserWithAdminCredentials_success() {
        String testPassword = "zxcvb";
        UserRequest userEditRequest = new UserRequest("Sarah", "Doe", "sarahdoe@gmail.com", testPassword, List.of(Role.GUEST));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        headers.add(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, this.adminToken));

        HttpEntity<UserRequest> editRequest = new HttpEntity<>(userEditRequest, headers);

        ResponseEntity<UserResponse> editResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getUid()), HttpMethod.PUT, editRequest, UserResponse.class);

        assertEquals(HttpStatus.OK, editResponse.getStatusCode());
        assertNotNull(editResponse.getBody());

        UserResponse userResponse = editResponse.getBody();

        assertNotNull(userResponse.getUid());
        assertEquals(userEditRequest.getFirstName(), userResponse.getFirstName());
        assertEquals(userEditRequest.getLastName(), userResponse.getLastName());
        assertEquals(userEditRequest.getEmail(), userResponse.getEmail());
        assertNotNull(userResponse.getRoles());
        assertEquals(1, userResponse.getRoles().size());
        assertTrue(userResponse.getRoles().stream().anyMatch(r -> r.equals(this.userCreatedWithDefaultRole.getRoles().get(0))));

        this.userCreatedWithDefaultRole = userResponse;
        this.userCreatedWithDefaultRolePassword = testPassword;

        LoginRequest loginCheckRequest = new LoginRequest(userEditRequest.getEmail(), userEditRequest.getPassword());

        headers.remove(HttpHeaders.AUTHORIZATION);

        HttpEntity<LoginRequest> checkRequest = new HttpEntity<>(loginCheckRequest, headers);

        ResponseEntity<LoginResponse> loginCheckResponse = restTemplate.exchange("/users/login", HttpMethod.POST, checkRequest, LoginResponse.class);

        assertEquals(HttpStatus.OK, loginCheckResponse.getStatusCode());
    }

    @Test(dependsOnMethods = {
            "createUserWithDefaultRole_success",
            "editUser_failByNoUserCredentials",
            "editUserWithUserCredentials_success",
            "editUserWithUserCredentials_failByWrongId",
            "editUserToAdminWithUserCredentials_failByNoAdminCredentials",
            "editUserWithAdminCredentials_success"
    })
    public void deleteUser_failByNoCredentials() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<UserRequest> deleteRequest = new HttpEntity<>(headers);

        ResponseEntity<UserResponse> deleteResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getUid()), HttpMethod.DELETE, deleteRequest, UserResponse.class);

        assertEquals(HttpStatus.FORBIDDEN, deleteResponse.getStatusCode());
    }

    @Test(dependsOnMethods = {
            "createUserWithDefaultRole_success",
            "deleteUser_failByNoCredentials",
            "editUser_failByNoUserCredentials",
            "editUserWithUserCredentials_success",
            "editUserWithUserCredentials_failByWrongId",
            "editUserToAdminWithUserCredentials_failByNoAdminCredentials",
            "editUserWithAdminCredentials_success"
    })
    public void deleteUserWithUserCredentials_success() {
        LoginRequest loginDeleteAndCheckRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<LoginRequest> loginRequest = new HttpEntity<>(loginDeleteAndCheckRequest, headers);

        ResponseEntity<LoginResponse> loginEditResponse = restTemplate.exchange("/users/login", HttpMethod.POST, loginRequest, LoginResponse.class);

        assertEquals(HttpStatus.OK, loginEditResponse.getStatusCode());

        String token = loginEditResponse.getBody().getToken();
        System.out.println("APPLOGGER - token for delete success  " + token);
        System.out.println("APPLOGGER - uid for delete success  " + userCreatedWithDefaultRole.getUid());

        headers.add(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token));

        HttpEntity<UserRequest> deleteRequest = new HttpEntity<>(headers);

        ResponseEntity<UserResponse> deleteResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getUid()), HttpMethod.DELETE, deleteRequest, UserResponse.class);

        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
        assertNull(deleteResponse.getBody());

        headers.remove(HttpHeaders.AUTHORIZATION);

        HttpEntity<LoginRequest> checkRequest = new HttpEntity<>(loginDeleteAndCheckRequest, headers);

        ResponseEntity<LoginResponse> loginCheckResponse = restTemplate.exchange("/users/login", HttpMethod.POST, checkRequest, LoginResponse.class);

        assertEquals(HttpStatus.UNAUTHORIZED, loginCheckResponse.getStatusCode());

        this.userCreatedWithDefaultRole = null;
        this.userCreatedWithDefaultRolePassword = null;
    }

}

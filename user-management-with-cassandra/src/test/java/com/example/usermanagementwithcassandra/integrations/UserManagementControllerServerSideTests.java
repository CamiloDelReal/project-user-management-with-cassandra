package com.example.usermanagementwithcassandra.integrations;

import com.example.usermanagementwithcassandra.dtos.LoginRequest;
import com.example.usermanagementwithcassandra.dtos.LoginResponse;
import com.example.usermanagementwithcassandra.dtos.UserRequest;
import com.example.usermanagementwithcassandra.dtos.UserResponse;
import com.example.usermanagementwithcassandra.entities.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testng.annotations.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.AssertJUnit.assertTrue;

//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@EnableAutoConfiguration
//@RunWith(SpringRunner.class)


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnableWebMvc
@AutoConfigureMockMvc
@ContextConfiguration(initializers = UserManagementControllerServerSideTests.Initializer.class)
public class UserManagementControllerServerSideTests extends AbstractTestNGSpringContextTests {

    @Value("${security.token-type}")
    private String TOKEN_TYPE;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

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
    public void loginRoot_success() throws Exception {
        LoginRequest loginRequest = new LoginRequest("root@gmail.com", "root");

        MvcResult result = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.tokenType").exists())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        LoginResponse loginResponse = mapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);

        this.adminToken = loginResponse.getToken();
    }

    @Test
    public void loginRoot_failByInvalidPassword() throws Exception {
        LoginRequest loginRequest = new LoginRequest("root@gmail.com", "invalid");

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void login_failByInvalidCredentials() throws Exception {
        LoginRequest loginRequest = new LoginRequest("invalid@gmail.com", "12345");

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void createUserWithDefaultRole_success() throws Exception {
        String testPassword = "qwerty";
        UserRequest userRequest = new UserRequest("John", "Doe", "johndoe@gmail.com", testPassword, List.of(Role.GUEST));

        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uid").exists())
                .andExpect(jsonPath("$.firstName").exists())
                .andExpect(jsonPath("$.lastName").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.roles").exists())
                .andReturn();

        UserResponse userResponse = mapper.readValue(result.getResponse().getContentAsString(), UserResponse.class);

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
    public void createUserWithAdminRole_failByNoAdminCredentials() throws Exception {
        UserRequest userRequest = new UserRequest("Jane", "Doe", "janedoe@gmail.com", "qwerty", List.of(Role.ADMINISTRATOR));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(userRequest)))
                .andExpect(status().isForbidden());
    }

    @Test(dependsOnMethods = {"loginRoot_success"})
    public void createUserWithAdminRole_success() throws Exception {
        assertNotNull(this.adminToken);

        UserRequest userRequest = new UserRequest("Kath", "Doe", "kathdoe@gmail.com", "qwerty", List.of(Role.ADMINISTRATOR));

        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, this.adminToken))
                        .content(mapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uid").exists())
                .andExpect(jsonPath("$.firstName").exists())
                .andExpect(jsonPath("$.lastName").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.roles").exists())
                .andReturn();

        UserResponse userResponse = mapper.readValue(result.getResponse().getContentAsString(), UserResponse.class);

        assertNotNull(userResponse.getUid());
        assertNotEquals(0, userResponse.getUid());
        assertEquals(userRequest.getFirstName(), userResponse.getFirstName());
        assertEquals(userRequest.getLastName(), userResponse.getLastName());
        assertEquals(userRequest.getEmail(), userResponse.getEmail());
        assertNotNull(userResponse.getRoles());
        assertEquals(1, userResponse.getRoles().size());
        assertTrue(userResponse.getRoles().stream().anyMatch(r -> r.equals(Role.ADMINISTRATOR)));
    }

    @Test
    public void createUser_failByEmailDuplicity() throws Exception {
        UserRequest userRequest = new UserRequest("Root2 First Name", "Root2 Last Name", "root@gmail.com", "qwerty", List.of(Role.GUEST));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(userRequest)))
                .andExpect(status().isConflict());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUserWithUserCredentials_success() throws Exception {
        String testPassword = "12345";
        LoginRequest loginEditRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);

        MvcResult loginEditResult = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginEditRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();


        UserRequest userEditRequest = new UserRequest("Anna", "Doe", "annadoe@gmail.com", testPassword, List.of(Role.GUEST));

        String token = mapper.readValue(loginEditResult.getResponse().getContentAsString(), LoginResponse.class).getToken();

        MvcResult userEditResult = mockMvc.perform(put("/users/{uid}", this.userCreatedWithDefaultRole.getUid())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token))
                        .content(mapper.writeValueAsString(userEditRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value(this.userCreatedWithDefaultRole.getUid()))
                .andExpect(jsonPath("$.firstName").value(userEditRequest.getFirstName()))
                .andExpect(jsonPath("$.lastName").value(userEditRequest.getLastName()))
                .andExpect(jsonPath("$.email").value(userEditRequest.getEmail()))
                .andExpect(jsonPath("$.roles[0]").value(Role.GUEST))
                .andReturn();

        this.userCreatedWithDefaultRole = mapper.readValue(userEditResult.getResponse().getContentAsString(), UserResponse.class);
        this.userCreatedWithDefaultRolePassword = testPassword;

        LoginRequest loginCheckRequest = new LoginRequest(userEditRequest.getEmail(), userEditRequest.getPassword());

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginCheckRequest)))
                .andExpect(status().isOk());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUserWithUserCredentials_failByWrongId() throws Exception {
        LoginRequest loginEditRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);

        MvcResult loginResult = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginEditRequest)))
                .andExpect(status().isOk())
                .andReturn();

        UserRequest userEditRequest = new UserRequest("Mia", "Doe", "miadoe@gmail.com", "asdfg", List.of(Role.GUEST));

        String token = mapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponse.class).getToken();

        mockMvc.perform(put("/users/{uid}", this.userCreatedWithDefaultRole.getUid() + 1)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token))
                        .content(mapper.writeValueAsString(userEditRequest)))
                .andExpect(status().isForbidden());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUserToAdminWithUserCredentials_failByNoAdminCredentials() throws Exception {
        LoginRequest loginEditRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);

        MvcResult loginEditResult = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginEditRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = mapper.readValue(loginEditResult.getResponse().getContentAsString(), LoginResponse.class).getToken();

        UserRequest userEditRequest = new UserRequest("Beth", "Doe", "bethdoe@gmail.com", "poiuy", List.of(Role.ADMINISTRATOR));

        mockMvc.perform(put("/users/{uid}", this.userCreatedWithDefaultRole.getUid())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token))
                        .content(mapper.writeValueAsString(userEditRequest)))
                .andExpect(status().isForbidden());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUser_failByNoUserCredentials() throws Exception {
        UserRequest userEditRequest = new UserRequest("Anna", "Doe", "annadoe@gmail.com", "12345", List.of(Role.GUEST));

        mockMvc.perform(put("/users/{uid}", this.userCreatedWithDefaultRole.getUid())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(userEditRequest)))
                .andExpect(status().isForbidden());
    }

    @Test(dependsOnMethods = {"loginRoot_success", "createUserWithDefaultRole_success"})
    public void editUserWithAdminCredentials_success() throws Exception {
        String testPassword = "zxcvb";
        UserRequest userEditRequest = new UserRequest("Sarah", "Doe", "sarahdoe@gmail.com", testPassword, List.of(Role.GUEST));

        MvcResult userEditResult = mockMvc.perform(put("/users/{uid}", this.userCreatedWithDefaultRole.getUid())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, this.adminToken))
                        .content(mapper.writeValueAsString(userEditRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value(this.userCreatedWithDefaultRole.getUid()))
                .andExpect(jsonPath("$.firstName").value(userEditRequest.getFirstName()))
                .andExpect(jsonPath("$.lastName").value(userEditRequest.getLastName()))
                .andExpect(jsonPath("$.email").value(userEditRequest.getEmail()))
                .andExpect(jsonPath("$.roles[0]").value(this.userCreatedWithDefaultRole.getRoles().get(0)))
                .andReturn();

        this.userCreatedWithDefaultRole = mapper.readValue(userEditResult.getResponse().getContentAsString(), UserResponse.class);
        this.userCreatedWithDefaultRolePassword = testPassword;

        LoginRequest loginCheckRequest = new LoginRequest(userEditRequest.getEmail(), userEditRequest.getPassword());

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsBytes(loginCheckRequest)))
                .andExpect(status().isOk());
    }

    @Test(dependsOnMethods = {"createUserWithDefaultRole_success"})
    public void deleteUser_failByNoCredentials() throws Exception {
        mockMvc.perform(delete("/users/{uid}", this.userCreatedWithDefaultRole.getUid()))
                .andExpect(status().isForbidden());
    }

    @Test(dependsOnMethods = {
            "createUserWithDefaultRole_success",
            "editUserToAdminWithUserCredentials_failByNoAdminCredentials",
            "editUserWithUserCredentials_failByWrongId",
            "editUserWithUserCredentials_success",
            "editUser_failByNoUserCredentials",
            "editUserWithAdminCredentials_success",
            "deleteUser_failByNoCredentials"})
    public void deleteUserWithUserCredentials_success() throws Exception {
        LoginRequest loginDeleteAndCheckRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);

        MvcResult loginDeleteResult = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginDeleteAndCheckRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = mapper.readValue(loginDeleteResult.getResponse().getContentAsString(), LoginResponse.class).getToken();

        mockMvc.perform(delete("/users/{uid}", this.userCreatedWithDefaultRole.getUid())
                        .header(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginDeleteAndCheckRequest)))
                .andExpect(status().isUnauthorized());

        this.userCreatedWithDefaultRole = null;
        this.userCreatedWithDefaultRolePassword = null;
    }
}

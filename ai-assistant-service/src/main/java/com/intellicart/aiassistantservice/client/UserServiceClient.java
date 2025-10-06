package com.intellicart.aiassistantservice.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class UserServiceClient {

    private static final String USER_SVC = "http://localhost:8081";
    private final RestTemplate http = new RestTemplate();

    public record UserDto(Long id, String username, String email) {}

    public UserDto findByName(String name) {
        try {
            return http.getForObject(
                    USER_SVC + "/api/users/by-name?q=" + name,
                    UserDto.class
            );
        } catch (Exception e) {
            return null;
        }
    }
}

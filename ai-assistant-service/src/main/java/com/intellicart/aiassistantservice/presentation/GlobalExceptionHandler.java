// src/main/java/.../presentation/GlobalExceptionHandler.java
package com.intellicart.aiassistantservice.presentation;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handle(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(e.getMessage() != null ? e.getMessage() : "Something went wrong");
    }
}

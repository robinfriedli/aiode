package net.robinfriedli.botify.rest;

import net.robinfriedli.botify.rest.exceptions.MissingAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ExceptionHandlerAdvice {

    @ExceptionHandler
    public ResponseEntity<String> handleMissingAccessException(MissingAccessException e) {
        return ResponseEntity.status(403).body(e.getMessage());
    }

}

package net.robinfriedli.aiode.rest;

import net.robinfriedli.aiode.rest.exceptions.MissingAccessException;
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

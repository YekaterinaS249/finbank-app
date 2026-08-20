package com.finbank.controller.api;

import com.finbank.dto.TransferRequest;
import com.finbank.model.User;
import com.finbank.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/transfers")
public class TransferApiController {

    private final TransferService transferService;
    private final CurrentUserResolver currentUserResolver;

    public TransferApiController(TransferService transferService, CurrentUserResolver currentUserResolver) {
        this.transferService = transferService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> transfer(@Valid @RequestBody TransferRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        String transferRef = transferService.transfer(user, request);
        return Map.of("transferRef", transferRef, "status", "COMPLETED");
    }
}

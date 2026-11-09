package com.fragment.policeforce.controller;

import com.fragment.policeforce.common.auth.UserContext;
import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.dto.AuthLoginDTO;
import com.fragment.policeforce.dto.AuthRegisterDTO;
import com.fragment.policeforce.dto.ChangePasswordDTO;
import com.fragment.policeforce.service.AuthService;
import com.fragment.policeforce.vo.AuthLoginVO;
import com.fragment.policeforce.vo.CurrentUserVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public Result<AuthLoginVO> login(@Valid @RequestBody AuthLoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody AuthRegisterDTO dto) {
        authService.register(dto);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<CurrentUserVO> me() {
        return Result.success(authService.getCurrentUser(UserContext.requireUser()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(UserContext.requireUser());
        return Result.success();
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(UserContext.requireUser(), dto);
        return Result.success();
    }
}

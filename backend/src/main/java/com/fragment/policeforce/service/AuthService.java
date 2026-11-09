package com.fragment.policeforce.service;

import com.fragment.policeforce.common.auth.LoginUser;
import com.fragment.policeforce.dto.AuthLoginDTO;
import com.fragment.policeforce.dto.AuthRegisterDTO;
import com.fragment.policeforce.dto.ChangePasswordDTO;
import com.fragment.policeforce.vo.AuthLoginVO;
import com.fragment.policeforce.vo.CurrentUserVO;

public interface AuthService {

    AuthLoginVO login(AuthLoginDTO dto);

    void register(AuthRegisterDTO dto);

    void logout(LoginUser loginUser);

    void changePassword(LoginUser loginUser, ChangePasswordDTO dto);

    CurrentUserVO getCurrentUser(LoginUser loginUser);
}

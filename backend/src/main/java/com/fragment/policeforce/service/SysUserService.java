package com.fragment.policeforce.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fragment.policeforce.dto.UserAddDTO;
import com.fragment.policeforce.dto.UserPageQueryDTO;
import com.fragment.policeforce.dto.UserUpdateDTO;
import com.fragment.policeforce.entity.SysUser;
import com.fragment.policeforce.vo.UserVO;

public interface SysUserService extends IService<SysUser> {

    Page<UserVO> pageUsers(UserPageQueryDTO queryDTO);

    UserVO getUserById(Long id);

    void addUser(UserAddDTO dto);

    void updateUser(UserUpdateDTO dto, Long operatorId);

    void updateUserStatus(Long id, String status, Long operatorId);

    void resetPassword(Long id);
}

package com.fragment.policeforce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fragment.policeforce.entity.SysDictType;

import java.util.List;

public interface SysDictTypeService extends IService<SysDictType> {
    List<SysDictType> listDictTypes();
}

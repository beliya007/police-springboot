package com.fragment.policeforce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fragment.policeforce.entity.SysDictType;
import com.fragment.policeforce.mapper.SysDictTypeMapper;
import com.fragment.policeforce.service.SysDictTypeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysDictTypeServiceImpl extends ServiceImpl<SysDictTypeMapper, SysDictType>
        implements SysDictTypeService {

    @Override
    public List<SysDictType> listDictTypes() {
        return this.list();
    }
}

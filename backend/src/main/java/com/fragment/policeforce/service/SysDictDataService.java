package com.fragment.policeforce.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fragment.policeforce.dto.SysDictDataAddDTO;
import com.fragment.policeforce.dto.SysDictDataPageQueryDTO;
import com.fragment.policeforce.dto.SysDictDataUpdateDTO;
import com.fragment.policeforce.entity.SysDictData;

import java.util.List;
import java.util.Map;

public interface SysDictDataService extends IService<SysDictData> {
    List<SysDictData> listByType(String type);
    Map<String, String> getDictLabelMap(String dictType);
    void addDictData(SysDictDataAddDTO dictData);
    void updateDictData(SysDictDataUpdateDTO dictData);
    void removeDictData(Long id);
    SysDictData getDictDataById(Long id);
    Page<SysDictData> pageDictData(SysDictDataPageQueryDTO queryDTO);

}

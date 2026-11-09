package com.fragment.policeforce.controller;

import com.fragment.policeforce.common.auth.AdminOnly;
import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.dto.SysDictDataAddDTO;
import com.fragment.policeforce.dto.SysDictDataPageQueryDTO;
import com.fragment.policeforce.dto.SysDictDataUpdateDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fragment.policeforce.entity.SysDictData;
import com.fragment.policeforce.service.SysDictDataService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dict/data")
public class DictDataController {

    @Autowired
    private SysDictDataService sysDictDataService;

    @GetMapping("/list")
    public Result<List<SysDictData>> listByType(@RequestParam String type) {
        return Result.success(sysDictDataService.listByType(type));
    }

    @AdminOnly
    @GetMapping("/page")
    public Result<Page<SysDictData>> pageDictData(SysDictDataPageQueryDTO queryDTO) {
        return Result.success(sysDictDataService.pageDictData(queryDTO));
    }

    @AdminOnly
    @GetMapping("/{id}")
    public Result<SysDictData> getDictDataById(@PathVariable Long id) {
        return Result.success(sysDictDataService.getDictDataById(id));
    }

    @AdminOnly
    @PostMapping
    public Result<Void> addDictData(@Valid @RequestBody SysDictDataAddDTO dictData) {
        sysDictDataService.addDictData(dictData);
        return Result.success();
    }

    @AdminOnly
    @PutMapping
    public Result<Void> updateDictData(@Valid @RequestBody SysDictDataUpdateDTO dictData) {
        sysDictDataService.updateDictData(dictData);
        return Result.success();
    }

    @AdminOnly
    @DeleteMapping("/{id}")
    public Result<Void> removeDictData(@PathVariable Long id) {
        sysDictDataService.removeDictData(id);
        return Result.success();
    }

}

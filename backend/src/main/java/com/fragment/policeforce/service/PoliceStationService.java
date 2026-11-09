package com.fragment.policeforce.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fragment.policeforce.dto.PoliceStationAddDTO;
import com.fragment.policeforce.dto.PoliceStationPageQueryDTO;
import com.fragment.policeforce.dto.PoliceStationQueryDTO;
import com.fragment.policeforce.dto.PoliceStationUpdateDTO;
import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.vo.PoliceStationVO;

import java.util.List;

/**
* @author fragment
* @description 閽堝琛ㄣ€恟esource(璧勬簮琛?銆戠殑鏁版嵁搴撴搷浣淪ervice
* @createDate 2026-03-28 15:02:51
*/
public interface PoliceStationService extends IService<PoliceStation> {
    List<PoliceStationVO> search(PoliceStationQueryDTO queryDTO);
    Page<PoliceStationVO> pageResource(PoliceStationPageQueryDTO queryDTO);
    PoliceStationVO getStationById(Long id);
    void addResource(PoliceStationAddDTO dto);
    void updateResource(PoliceStationUpdateDTO dto);
    void removeResource(Long id);

}

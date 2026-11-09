package com.fragment.policeforce.allocation.api.vo;

import com.fragment.policeforce.vo.UserVO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserAllocationOverviewVO {

    private UserVO user;

    private Long totalAllocationCount;
    private Long activeAllocationCount;
    private Long finishedAllocationCount;
    private Long cancelledAllocationCount;
    private Long recent30DayAllocationCount;

    private LocalDateTime latestAllocationAt;

    private String favoriteStationName;
    private String favoriteStationType;
    private String favoriteSourceType;
    private String favoriteTimeBucket;
}

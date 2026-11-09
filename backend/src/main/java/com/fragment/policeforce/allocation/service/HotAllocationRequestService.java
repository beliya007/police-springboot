package com.fragment.policeforce.allocation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.allocation.redis.HotAllocationRedisService;
import com.fragment.policeforce.allocation.messaging.AllocationCommandPublisher;
import com.fragment.policeforce.allocation.messaging.AllocationCreateCommand;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.model.AllocationRequest;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.allocation.persistence.AllocationRequestMapper;
import com.fragment.policeforce.allocation.api.vo.AllocationSubmitVO;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HotAllocationRequestService {

    private final AllocationCommandPublisher commandPublisher;
    private final HotAllocationRedisService hotRedis;
    private final AllocationRequestMapper requestMapper;
    private final AllocationMapper reservationMapper;

    public HotAllocationRequestService(AllocationCommandPublisher commandPublisher,
                                        HotAllocationRedisService hotRedis,
                                        AllocationRequestMapper requestMapper,
                                        AllocationMapper reservationMapper) {
        this.commandPublisher = commandPublisher;
        this.hotRedis = hotRedis;
        this.requestMapper = requestMapper;
        this.reservationMapper = reservationMapper;
    }

    /**
     * 受理一次热门预约请求。
     */
    public AllocationSubmitVO accept(String requestId, Long userId, Long officerId,
                                      Long stationId, Long slotId) {
        AllocationCreateCommand command = new AllocationCreateCommand(
                requestId, userId, officerId, stationId, slotId);
        return toResult(commandPublisher.publish(command));
    }

    /**
     * 查询本人预约请求的最终结果。
     */
    public AllocationSubmitVO getRequest(Long userId, String rawRequestId) {
        String requestId = normalizeRequestId(rawRequestId);
        //优先查 MySQL 账本
        AllocationRequest request = findLedger(requestId);
        if (request != null) {
            assertOwner(userId, request.getUserId());   // 只能查自己的
            return toResult(request);
        }

        // 查 Redis 请求记录：可能预占成功但未落库
        HotAllocationRedisService.HotRequestState state = hotRedis.getRequest(requestId);
        if (state == null) {
            throw new BusinessException(404, "预约请求不存在");
        }
        assertOwner(userId, state.userId());   // 只能查自己的
        return toResult(state);
    }


    public String normalizeRequestId(String value) {
        if (value == null) {
            throw new BusinessException(400, "缺少 Idempotency-Key 请求头");
        }
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "Idempotency-Key 必须是 UUID");
        }
    }


    private AllocationRequest findLedger(String requestId) {
        return requestMapper.selectOne(new LambdaQueryWrapper<AllocationRequest>()
                .eq(AllocationRequest::getRequestId, requestId));
    }

    private AllocationSubmitVO toResult(HotAllocationRedisService.HotRequestState state) {
        AllocationSubmitVO result = new AllocationSubmitVO();
        result.setRequestId(state.requestId());
        result.setStatus(apiStatus(state.status()));   // 把内部状态翻译成对外状态(PENDING等)
        result.setAllocationId(state.allocationId());
        result.setAllocationNo(state.allocationNo());
        result.setMessage(message(state.status(), state.rejectReason()));
        return result;
    }

    private AllocationSubmitVO toResult(AllocationRequest request) {
        AllocationSubmitVO result = new AllocationSubmitVO();
        result.setRequestId(request.getRequestId());
        result.setStatus(request.getStatus());
        result.setAllocationId(request.getAllocationId());
        if (request.getAllocationId() != null) {
            AllocationAssignment allocation = reservationMapper.selectById(request.getAllocationId());
            result.setAllocationNo(allocation == null ? null : allocation.getAllocationNo());
        }
        result.setMessage(message(request.getStatus(), request.getRejectReason()));
        return result;
    }

    private String apiStatus(String status) {
        if ("PRE_RESERVED".equals(status) || AllocationRequestStatus.PROCESSING.name().equals(status)) {
            return "PENDING";
        }
        return status;
    }

    private String message(String status, String rejectReason) {
        return switch (apiStatus(status)) {
            case "PENDING" -> "预约请求已受理，正在异步确认";
            case "CONFIRMED" -> "预约成功";
            case "REJECTED" -> rejectReason == null ? "预约未通过最终确认" : rejectReason;
            default -> "预约请求状态未知";
        };
    }

    private void assertOwner(Long actorId, Long ownerId) {
        if (actorId == null || !actorId.equals(ownerId)) {
            throw new BusinessException(403, "无权查询其他用户的预约请求");
        }
    }
}

package com.fragment.policeforce.allocation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.common.constants.DutySlotStatusConstants;
import com.fragment.policeforce.common.constants.DutySlotTypeConstants;
import com.fragment.policeforce.common.outbox.MessageOutboxService;
import com.fragment.policeforce.allocation.messaging.AllocationCreateCommand;
import com.fragment.policeforce.allocation.support.AllocationPersistenceHelper;
import com.fragment.policeforce.allocation.messaging.AllocationResultEvent;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.model.AllocationRequest;
import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.entity.DutySlot;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.allocation.persistence.AllocationRequestMapper;
import com.fragment.policeforce.mapper.PoliceStationMapper;
import com.fragment.policeforce.mapper.DutySlotMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 消费端「确认落库」服务。
 */
@Service
public class AllocationConfirmationService {

    public static final String RESULT_EVENT_TYPE = "RESERVATION_RESULT";
    public static final String RESULT_TAG = "RESULT";

    private final AllocationRequestMapper requestMapper;
    private final AllocationMapper reservationMapper;
    private final PoliceStationMapper policeStationMapper;
    private final DutySlotMapper slotMapper;
    private final AllocationPersistenceHelper persistenceHelper;
    private final MessageOutboxService outboxService;
    private final String resultTopic;

    public AllocationConfirmationService(AllocationRequestMapper requestMapper,
                                          AllocationMapper reservationMapper,
                                          PoliceStationMapper policeStationMapper,
                                          DutySlotMapper slotMapper,
                                          AllocationPersistenceHelper persistenceHelper,
                                          MessageOutboxService outboxService,
                                          @Value("${app.allocation.command.result-topic:allocation-result}") String resultTopic) {
        this.requestMapper = requestMapper;
        this.reservationMapper = reservationMapper;
        this.policeStationMapper = policeStationMapper;
        this.slotMapper = slotMapper;
        this.persistenceHelper = persistenceHelper;
        this.outboxService = outboxService;
        this.resultTopic = resultTopic;
    }

    /**
     * 消费预约命令，把它转成 MySQL 里的权威结果（整个方法在一个事务里）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(AllocationCreateCommand command) {
        validateCommand(command);

        // 请求账本的唯一键负责抵御 RocketMQ 重复投递。
        AllocationRequest request = newProcessingRequest(command);
        if (requestMapper.insertProcessingIgnore(request) == 0) {
            handleDuplicateDelivery(command);
            return;
        }

        // Redis 负责高并发预占，MySQL 仍需校验最终业务状态。
        PoliceStation station = policeStationMapper.selectById(command.stationId());
        DutySlot slot = slotMapper.selectById(command.slotId());
        Rejection rejection = validateBusiness(command, station, slot);
        if (rejection != null) {
            finish(request, AllocationRequestStatus.REJECTED, null, rejection.code(), rejection.reason());
            return;
        }

        AllocationAssignment allocation = persistenceHelper.buildReservation(
                command.userId(), command.officerId(), command.stationId(), command.slotId(), station, slot);
        if (persistenceHelper.saveIfNoActiveConflict(allocation)
                == AllocationPersistenceHelper.SaveOutcome.DUPLICATE) {
            finish(request, AllocationRequestStatus.REJECTED, null,
                    "DUPLICATE_ALLOCATION", "该警员已被调配到该时段，不能重复派出");
            return;
        }

        // 条件更新是数据库侧防超卖的最后一道约束。
        if (slotMapper.deductQuotaIfAvailable(command.slotId()) == 0) {
            reservationMapper.deleteById(allocation.getId());
            finish(request, AllocationRequestStatus.REJECTED, null,
                    "OUT_OF_STOCK", "时段余量不足");
            return;
        }
        finish(request, AllocationRequestStatus.CONFIRMED, allocation, null, null);
    }

    /** 幂等分支：账本已存在，说明这是重复投递。校验一致且已是终态才允许安全返回。 */
    private void handleDuplicateDelivery(AllocationCreateCommand command) {
        AllocationRequest existing = find(command.requestId());
        if (existing == null) {
            throw new IllegalStateException("Duplicate request key exists but ledger row is missing");
        }
        if (!sameRequest(existing, command)) {
            throw new IllegalArgumentException("AllocationAssignment command does not match its request ledger");
        }
        if (!AllocationRequestStatus.isTerminal(existing.getStatus())) {
            throw new IllegalStateException("AllocationAssignment request is still being processed: " + command.requestId());
        }
    }

    /**
     * 收尾：把账本更新成最终状态，并发出结果事件（经 Outbox 可靠投递）。
     *
     * 结果事件会被 AllocationResultService 消费，做三件事：
     *   - 收敛 Redis 状态（CONFIRMED 保留 / REJECTED 释放占位并回补库存）
     *   - 发站内通知
     *   - 仅成功时：建提醒 + 排自动取消
     */
    private void finish(AllocationRequest request,
                        AllocationRequestStatus status,
                        AllocationAssignment allocation,
                        String rejectCode,
                        String rejectReason) {
        request.setStatus(status.name());
        request.setAllocationId(allocation == null ? null : allocation.getId());
        request.setRejectCode(rejectCode);
        request.setRejectReason(rejectReason);
        request.setCompletedAt(LocalDateTime.now());
        if (requestMapper.updateById(request) <= 0) {
            throw new IllegalStateException("Failed to finalize allocation request " + request.getRequestId());
        }

        // 结果事件与业务数据同事务写入 Outbox，避免数据库成功但事件丢失。
        AllocationResultEvent event = new AllocationResultEvent(
                request.getRequestId(),
                request.getUserId(),
                request.getOfficerId(),
                request.getStationId(),
                request.getSlotId(),
                status.name(),
                request.getAllocationId(),
                allocation == null ? null : allocation.getAllocationNo(),
                rejectCode,
                rejectReason
        );
        outboxService.enqueue(
                "RESERVATION_REQUEST",   // 业务类型
                request.getRequestId(),  // 业务 key（幂等）
                RESULT_EVENT_TYPE,       // 事件类型
                resultTopic,             // 目标 topic
                RESULT_TAG,              // tag
                request.getRequestId(),  // 消息 key
                LocalDateTime.now(),     // 入队时间
                event                    // 事件本体
        );
    }

    /** 造一条 PROCESSING 状态的请求账本记录（insertProcessingIgnore 用它做幂等）。 */
    private AllocationRequest newProcessingRequest(AllocationCreateCommand command) {
        AllocationRequest request = new AllocationRequest();
        request.setRequestId(command.requestId());
        request.setUserId(command.userId());
        request.setOfficerId(command.officerId());
        request.setStationId(command.stationId());
        request.setSlotId(command.slotId());
        request.setStatus(AllocationRequestStatus.PROCESSING.name());
        request.setCreatedAt(LocalDateTime.now());
        return request;
    }

    private AllocationRequest find(String requestId) {
        return requestMapper.selectOne(new LambdaQueryWrapper<AllocationRequest>()
                .eq(AllocationRequest::getRequestId, requestId));
    }

    /**
     * 业务校验：消费端拿到 Redis 预占成功的命令后，还要在 MySQL 侧再核一遍。
     * 任何一项不过就返回一个拒绝原因（Rejection），由调用方 finish 成 REJECTED。
     * 返回 null 表示全部通过。
     */
    private Rejection validateBusiness(AllocationCreateCommand command, PoliceStation station, DutySlot slot) {
        if (station == null) {
            return new Rejection("RESOURCE_NOT_FOUND", "资源不存在");
        }
        if (slot == null) {
            return new Rejection("SLOT_NOT_FOUND", "时段不存在");
        }
        if (!command.stationId().equals(slot.getStationId())) {
            return new Rejection("RESOURCE_MISMATCH", "时段不属于当前资源");
        }
        if (!DutySlotTypeConstants.HOT.equals(slot.getSlotType())) {
            return new Rejection("SLOT_TYPE_CHANGED", "时段已不再是热门预约");
        }
        if (!DutySlotStatusConstants.OPEN.equals(slot.getStatus())) {
            return new Rejection("SLOT_CLOSED", "当前时段不可预约");
        }
        LocalDateTime now = LocalDateTime.now();
        if (slot.getOpenTime() == null || now.isBefore(slot.getOpenTime())) {
            return new Rejection("NOT_OPEN", "热门时段尚未开放预约");
        }
        if (slot.getEndDatetime() == null || !now.isBefore(slot.getEndDatetime())) {
            return new Rejection("SLOT_ENDED", "当前时段已结束");
        }
        return null;
    }

    /** 命令基本校验：必要字段缺了直接拒绝（数据异常，不落账本）。 */
    private void validateCommand(AllocationCreateCommand command) {
        if (command == null || command.requestId() == null || command.userId() == null
                || command.officerId() == null || command.stationId() == null || command.slotId() == null) {
            throw new IllegalArgumentException("Invalid allocation create command");
        }
    }

    /** 比对：重复投递的命令和账本记录是否同一件事（警员/资源/时段一致）。 */
    private boolean sameRequest(AllocationRequest request, AllocationCreateCommand command) {
        return request.getUserId().equals(command.userId())
                && request.getOfficerId().equals(command.officerId())
                && request.getStationId().equals(command.stationId())
                && request.getSlotId().equals(command.slotId());
    }

    private record Rejection(String code, String reason) {
    }
}

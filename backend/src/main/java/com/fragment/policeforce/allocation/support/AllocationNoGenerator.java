package com.fragment.policeforce.allocation.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Component;

@Component
public class AllocationNoGenerator {

    public String nextAllocationNo() {
        return "RES" + IdWorker.getIdStr();
    }

    public String nextRequestNo() {
        return "REQ" + IdWorker.getIdStr();
    }
}

package com.fragment.policeforce;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan({
        "com.fragment.policeforce.mapper",
        "com.fragment.policeforce.allocation.persistence",
        "com.fragment.policeforce.efficiency.persistence",
        "com.fragment.policeforce.exam.persistence",
        "com.fragment.policeforce.dashboard.persistence"
})
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class PoliceForceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PoliceForceApplication.class, args);
    }

}

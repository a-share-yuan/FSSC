// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FSSC 后端启动类。扫描 com.fssc 下所有模块（framework / platform / system / demo ...）。
 * <p>
 * 多租户与四道防线全部由框架层（com.fssc.framework）自动装配，
 * 业务模块只需关注自己的 Controller/Service/Mapper。
 */
@SpringBootApplication(scanBasePackages = "com.fssc")
@MapperScan("com.fssc.**.mapper")
@EnableScheduling
@EnableCaching
public class FsscApplication {

    public static void main(String[] args) {
        SpringApplication.run(FsscApplication.class, args);
    }
}

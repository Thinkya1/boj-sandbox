package com.bin.sandbox.model;

import lombok.Data;

/**
 * 容器执行结果
 */
@Data
public class ExecResult {

    /**
     * 进程退出码
     */
    private Integer exitCode;

    /**
     * 标准输出
     */
    private String stdout;

    /**
     * 标准错误
     */
    private String stderr;

    /**
     * 执行耗时
     */
    private Long time;

    /**
     * 峰值内存
     */
    private Long maxMemory;
}

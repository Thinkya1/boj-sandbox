package com.bin.sandbox;

import java.io.File;
import org.springframework.stereotype.Component;

@Component("javaDockerCodeSandBox")
public class JavaDockerCodeSandbox extends DockerCodeSandboxTemplate {

    private static final String DEFAULT_IMAGE = "openjdk:8u342-jdk-slim-buster";

    /**
     * 获取镜像名
     *
     * @return 镜像名
     */
    @Override
    protected String getDockerImage() {
        return DEFAULT_IMAGE;
    }

    /**
     * 生成编译命令
     *
     * @param userCodeFile 代码文件
     * @return 编译命令
     */
    @Override
    protected String[] buildCompileCommand(File userCodeFile) {
        return new String[]{"javac", "-encoding", "UTF-8", "/app/" + userCodeFile.getName()};
    }

    /**
     * 生成运行命令
     *
     * @param userCodeFile 代码文件
     * @return 运行命令
     */
    @Override
    protected String[] buildRunCommand(File userCodeFile) {
        return new String[]{"java", "-cp", "/app", "Main"};
    }
}

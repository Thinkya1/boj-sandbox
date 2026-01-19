package com.bin.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * C++ sandbox implementation.
 */
@Component("cppDockerCodeSandBox")
public class CppDockerCodeSandbox extends DockerCodeSandboxTemplate {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_CPP_FILE_NAME = "main.cpp";

    private static final String EXECUTABLE_NAME = "main";

    private static final String DEFAULT_IMAGE = "gcc:13";

    @Override
    public File saveCodeToFile(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_CPP_FILE_NAME;
        return FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
    }

    @Override
    protected String getDockerImage() {
        return DEFAULT_IMAGE;
    }

    @Override
    protected String[] buildCompileCommand(File userCodeFile) {
        return new String[]{"g++", "/app/" + GLOBAL_CPP_FILE_NAME, "-O2", "-std=c++17", "-o", "/app/" + EXECUTABLE_NAME};
    }

    @Override
    protected String[] buildRunCommand(File userCodeFile) {
        return new String[]{"/app/" + EXECUTABLE_NAME};
    }
}

package com.bin.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;

@Component("pythonDockerCodeSandBox")
public class PythonDockerCodeSandbox extends DockerCodeSandboxTemplate{

    public static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    public static final String GLOBAL_PYTHON_FILE_NAME = "main.py";

    public static final String DEFAULT_IMAGE = "python:3.11-slim";

    @Override
    public File saveCodeToFile(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_PYTHON_FILE_NAME;
        return FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
    }

    @Override
    protected String getDockerImage() {
        return DEFAULT_IMAGE;
    }

    @Override
    protected String[] buildCompileCommand(File userCodeFile) {
        return null;
    }

    @Override
    protected String[] buildRunCommand(File userCodeFile) {
        return new String[]{"python3", "/app/" + GLOBAL_PYTHON_FILE_NAME};
    }

}

package com.bin.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * JavaScript sandbox implementation.
 */
@Component("jsDockerCodeSandBox")
public class JavaScriptDockerCodeSandbox extends DockerCodeSandboxTemplate {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JS_FILE_NAME = "main.js";

    private static final String DEFAULT_IMAGE = "node:18-bullseye-slim";

    @Override
    public File saveCodeToFile(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JS_FILE_NAME;
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
        return new String[]{"node", "/app/" + GLOBAL_JS_FILE_NAME};
    }
}

package com.bin.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * GCC C 语言代码沙箱实现。
 */
@Component("gccDockerCodeSandBox")
public class GccDockerCodeSandbox extends DockerCodeSandboxTemplate {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_C_FILE_NAME = "main.c";

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
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_C_FILE_NAME;
        return FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
    }

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
        return new String[]{"gcc", "/app/" + GLOBAL_C_FILE_NAME, "-O2", "-std=c11", "-o", "/app/" + EXECUTABLE_NAME};
    }

    /**
     * 生成运行命令
     *
     * @param userCodeFile 代码文件
     * @return 运行命令
     */
    @Override
    protected String[] buildRunCommand(File userCodeFile) {
        return new String[]{"/app/" + EXECUTABLE_NAME};
    }

}

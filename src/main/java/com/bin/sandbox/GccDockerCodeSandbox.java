package com.bin.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.bin.sandbox.model.ExecuteMessage;
import com.bin.sandbox.utils.ProcessUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GCC C 语言代码沙箱实现。
 */
@Component("gccDockerCodeSandBox")
public class GccDockerCodeSandbox extends CodeSandboxTemplate {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_C_FILE_NAME = "main.c";

    private static final String EXECUTABLE_NAME = "main";

    private static final long TIME_OUT = 5000L;

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

    @Override
    public ExecuteMessage compileFile(File userCodeFile) {
        String outputFilePath = getExecutablePath(userCodeFile);
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "gcc",
                    userCodeFile.getAbsolutePath(),
                    "-o",
                    outputFilePath
            );
            Process compileProcess = processBuilder.start();
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "compile");
                if (executeMessage.getExitValue() != null && executeMessage.getExitValue() != 0) {
                    String errorMessage = executeMessage.getErrorMessage();
                    if (StrUtil.isBlank(errorMessage)) {
                        errorMessage = "编译失败";
                    }
                    throw new RuntimeException(errorMessage);
                }
            return executeMessage;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String executablePath = getExecutablePath(userCodeFile);
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(executablePath);
                Process runProcess = processBuilder.start();
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        System.out.println("执行超时，终止运行");
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessageWithInput(runProcess, "run", inputArgs);
                System.out.println(executeMessage);
                executeMessageList.add(executeMessage);
            } catch (Exception e) {
                throw new RuntimeException("execution error", e);
            }
        }
        return executeMessageList;
    }

    private String getExecutablePath(File userCodeFile) {
        String parentPath = userCodeFile.getParentFile().getAbsolutePath();
        return parentPath + File.separator + getExecutableName();
    }

    private String getExecutableName() {
        String osName = System.getProperty("os.name");
        if (osName != null && osName.toLowerCase(Locale.ROOT).contains("win")) {
            return EXECUTABLE_NAME + ".exe";
        }
        return EXECUTABLE_NAME;
    }
}

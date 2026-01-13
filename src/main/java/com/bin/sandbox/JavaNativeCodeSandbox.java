package com.bin.sandbox;

import cn.hutool.Hutool;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.bin.sandbox.model.ExecuteCodeRequest;
import com.bin.sandbox.model.ExecuteCodeResponse;
import com.bin.sandbox.model.ExecuteMessage;
import com.bin.sandbox.model.JudgeInfo;
import com.bin.sandbox.utils.ProcessUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class JavaNativeCodeSandbox implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    private static final String SECURITY_MANAGER_PATH = System.getProperty("user.dir")
            + File.separator + "src" + File.separator + "main" + File.separator + "resources"
            + File.separator + "security";

    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";

    private static final List<String> blackList = Arrays.asList("Files", "exec");

    private static final WordTree WORD_TREE;

    static {
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(blackList);
    }

    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("testCode/unsafeCode/RunFileError.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        if (inputList == null || inputList.isEmpty()) {
            inputList = new ArrayList<>();
            inputList.add("");
        }

        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        String userCodeParentPath = null;
        File userCodeFile = null;
        try {
            userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
            String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
            userCodeFile = FileUtil.writeString(code, userCodePath, "UTF-8");

            String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
            try {
                Process compileProcess = Runtime.getRuntime().exec(compileCmd);
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "compile");
                System.out.println(executeMessage);
                if (executeMessage.getExitValue() != null && executeMessage.getExitValue() != 0) {
                    String errorMessage = executeMessage.getErrorMessage();
                    if (StrUtil.isBlank(errorMessage)) {
                        errorMessage = "编译失败";
                    }
                    return getErrorResponse(new RuntimeException(errorMessage));
                }
            } catch (Exception e) {
                return getErrorResponse(e);
            }

            List<ExecuteMessage> executeMessageList = new ArrayList<>();
            for (String inputArgs : inputList) {
//                String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
                String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main",
                        userCodeParentPath,
                        SECURITY_MANAGER_PATH,
                        SECURITY_MANAGER_CLASS_NAME);
                try {
                    Process runProcess = Runtime.getRuntime().exec(runCmd);
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
//                ExecuteMessage executeMessage = ProcessUtils.runInteractProcessAndGetMessage(runProcess, inputArgs);
                    System.out.println(executeMessage);
                    executeMessageList.add(executeMessage);
                } catch (Exception e) {
                    return getErrorResponse(e);
                }
            }

            ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
            List<String> outputList = new ArrayList<>();
            long maxTime = 0;
            for (ExecuteMessage executeMessage : executeMessageList) {
                String errorMessage = executeMessage.getErrorMessage();
                if (StrUtil.isNotBlank(errorMessage)) {
                    executeCodeResponse.setMessage(errorMessage);
                    executeCodeResponse.setStatus(3);
                    break;
                }
                outputList.add(executeMessage.getMessage());
                Long time = executeMessage.getTime();
                if (time != null) {
                    maxTime = Math.max(maxTime, time);
                }
            }
            if (outputList.size() == executeMessageList.size()) {
                executeCodeResponse.setStatus(1);
            }
            executeCodeResponse.setOutputList(outputList);
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setTime(maxTime);

            executeCodeResponse.setJudgeInfo(judgeInfo);
            return executeCodeResponse;
        } finally {
            if (userCodeParentPath != null) {
                boolean del = FileUtil.del(userCodeParentPath);
                System.out.println("删除" + (del ? "成功" : "失败"));
            }
        }
    }

    /**
     * 构建错误响应。
     *
     * @param e 异常
     * @return 响应
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}

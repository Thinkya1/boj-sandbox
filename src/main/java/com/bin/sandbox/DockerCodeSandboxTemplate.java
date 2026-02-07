package com.bin.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.bin.sandbox.config.DockerClientFactory;
import com.bin.sandbox.model.ExecResult;
import com.bin.sandbox.model.ExecuteCodeRequest;
import com.bin.sandbox.model.ExecuteCodeResponse;
import com.bin.sandbox.model.ExecuteMessage;
import com.bin.sandbox.model.JudgeInfo;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import org.springframework.util.StopWatch;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Docker 代码沙箱模板
 */
public abstract class DockerCodeSandboxTemplate implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final long DEFAULT_RUN_TIMEOUT = 5000L;
    private static final long DEFAULT_COMPILE_TIMEOUT = 10000L;
    private static final long PULL_TIMEOUT = 5 * 60 * 1000L;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        if (inputList == null || inputList.isEmpty()) {
            inputList = new ArrayList<>();
            inputList.add("");
        }

        File userCodeFile = null;
        try {
            // 1. 保存用户代码到文件
            userCodeFile = saveCodeToFile(code);

            // 2. 编译代码为 class
            ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
            System.out.println(compileFileExecuteMessage);

            // 3. 运行代码并收集输出
            List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);

            // 4. 汇总输出响应
            return getOutputResponse(executeMessageList);
        } catch (Exception e) {
            // 编译错误 / 沙箱内部异常，不应抛出 500，统一返回结构化响应
            ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
            executeCodeResponse.setOutputList(new ArrayList<>());
            executeCodeResponse.setMessage(e.getMessage());
            executeCodeResponse.setStatus(2);
            executeCodeResponse.setJudgeInfo(new JudgeInfo());
            return executeCodeResponse;
        } finally {
            // 5. 清理文件
            if (userCodeFile != null) {
                boolean deleted = deleteFile(userCodeFile);
                if (!deleted) {
                    System.err.println("删除文件失败, userCodeFilePath = " + userCodeFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * 保存用户代码到文件。
     *
     * @param code 用户代码
     * @return 代码文件
     */
    public File saveCodeToFile(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        return FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
    }

    /**
     * 删除文件。
     *
     * @param userCodeFile 代码文件
     * @return 是否删除成功
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    /**
     * 获取镜像名
     *
     * @return 镜像名
     */
    protected abstract String getDockerImage();

    /**
     * 生成编译命令
     *
     * @param userCodeFile 代码文件
     * @return 编译命令
     */
    protected abstract String[] buildCompileCommand(File userCodeFile);

    /**
     * 生成运行命令
     *
     * @param userCodeFile 代码文件
     * @return 运行命令
     */
    protected abstract String[] buildRunCommand(File userCodeFile);

    /**
     * 获取编译超时
     *
     * @return 超时毫秒
     */
    protected long getCompileTimeoutMs() {
        return DEFAULT_COMPILE_TIMEOUT;
    }

    /**
     * 获取运行超时
     *
     * @return 超时毫秒
     */
    protected long getRunTimeoutMs() {
        return DEFAULT_RUN_TIMEOUT;
    }

    /**
     * 运行时是否统计内存
     *
     * @return 是否统计内存
     */
    protected boolean trackMemoryOnRun() {
        return true;
    }

    /**
     * 编译代码
     *
     * @param userCodeFile 代码文件
     * @return 编译信息
     */
    public ExecuteMessage compileFile(File userCodeFile) {
        String[] compileCommand = buildCompileCommand(userCodeFile);
        if (compileCommand == null || compileCommand.length == 0) {
            ExecuteMessage executeMessage = new ExecuteMessage();
            executeMessage.setExitValue(0);
            return executeMessage;
        }
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        DockerClient dockerClient = DockerClientFactory.createClient();
        String containerId = null;
        try {
            ensureImageExists(dockerClient, getDockerImage());
            containerId = createContainer(dockerClient, userCodeParentPath, false);
            dockerClient.startContainerCmd(containerId).exec();

            ExecResult execResult = execInContainer(
                    dockerClient,
                    containerId,
                    compileCommand,
                    null,
                    getCompileTimeoutMs(),
                    false,
                    true
            );
            ExecuteMessage executeMessage = new ExecuteMessage();
            executeMessage.setExitValue(execResult.getExitCode());
            executeMessage.setMessage(execResult.getStdout());
            executeMessage.setErrorMessage(execResult.getStderr());
            executeMessage.setTime(execResult.getTime());
            if (execResult.getExitCode() == null || execResult.getExitCode() != 0) {
                String errorMessage = execResult.getStderr();
                if (StrUtil.isBlank(errorMessage)) {
                    errorMessage = "编译失败";
                }
                throw new RuntimeException(errorMessage);
            }
            return executeMessage;
        } finally {
            cleanupContainer(dockerClient, containerId);
        }
    }

    /**
     * 运行代码
     *
     * @param userCodeFile 代码文件
     * @param inputList    输入列表
     * @return 执行信息列表
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        DockerClient dockerClient = DockerClientFactory.createClient();
        String containerId = null;
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        try {
            ensureImageExists(dockerClient, getDockerImage());
            containerId = createContainer(dockerClient, userCodeParentPath, true);
            dockerClient.startContainerCmd(containerId).exec();

            String[] runCommand = buildRunCommand(userCodeFile);
            for (String input : inputList) {
                ExecResult execResult = execInContainer(
                        dockerClient,
                        containerId,
                        runCommand,
                        input,
                        getRunTimeoutMs(),
                        trackMemoryOnRun(),
                        false
                );
                ExecuteMessage executeMessage = new ExecuteMessage();
                executeMessage.setMessage(execResult.getStdout());
                executeMessage.setErrorMessage(execResult.getStderr());
                executeMessage.setTime(execResult.getTime());
                executeMessage.setMemory(execResult.getMaxMemory());
                executeMessageList.add(executeMessage);
            }
            return executeMessageList;
        } finally {
            cleanupContainer(dockerClient, containerId);
        }
    }

    /**
     * 构建输出响应
     *
     * @param executeMessageList 执行信息列表
     * @return 输出响应
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0;
        long maxMemory = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(normalizeOutput(executeMessage.getMessage()));
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            Long mem = executeMessage.getMemory();
            if (mem != null && mem > 0) {
                maxMemory = Math.max(maxMemory, mem);
            }
        }
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        System.out.println("当前输出: " + outputList);
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }



    /**
     * 去除行尾空白
     *
     * @param line 行内容
     * @return 处理后内容
     */
    protected String rtrimLine(String line) {
        int end = line.length();
        while (end > 0) {
            char ch = line.charAt(end - 1);
            if (ch == ' ' || ch == '\t') {
                end--;
            } else {
                break;
            }
        }
        return line.substring(0, end);
    }

    /**
     * 创建运行容器
     *
     * @param dockerClient       docker 客户端
     * @param userCodeParentPath 代码目录
     * @param readonlyRootfs     是否只读根文件系统
     * @return 容器 id
     */
    protected String createContainer(DockerClient dockerClient, String userCodeParentPath, boolean readonlyRootfs) {
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(getDockerImage());
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        hostConfig.withSecurityOpts(Arrays.asList("seccomp=unconfined"));
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));

        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(readonlyRootfs)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        return createContainerResponse.getId();
    }

    /**
     * 执行容器命令并收集结果
     *
     * @param dockerClient docker 客户端
     * @param containerId  容器 id
     * @param cmd          执行命令
     * @param input        标准输入
     * @param timeoutMs    超时时间
     * @param trackMemory  是否采集内存
     * @param failOnTimeout 是否超时失败
     * @return 执行结果
     */
    protected ExecResult execInContainer(DockerClient dockerClient, String containerId, String[] cmd, String input,
                                         long timeoutMs, boolean trackMemory, boolean failOnTimeout) {
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                .withCmd(cmd)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .exec();
        String execId = execCreateCmdResponse.getId();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
            @Override
            public void onNext(Frame frame) {
                StreamType streamType = frame.getStreamType();
                if (StreamType.STDERR.equals(streamType)) {
                    stderr.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                } else {
                    stdout.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                }
                super.onNext(frame);
            }
        };

        final long[] maxMemory = {0L};
        StatsCmd statsCmd = null;
        ResultCallback<Statistics> statisticsResultCallback = null;
        if (trackMemory) {
            statsCmd = dockerClient.statsCmd(containerId);
            statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    if (statistics.getMemoryStats() != null && statistics.getMemoryStats().getUsage() != null) {
                        maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                    }
                }

                @Override
                public void close() throws IOException {
                }

                @Override
                public void onStart(Closeable closeable) {
                }

                @Override
                public void onError(Throwable throwable) {
                }

                @Override
                public void onComplete() {
                }
            });
        }

        StopWatch stopWatch = new StopWatch();
        try {
            stopWatch.start();
            ExecStartCmd execStartCmd = dockerClient.execStartCmd(execId);
            if (input != null) {
                execStartCmd.withStdIn(buildStdIn(input));
            }
            boolean completed = execStartCmd.exec(execStartResultCallback)
                    .awaitCompletion(timeoutMs, TimeUnit.MILLISECONDS);
            stopWatch.stop();
            if (!completed && failOnTimeout) {
                throw new RuntimeException("执行超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("执行被中断", e);
        } finally {
            if (statsCmd != null) {
                try {
                    statsCmd.close();
                } catch (Exception ignored) {
                }
            }
            if (statisticsResultCallback != null) {
                try {
                    statisticsResultCallback.close();
                } catch (Exception ignored) {
                }
            }
        }

        Long exitCode = dockerClient.inspectExecCmd(execId).exec().getExitCodeLong();
        ExecResult result = new ExecResult();
        result.setExitCode(exitCode == null ? null : exitCode.intValue());
        result.setStdout(stdout.toString());
        result.setStderr(stderr.toString());
        result.setTime(stopWatch.getLastTaskTimeMillis());
        result.setMaxMemory(maxMemory[0]);
        return result;
    }

    /**
     * 拉取缺失镜像
     *
     * @param dockerClient docker 客户端
     * @param image        镜像名
     */
    protected void ensureImageExists(DockerClient dockerClient, String image) {
        try {
            dockerClient.inspectImageCmd(image).exec();
            return;
        } catch (NotFoundException ignored) {
        }
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
        PullImageResultCallback callback = new PullImageResultCallback() {
            @Override
            public void onNext(com.github.dockerjava.api.model.PullResponseItem item) {
                String id = item.getId();
                String status = item.getStatus();
                com.github.dockerjava.api.model.ResponseItem.ProgressDetail pd = item.getProgressDetail();
                String percent = "";
                if (pd != null && pd.getTotal() != null && pd.getTotal() > 0) {
                    double p = pd.getCurrent() * 100.0 / pd.getTotal();
                    percent = String.format(" %.1f%%", p);
                }
                System.out.println("拉取层 " + (id != null ? id : "?") + " : " + status + percent);
                super.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                System.err.println("镜像拉取错误: " + throwable.getMessage());
                throwable.printStackTrace();
                super.onError(throwable);
            }
        };

        try {
            pullImageCmd.exec(callback).awaitCompletion(PULL_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("拉取被中断", ie);
        }
    }

    /**
     * 构造标准输入
     *
     * @param input 输入内容
     * @return 标准输入流
     */
    protected InputStream buildStdIn(String input) {
        if (input == null) {
            return new ByteArrayInputStream(new byte[0]);
        }
        String normalized = input.endsWith("\n") ? input : input + "\n";
        return new ByteArrayInputStream(normalized.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 清理容器和客户端
     *
     * @param dockerClient docker 客户端
     * @param containerId  容器 id
     */
    protected void cleanupContainer(DockerClient dockerClient, String containerId) {
        if (containerId != null) {
            try {
                dockerClient.removeContainerCmd(containerId)
                        .withForce(true)
                        .exec();
            } catch (Exception ignored) {
            }
        }
        try {
            dockerClient.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * 归一化输出
     *
     * @param value 原始输出
     * @return 归一化输出
     */
    protected String normalizeOutput(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = rtrimLine(lines[i]);
        }
        int end = lines.length;
        while (end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }
        if (end == 0) {
            return "";
        }
        return String.join("\n", Arrays.copyOf(lines, end));
    }
}

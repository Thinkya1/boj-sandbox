package com.bin.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.bin.sandbox.model.ExecuteCodeRequest;
import com.bin.sandbox.model.ExecuteCodeResponse;
import com.bin.sandbox.model.ExecuteMessage;
import com.bin.sandbox.model.JudgeInfo;
import com.bin.sandbox.utils.ProcessUtils;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.stereotype.Component;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

@Component
public class JavaDockerCodeSandboxOld implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;
    private static final long PULL_TIMEOUT = 5 * 60 * 1000L;

    private static final String DEFAULT_IMAGE = "openjdk:8u342-jre-slim-buster";

    private static final String SECURITY_MANAGER_PATH = System.getProperty("user.dir")
            + File.separator + "src" + File.separator + "main" + File.separator + "resources"
            + File.separator + "security";

    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";

    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        JavaDockerCodeSandboxOld javaDockerCodeSandboxold = new JavaDockerCodeSandboxOld();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3", "3 10"));
        String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("testCode/unsafeCode/RunFileError.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
//        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        ExecuteCodeResponse executeCodeResponse = javaDockerCodeSandboxold.executeCode(executeCodeRequest);
        System.out.println("执行完成: " + executeCodeResponse);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, "UTF-8");

        try {
            String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
            try {
                Process compileProcess = Runtime.getRuntime().exec(compileCmd);
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "compile");
                System.out.println(executeMessage);
            } catch (Exception e) {
                return getErrorResponse(e);
            }
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost("tcp://localhost:2375")
                    .build();

            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofMinutes(5))
                    .build();
            DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);

            String image = DEFAULT_IMAGE;
            ensureImageExists(dockerClient, image);

            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
            HostConfig hostConfig = new HostConfig();
            hostConfig.withMemory(100 * 1000 * 1000L);
            hostConfig.withMemorySwap(0L);
            hostConfig.withCpuCount(1L);
            hostConfig.withSecurityOpts(Arrays.asList("seccomp=unconfined"));
            hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));

            CreateContainerResponse createContainerResponse = containerCmd
                    .withHostConfig(hostConfig)
                    .withNetworkDisabled(true)
                    .withReadonlyRootfs(true)
                    .withAttachStdin(true)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .withTty(true)
                    .exec();

            String containerId = createContainerResponse.getId();

            dockerClient.startContainerCmd(containerId).exec();

            List<ExecuteMessage> executeMessageList = new ArrayList<>();
            for (String input : inputList) {
                StopWatch stopWatch = new StopWatch();
                String[] cmdArray = new String[]{"java", "-cp", "/app", "Main"};
                InputStream stdIn = buildStdIn(input);
                ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                        .withCmd(cmdArray)
                        .withAttachStderr(true)
                        .withAttachStdin(true)
                        .withAttachStdout(true)
                        .exec();

                ExecuteMessage executeMessage = new ExecuteMessage();
                final String[] message = {null};
                final String[] errorMessage = {null};
                long time = 0L;
                final boolean[] timeout = {true};
                String execId = execCreateCmdResponse.getId();

                ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                    @Override
                    public void onComplete() {
                        timeout[0] = false;
                        super.onComplete();
                    }

                    @Override
                    public void onNext(Frame frame) {
                        StreamType streamType = frame.getStreamType();
                        if (StreamType.STDERR.equals(streamType)) {
                            errorMessage[0] = new String(frame.getPayload());
                            System.out.println("标准错误: " + errorMessage[0]);
                        } else {
                            message[0] = new String(frame.getPayload());
                            System.out.println("标准输出: " + message[0]);
                        }
                        super.onNext(frame);
                    }
                };

                final long[] maxMemory = {0L};
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {

                    @Override
                    public void onNext(Statistics statistics) {
                        System.out.println("内存占用: " + statistics.getMemoryStats().getUsage() + "kb");
                        maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
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
                statsCmd.exec(statisticsResultCallback);
                try {
                    stopWatch.start();
                    dockerClient.execStartCmd(execId)
                            .withStdIn(stdIn)
                            .exec(execStartResultCallback)
                            .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                    stopWatch.stop();
                    time = stopWatch.getLastTaskTimeMillis();
                    statsCmd.close();
                } catch (InterruptedException e) {
                    System.out.println("执行被中断");
                    throw new RuntimeException(e);
                } finally {
                    try { statisticsResultCallback.close(); } catch (Exception ignored) {}
                }

                executeMessage.setMessage(message[0]);
                executeMessage.setErrorMessage(errorMessage[0]);
                executeMessage.setTime(time);
                executeMessage.setMemory(maxMemory[0]);
                executeMessageList.add(executeMessage);
            }

            ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
            List<String> outputList = new ArrayList<>();
            long maxTime = 0;
            long maxMemoryAll = 0L;
            for (ExecuteMessage executeMessage : executeMessageList) {
                String errorMessage = executeMessage.getErrorMessage();
                if (StrUtil.isNotBlank(errorMessage)) {
                    executeCodeResponse.setMessage(errorMessage);
                    executeCodeResponse.setStatus(3);
                    break;
                }
                String msg = executeMessage.getMessage();
                if (msg != null) {
                    msg = msg.replace("\r", "").replace("\n", "").trim();
                    outputList.add(msg);
                }
                Long time = executeMessage.getTime();
                if (time != null) {
                    maxTime = Math.max(maxTime, time);
                }
                Long mem = executeMessage.getMemory();
                if (mem != null && mem > 0) {
                    maxMemoryAll = Math.max(maxMemoryAll, mem);
                }
            }

            if (outputList.size() == executeMessageList.size()) {
                executeCodeResponse.setStatus(1);
            }
            System.out.println("当前输出: " + outputList);
            executeCodeResponse.setOutputList(outputList);
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setTime(maxTime);
            judgeInfo.setMemory(maxMemoryAll);

            executeCodeResponse.setJudgeInfo(judgeInfo);

            return executeCodeResponse;
        } finally {
            if (userCodeFile.getParentFile() != null) {
                boolean del = FileUtil.del(userCodeParentPath);
                System.out.println("删除" + (del ? "成功" : "失败"));
            }
        }
    }

    private void ensureImageExists(DockerClient dockerClient, String image) {
        try {
            dockerClient.inspectImageCmd(image).exec();
            return;
        } catch (NotFoundException ignored) {
            // 镜像不存在时拉取
        }
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
        PullImageResultCallback callback = new PullImageResultCallback() {
            @Override
            public void onNext(PullResponseItem item) {
                String id = item.getId();
                String status = item.getStatus();
                ResponseItem.ProgressDetail pd = item.getProgressDetail();
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

    private InputStream buildStdIn(String input) {
        if (input == null) {
            return new ByteArrayInputStream(new byte[0]);
        }
        String normalized = input.endsWith("\n") ? input : input + "\n";
        return new ByteArrayInputStream(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}

package com.bin.sandbox.utils;

import cn.hutool.core.util.StrUtil;
import com.bin.sandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 进程工具类
 */
@Slf4j
public class ProcessUtils {

    /**
     * 执行进程并获取信息
     *
     * @param runProcess
     * @param opName
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> stdOutFuture = executor.submit(() -> readStream(runProcess.getInputStream()));
            Future<String> stdErrFuture = executor.submit(() -> readStream(runProcess.getErrorStream()));

            int exitValue = runProcess.waitFor();
            executeMessage.setExitValue(exitValue);
            String out = stdOutFuture.get();
            String err = stdErrFuture.get();

            if (exitValue == 0) {
                System.out.println(opName + "成功");
                executeMessage.setMessage(out);
            } else {
                System.out.println(opName + "失败，错误码：" + exitValue);
                executeMessage.setMessage(out);
                executeMessage.setErrorMessage(err);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdownNow();
        }
        stopWatch.stop();
        executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        return executeMessage;
    }

    /**
     * 执行进程并获取带输入的信息
     * @param runProcess
     * @param opName
     * @param input
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessageWithInput(Process runProcess, String opName, String input) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> stdOutFuture = executor.submit(() -> readStream(runProcess.getInputStream()));
            Future<String> stdErrFuture = executor.submit(() -> readStream(runProcess.getErrorStream()));

            writeInput(runProcess, input);

            int exitValue = runProcess.waitFor();
            executeMessage.setExitValue(exitValue);
            String out = stdOutFuture.get();
            String err = stdErrFuture.get();

            if (exitValue == 0) {
                System.out.println(opName + "成功");
                executeMessage.setMessage(out);
            } else {
                System.out.println(opName + "失败，错误码：" + exitValue);
                executeMessage.setMessage(out);
                executeMessage.setErrorMessage(err);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdownNow();
        }
        stopWatch.stop();
        executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        return executeMessage;
    }


    private static String readStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeInput(Process runProcess, String input) throws IOException {
        try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(runProcess.getOutputStream(), StandardCharsets.UTF_8)) {
            if (input != null && !input.isEmpty()) {
                outputStreamWriter.write(input);
                if (!input.endsWith("\n")) {
                    outputStreamWriter.write("\n");
                }
            }
            outputStreamWriter.flush();
        }
    }
}

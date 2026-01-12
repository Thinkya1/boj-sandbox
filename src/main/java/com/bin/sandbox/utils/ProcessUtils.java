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

    /**
     * 执行交互式进程并获取信息
     *
     * @param runProcess
     * @param args
     * @return
     */
    public static ExecuteMessage runInteractProcessAndGetMessage(Process runProcess, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        // 计算执行时间
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        OutputStream outputStream = null;
        OutputStreamWriter outputStreamWriter = null;
        BufferedReader bufferedReader = null;
        InputStreamReader inputStreamReader = null;
        BufferedReader errorBufferedReader = null;
        InputStreamReader inputStreamReader1 = null;
        try {
            // 向控制台输入程序
            outputStream = runProcess.getOutputStream();
            outputStreamWriter = new OutputStreamWriter(outputStream);
            String[] s = args.split(" ");
            String join = StrUtil.join("\n", s) + "\n";
            outputStreamWriter.write(join);
            // 相当于按了回车，执行输入的发送
            outputStreamWriter.flush();

            int exitCode = runProcess.waitFor();
            // 获取正常输出
            inputStreamReader = new InputStreamReader(runProcess.getInputStream());
            bufferedReader = new BufferedReader(inputStreamReader);
            String curStr;
            List<String> messages = new ArrayList<>();
            while ((curStr = bufferedReader.readLine()) != null) {
                messages.add(curStr);
            }
            executeMessage.setMessage(StringUtils.join(messages, "\n"));
            if (exitCode == 0) {
                log.info("执行成功");
            } else {
                log.info("执行失败 收集错误信息");
                // 获取错误输出
                inputStreamReader1 = new InputStreamReader(runProcess.getErrorStream());
                errorBufferedReader = new BufferedReader(inputStreamReader1);
                List<String> errorMessages = new ArrayList<>();
                String curErrorLine;
                while ((curErrorLine = errorBufferedReader.readLine()) != null) {
                    errorMessages.add(curErrorLine);
                }
                executeMessage.setErrorMessage(StringUtils.join(errorMessages, "\n"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 记得释放资源 否则会卡死
            try {
                if (outputStreamWriter != null) {
                    outputStreamWriter.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                if (inputStreamReader != null) {
                    inputStreamReader.close();
                }
                if (errorBufferedReader != null) {
                    errorBufferedReader.close();
                }
                if (inputStreamReader1 != null) {
                    inputStreamReader1.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        stopWatch.stop();
        executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        return executeMessage;
    }
}

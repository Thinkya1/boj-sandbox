package com.bin.sandbox.utils;

import cn.hutool.core.util.StrUtil;
import com.bin.sandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

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
        BufferedReader bufferedReader = null;
        InputStreamReader inputStreamReader = null;
        BufferedReader errorBufferedReader = null;
        InputStreamReader inputStreamReader1 = null;
        try {

            // 等待程序执行，获取错误码
            int exitValue = runProcess.waitFor();
            executeMessage.setExitValue(exitValue);
            // 正常退出
            if (exitValue == 0) {
                System.out.println(opName + "成功");
                // 分批获取进程的正常输出
                bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行读取
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine).append("\n");
                }
                executeMessage.setMessage(compileOutputStringBuilder.toString());
            } else {
                // 异常退出
                System.out.println(opName + "失败，错误码： " + exitValue);
                // 分批获取进程的正常输出
                bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行读取
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine).append("\n");
                }
                executeMessage.setMessage(compileOutputStringBuilder.toString());

                // 分批获取进程的错误输出
                errorBufferedReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                StringBuilder errorCompileOutputStringBuilder = new StringBuilder();

                // 逐行读取
                String errorCompileOutputLine;
                while ((errorCompileOutputLine = errorBufferedReader.readLine()) != null) {
                    errorCompileOutputStringBuilder.append(errorCompileOutputLine).append("\n");
                }
                executeMessage.setErrorMessage(errorCompileOutputStringBuilder.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 记得释放资源 否则会卡死
            try {
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

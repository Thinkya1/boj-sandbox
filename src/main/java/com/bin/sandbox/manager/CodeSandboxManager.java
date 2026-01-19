package com.bin.sandbox.manager;

import com.bin.sandbox.CodeSandbox;
import com.bin.sandbox.constant.LanguageConstant;
import com.bin.sandbox.model.ExecuteCodeRequest;
import com.bin.sandbox.model.ExecuteCodeResponse;
import com.bin.sandbox.utils.SpringContextUtils;
import com.bin.sandbox.utils.ThrowUtils;

/**
 * 代码沙箱管理器，根据语言选择判题系统。
 */
public final class CodeSandboxManager {

    private CodeSandboxManager() {
    }

    public static ExecuteCodeResponse doExec(ExecuteCodeRequest executeCodeRequest) {
        CodeSandbox codeSandbox = null;
        String language = executeCodeRequest.getLanguage();
        if (LanguageConstant.JAVA.equals(language)) {
            codeSandbox = (CodeSandbox) SpringContextUtils.getBean("javaDockerCodeSandBox");
        } else if (LanguageConstant.PYTHON.equals(language)) {
            codeSandbox = (CodeSandbox) SpringContextUtils.getBean("pythonDockerCodeSandBox");
        } else if (LanguageConstant.JAVASCRIPT.equals(language) || LanguageConstant.JS.equals(language)) {
            codeSandbox = (CodeSandbox) SpringContextUtils.getBean("jsDockerCodeSandBox");
        } else if (LanguageConstant.GCC.equals(language)) {
            codeSandbox = (CodeSandbox) SpringContextUtils.getBean("gccDockerCodeSandBox");
        } else if (LanguageConstant.CPP.equals(language) || LanguageConstant.CPLUSPLUS.equals(language)) {
            codeSandbox = (CodeSandbox) SpringContextUtils.getBean("cppDockerCodeSandBox");
        }
        ThrowUtils.throwIf(codeSandbox == null, "编程语言非法");
        return codeSandbox.executeCode(executeCodeRequest);
    }
}

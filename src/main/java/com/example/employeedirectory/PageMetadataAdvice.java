package com.example.employeedirectory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class PageMetadataAdvice {

    @Value("${build.version:dev}")
    private String version;

    @Value("${build.git-commit:unknown}")
    private String gitCommit;

    @ModelAttribute("appVersion")
    public String appVersion() {
        return version;
    }

    @ModelAttribute("gitCommit")
    public String gitCommit() {
        return gitCommit;
    }
}

package com.data.imputation;

import com.data.imputation.ui.DesktopUi;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class ImputationApplication implements CommandLineRunner {

    private final DesktopUi desktopUi;

    public ImputationApplication(DesktopUi desktopUi) {
        this.desktopUi = desktopUi;
    }

    public static void main(String[] args) {
        // headless(false) is required so Swing windows can open
        new SpringApplicationBuilder(ImputationApplication.class)
                .headless(false)
                .run(args);
    }

    @Override
    public void run(String... args) {
        desktopUi.show();
    }
}

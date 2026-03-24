package com.wiyuka.acceleratedrecoiling.natives;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class AVX2 {
    public static boolean hasAVX2() {
        try {

            ProcessBuilder pb = new ProcessBuilder("java", "-XX:+PrintFlagsFinal", "-version");
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("UseAVX")) {
                        String[] parts = line.trim().split("\\s+");
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].equals("UseAVX")) {
                                String val = parts[i + 2];
                                int avxLevel = Integer.parseInt(val);
                                return avxLevel >= 2;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}

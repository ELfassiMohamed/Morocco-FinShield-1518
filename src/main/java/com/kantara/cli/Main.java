package com.kantara.cli;

import picocli.CommandLine;

public class Main {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new KantaraCommand()).execute(args);
        System.exit(exitCode);
    }
}


package com.kantara.cli;

import picocli.CommandLine.Command;

@Command(
        name = "kantara",
        description = "AI-powered document processing CLI",
        mixinStandardHelpOptions = true,
        subcommands = {ComposeCommand.class}
)
public class KantaraCommand implements Runnable {

    @Override
    public void run() {
        // Root command intentionally does nothing. Use subcommands.
    }
}


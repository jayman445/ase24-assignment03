import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fuzzer {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Fuzzer.java \"<command_to_fuzz>\"");
            System.exit(1);
        }
        String commandToFuzz = args[0];
        String workingDirectory = "./";

        if (!Files.exists(Paths.get(workingDirectory, commandToFuzz))) {
            throw new RuntimeException("Could not find command '%s'.".formatted(commandToFuzz));
        }

        String seedInput = "<html a=\"value\">...</html>";

        ProcessBuilder builder = getProcessBuilderForCommand(commandToFuzz, workingDirectory);
        System.out.printf("Command: %s\n", builder.command());

        //Hardcoded mutations
        runCommand(builder, seedInput, getMutatedInputs(seedInput, List.of(
                input -> input.replace("<html", "a"), // this is just a placeholder, mutators should not only do hard-coded string replacement
                input -> input.replace("<html", "")
                
                // more hard-coded string replacements
                /*input -> input.replace("value", ""), // Remove value of attribute
                input -> input.replace("a=\"value\"", ""), // remove complete attribute
                input -> input + "<body></body>", // Add new tag
                input -> input.replace("...", ""), // Remove the content
                input -> input.replace("<html", "<invalid"), // Replace html tag with unvalid tag
                input -> input.toUpperCase(), // everything to upper case
                input -> input.replace("a=\"value\"", "b=\"other\""), // change attribute and value
                input -> input.substring(0, input.length() / 2), // cut string in half
                input -> input + " & <script>alert('XSS')</script>", // add pontential XSS-Code
                input -> "<!DOCTYPE html>" + input, // Add HTML-Configuration
                input -> input.replace(">", " "), // Remove closing sign from tag
                input -> "", // test parser with empty string
                input -> " ", // test parser with only whitespace in string
                //input -> null // test parser with null => NullPointerException
                input -> input.replace(">", "&gt;").replace("<", "&lt;"), // replace angle brackets
                input -> new StringBuilder(input).reverse().toString(), // reverse string
                input -> "   " + input.trim() + "   ", // add random whitespace parts
                input -> input.replace("<", "<<")*/
        )
        ));

        System.err.println("dynamic Mutators!");
        // List of Mutators 
        List<Function<String, String>> dynamicMutators = new ArrayList<>();
        // adding dynamic mutators multiple times to see some variety in mutated inputs
        for (int i = 0; i < 0; i++) {
            dynamicMutators.add(input -> insertRandomCharacter(seedInput));
            dynamicMutators.add(input -> removeRandomSubstring(seedInput));
            dynamicMutators.add(input -> replaceRandomTag(seedInput));
            dynamicMutators.add(input -> randomizeCase(seedInput));
            dynamicMutators.add(input -> changeAttributeValueLengthRandomly(seedInput, "a"));
            dynamicMutators.add(input -> replaceContentBetweenTags(seedInput));
        }

        // execude again with dynamic mutators
        runCommand(builder, seedInput, getMutatedInputs(seedInput, dynamicMutators));
    }

    private static ProcessBuilder getProcessBuilderForCommand(String command, String workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder();
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("sh", "-c", command);
        }
        builder.directory(new File(workingDirectory));
        builder.redirectErrorStream(true); // redirect stderr to stdout
        return builder;
    }

    private static void runCommand(ProcessBuilder builder, String seedInput, List<String> mutatedInputs) {
        Stream.concat(Stream.of(seedInput), mutatedInputs.stream()).forEach(
                input -> { 
                    try {
                        Process process = builder.start();

                        try (OutputStream stdin = process.getOutputStream()) {
                            stdin.write(input.getBytes());
                            stdin.flush();
                        }

                        int exitCode = process.waitFor();
                        System.err.println("Process exited with code: " + exitCode);

                        String output = readStreamIntoString(process.getInputStream());
                        System.out.println("Input: " + input);
                        System.out.println("Output: " + output);

                        if (exitCode != 0) {
                            System.err.printf("Error: Command exited with non-zero exit code");
                            System.exit(1);
                        }
                    } catch (IOException | InterruptedException e) {
                        System.err.println("Error while executing command: " + e.getMessage());
                        System.exit(1);
                    }
                }
        );
    }

    private static String readStreamIntoString(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines()
                .map(line -> line + System.lineSeparator())
                .collect(Collectors.joining());
    }

    private static List<String> getMutatedInputs(String seedInput, Collection<Function<String, String>> mutators) {
        return mutators.stream()
                .map(mutator -> mutator.apply(seedInput))
                .collect(Collectors.toList());
    }

    private static String insertRandomCharacter(String input) {
        if (input == null || input.isEmpty()) return input;
        int position = (int) (Math.random() * input.length());
        char randomChar = (char) ('a' + Math.random() * 26); // Random letter
        return new StringBuilder(input).insert(position, randomChar).toString();
    }

    private static String removeRandomSubstring(String input) {
        if (input == null || input.length() < 2) return input;
        int start = (int) (Math.random() * input.length() / 2);
        int end = start + (int) (Math.random() * (input.length() - start));
        return new StringBuilder(input).delete(start, end).toString();
    }

    private static String replaceRandomTag(String input) {
        if (input == null) return input;

        if (Math.random() < 0.5) {
            // generate random string between 1 and 20 chars long
            int length = (int) (Math.random() * 20) + 1;
            StringBuilder randomString = new StringBuilder();
            for (int i = 0; i < length; i++) {
                char randomChar = (char) ('a' + (int) (Math.random() * 26));
                randomString.append(randomChar);
            }
    
            // replace first tag with random string
            return input.replaceFirst("<\\w+", "<" + randomString.toString());
        }

        List<String> newTags = List.of(
        "div", "header", "footer", // common Tags
        "xyz123", "di v", "<div>>", // malformed Tags
        "<html", "html>", "<", ">"  // double angle brakets and empty tag
);
        int index = (int) (Math.random() * 10);
        return input.replaceFirst("<\\w+", "<" + newTags.get(index)); // change first html-Tag
    }

    private static String randomizeCase(String input) {
        if (input == null) return input;
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isLetter(c)) {
                sb.append(Math.random() > 0.5 ? Character.toUpperCase(c) : Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String changeAttributeValueLengthRandomly(String input, String attributeName) {
        // check seed input for attribute
        if (!input.contains(attributeName + "=\"")) {
            return input;
        }
    
        int newLength = (int) (Math.random() * 20) + 1; // random length between 1 and 20
        StringBuilder newValue = new StringBuilder();
    
        // generate random letters
        for (int i = 0; i < newLength; i++) {
            char randomChar = (char) ('a' + (int) (Math.random() * 26)); // Zufälliger Buchstabe (a-z)
            newValue.append(randomChar);
        }

        // Change attribute value
        return input.replaceAll(attributeName + "=\"[^\"]*\"", attributeName + "=\"" + newValue + "\"");
    }

    private static String replaceContentBetweenTags(String input) {
        // check for content between html-Tags
        if (!input.matches(".*>.*<.*")) {
            return input; 
        }
    
        // random length for new content between 1 and 100
        int newLength = (int) (Math.random() * 100) + 1;
    
        // generate random string
        StringBuilder randomContent = new StringBuilder();
        for (int i = 0; i < newLength; i++) {
            char randomChar = (char) ('a' + (int) (Math.random() * 26)); // random letter
            randomContent.append(randomChar);
        }
    
        // replace content between tags
        return input.replaceAll(">(.*?)<", ">" + randomContent + "<");
    }
}

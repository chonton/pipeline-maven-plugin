package org.honton.chas.compose.maven.plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import org.apache.maven.plugin.MojoExecutionException;

class StepProcess {
  @Getter private final ProcessBuilder processBuilder;
  private final List<Integer> successCodes;
  private final Path dir;
  private final Step step;
  private final String name;
  @Setter private Process process;

  StepProcess(Path workDir, Step step) throws IOException, MojoExecutionException {
    successCodes = parseSuccessCodes(step.getSuccess());
    dir = setWorkingDir(workDir, step.getDir());
    this.step = step;
    processBuilder = createProcessBuilder(step);
    name = getName(step);
  }

  private static Path setWorkingDir(Path buildPath, String dir) throws IOException {
    if (dir != null && !dir.isEmpty()) {
      buildPath = buildPath.resolve(dir);
    }
    Files.createDirectories(buildPath);
    return buildPath;
  }

  private static List<Integer> parseSuccessCodes(String success) {
    List<Integer> codes = new ArrayList<>();
    if (success != null) {
      for (String part : success.split("[\\s,]+")) {
        if (!part.isEmpty()) {
          codes.add(Integer.parseInt(part));
        }
      }
    }
    return codes;
  }

  private static String trimCmd(String cmd) throws MojoExecutionException {
    if (cmd != null) {
      cmd = cmd.trim();
      if (!cmd.isEmpty()) {
        return cmd;
      }
    }
    throw new MojoExecutionException("cmd is required");
  }

  private ProcessBuilder createProcessBuilder(Step step) throws MojoExecutionException {
    List<String> command = new ArrayList<>();
    command.add(trimCmd(step.getCmd()));
    List<String> args = step.getArgs();
    if (args != null) {
      command.addAll(args);
    }

    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(dir.toFile());
    Map<String, String> env = step.getEnv();
    if (env != null) {
      builder.environment().putAll(env);
    }
    return builder;
  }

  private String getName(Step step) {
    String putative = step.getName();
    if (putative != null) {
      putative = putative.trim();
      if (!putative.isEmpty()) {
        return putative;
      }
    }
    return processBuilder.command().get(0);
  }

  void plumbStdIn(String stdIn) throws MojoExecutionException {
    if (stdIn != null && !stdIn.isEmpty()) {
      Path inputPath = dir.resolve(stdIn);
      if (!Files.exists(inputPath)) {
        throw new MojoExecutionException("Input file does not exist: " + inputPath);
      }
      processBuilder.redirectInput(inputPath.toFile());
    }
  }

  void plumbStdOut(String stdOut) throws IOException {
    if (stdOut != null && !stdOut.isEmpty()) {
      Path outputPath = dir.resolve(stdOut);
      Files.createDirectories(outputPath.getParent());
      processBuilder.redirectOutput(outputPath.toFile());
    }
  }

  void plumbStdErr(boolean isTTY) throws IOException {
    String stdErr = step.getStdErr();
    if (stdErr != null && !stdErr.isEmpty()) {
      Path outputPath = dir.resolve(stdErr);
      Files.createDirectories(outputPath.getParent());
      processBuilder.redirectError(outputPath.toFile());
    } else if (isTTY) {
      processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
    }
  }

  void closeStdIn() throws IOException {
    OutputStream outputStream = process.getOutputStream();
    if (outputStream != null) {
      outputStream.close();
    }
  }

  void pumpStdErr(Consumer<String> consumer) {
    new Pump(process.errorReader(), name, consumer);
  }

  public void pumpStdOut(Consumer<String> consumer) {
    new Pump(process.inputReader(), name, consumer);
  }

  public void waitForProcess(long deadline) throws MojoExecutionException, InterruptedException {
    long waitTime = deadline - System.currentTimeMillis();
    if (waitTime < 0 || !process.waitFor(waitTime, TimeUnit.MILLISECONDS)) {
      throw new MojoExecutionException("Pipeline step " + name + " exceeded wait time");
    }
    if (successCodes.isEmpty()) {
      return;
    }
    int exitCode = process.exitValue();
    if (successCodes.contains(exitCode)) {
      return;
    }
    throw new MojoExecutionException("Pipeline step " + name + " exited with code " + exitCode);
  }
}

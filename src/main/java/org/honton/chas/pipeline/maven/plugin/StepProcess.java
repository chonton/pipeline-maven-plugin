package org.honton.chas.pipeline.maven.plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.Setter;
import org.apache.maven.plugin.MojoExecutionException;

class StepProcess {

  private static final String DOES_NOT_MATCH_SUCCESS_CODES = " - does not match success codes";

  @Getter private final ProcessBuilder processBuilder;
  private final List<Integer> successCodes;
  private final LogLevel exitLevel;
  private final EndAction endAction;

  private final LogLevel stdErrLevel;
  private final Path dir;
  private final Step step;
  private final String name;
  Path outputPath;
  @Setter private Process process;

  StepProcess(Path workDir, Step step) throws IOException, MojoExecutionException {
    successCodes = parseSuccessCodes(step.getSuccess());
    this.exitLevel = parseEnum(step.getExitLogLevel(), LogLevel::values, LogLevel.INFO);
    this.stdErrLevel = parseEnum(step.getStdErrLevel(), LogLevel::values, null);
    this.endAction = parseEnum(step.getEndAction(), EndAction::values, EndAction.LOG_MISMATCH);

    dir = setWorkingDir(workDir, step.getDir());
    this.step = step;
    processBuilder = createProcessBuilder(step);
    name = getName(step);
  }

  private static <T extends Enum<T>> T parseEnum(
      String givenValue, Supplier<T[]> values, T defaultValue) throws MojoExecutionException {
    if (givenValue != null) {
      String match = givenValue.trim();
      if (!match.isEmpty()) {
        for (T enumValue : values.get()) {
          if (enumValue.name().equalsIgnoreCase(match)) {
            return enumValue;
          }
        }
        throw new MojoExecutionException("Invalid value " + givenValue);
      }
    }
    return defaultValue;
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
      outputPath = dir.resolve(stdErr);
      Files.createDirectories(outputPath.getParent());
      if (stdErrLevel == null) {
        processBuilder.redirectError(outputPath.toFile());
      }
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

  void pumpStdErr(BiConsumer<LogLevel, CharSequence> logger) {
    new Pump(process.errorReader(), name, outputPath, logger, stdErrLevel);
  }

  public void pumpStdOut(BiConsumer<LogLevel, CharSequence> logger) {
    new Pump(process.inputReader(), name, null, logger, LogLevel.INFO);
  }

  public void waitForProcess(long deadline, BiConsumer<LogLevel, CharSequence> logger)
      throws MojoExecutionException, InterruptedException {
    long waitTime = deadline - System.currentTimeMillis();
    if (waitTime < 0 || !process.waitFor(waitTime, TimeUnit.MILLISECONDS)) {
      throw new MojoExecutionException("Pipeline step " + name + " exceeded wait time");
    }

    int exitCode = process.exitValue();
    boolean misMatch = !successCodes.isEmpty() && !successCodes.contains(exitCode);
    switch (endAction) {
      case EXIT_MISMATCH:
        {
          if (misMatch) {
            throw new MojoExecutionException(
                exitCodeMessage(exitCode).append(DOES_NOT_MATCH_SUCCESS_CODES).toString());
          }
          break;
        }
      case LOG_ALWAYS:
        {
          logger.accept(exitLevel, exitCodeMessage(exitCode));
          break;
        }
      default:
        if (misMatch) {
          logger.accept(exitLevel, exitCodeMessage(exitCode).append(DOES_NOT_MATCH_SUCCESS_CODES));
        }
    }
  }

  private StringBuilder exitCodeMessage(int exitCode) {
    return new StringBuilder("step: ").append(name).append(", exitCode: ").append(exitCode);
  }
}

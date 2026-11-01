package org.honton.chas.pipeline.maven.plugin;

import java.io.BufferedReader;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;

class Pump {
  private final BufferedReader bufferedReader;
  private final String name;
  private final BiConsumer<LogLevel, CharSequence> logger;

  Pump(BufferedReader bufferedReader, String name, BiConsumer<LogLevel, CharSequence> logger) {
    this.bufferedReader = bufferedReader;
    this.name = name;
    this.logger = logger;
    ForkJoinPool.commonPool().execute(this::startPumpThread);
  }

  private void startPumpThread() {
    try {
      for (; ; ) {
        String line = bufferedReader.readLine();
        if (line == null) {
          return;
        }
        if (!line.isEmpty()) {
          logger.accept(LogLevel.INFO, name + ' ' + line);
        }
      }
    } catch (Exception e) {
      throw new UndeclaredThrowableException(e);
    }
  }
}

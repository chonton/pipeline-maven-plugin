package org.honton.chas.pipeline.maven.plugin;

import java.io.BufferedReader;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

class Pump {
  private final BufferedReader bufferedReader;
  private final String name;
  private final Consumer<String> consumer;

  Pump(BufferedReader bufferedReader, String name, Consumer<String> consumer) {
    this.bufferedReader = bufferedReader;
    this.name = name;
    this.consumer = consumer;
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
          consumer.accept(name + ' ' + line);
        }
      }
    } catch (Exception e) {
      throw new UndeclaredThrowableException(e);
    }
  }
}

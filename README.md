# pipeline-maven-plugin

Use pipeline to run multiple process pipeline from maven. This plugin has single goal:

- [Run](https://chonton.github.io/pipeline-maven-plugin/run-mojo.html) pipeline

# Rationale

Run complex piped commands without a shell. Each step in pipeline is executing an os-level program. The output of a
stage is piped into the input of the next stage. The input and output of the entire pipeline can be

# Plugin

Plugin reports are available at [plugin info](https://chonton.github.io/pipeline-maven-plugin/plugin-info.html).

## Run Configuration

| Parameter | Default            | Property      | Description                                                   |
|----------:|:-------------------|:--------------|:--------------------------------------------------------------|
|       dir | ${project.baseDir} |               | Working directory of pipeline, relative to ${project.basedir} |
|      skip | false              | pipeline.skip | Skip execution                                                |
|     stdIn | /dev/null          |               | Existing input file for first step                            |
|    stdOut | maven logger       |               | Output file for last step                                     |
|     steps |                    |               | Non-empty list of Step                                        |
|      wait | 5m                 | pipeline.wait | Duration* to wait for completion of pipeline                  |

* Duration is positive integer suffixed with single character unit. Unit can be 'h' (hours), 'm' (minutes), or  's'
  (seconds).

## Step Attributes

| Parameter | Type               | Description                                                                                                    |
|----------:|:-------------------|:---------------------------------------------------------------------------------------------------------------|
|      name | optional String    | name of step; defaults to cmd                                                                                  |
|       cmd | required String    | program to run                                                                                                 |
|      args | List<String>       | list of program arguments                                                                                      |
|       env | Map<String,String> | environment variables for program                                                                              |
|       dir | optional String    | working directory of step, relative to pipeline working directory; defaults to pipeline working directory      |
|    stdErr | optional String    | file to receive error output; defaults to maven logger                                                         |
|   success | optional String    | Comma separated list of positive integers; defaults to all exit codes sucessful                                |
| endAction | optional String    | Action to take on command completion. One of LOG_MISMATCH, LOG_ALWAYS, EXIT_MISMATCH; defaults to LOG_MISMATCH |
|  logLevel | optional String    | Level to log exit code; one of ERROR, WARN, INFO, DEBUG; defaults to INFO                                      |

Successful run of pipeline occurs if:
- wait duration is not exceeded
- for each step: endAction not equals EXIT_MISMATCH or exit code of the command matches success codes

# Example

## Typical Use

```xml

<build>
  <pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.honton.chas</groupId>
        <artifactId>pipeline-maven-plugin</artifactId>
        <version>1.0.0</version>
      </plugin>
    </plugins>
  </pluginManagement>
  <plugins>
    <plugin>
      <groupId>org.honton.chas</groupId>
      <artifactId>pipeline-maven-plugin</artifactId>
      <executions>
        <execution>
          <goals>
            <goal>run</goal>
          </goals>
          <configuration>
            <dir>snyk-jq</dir>
            <steps>
              <step>
                <cmd>snyk</cmd>
                <args>
                  <arg>test</arg>
                  <arg>--maven-aggregate-project</arg>
                  <arg>--severity-threshold=high</arg>
                  <arg>--json</arg>
                </args>
                <logLevel>INFO</logLevel>
              </step>
              <step>
                <cmd>jq</cmd>
                <args>
                  <arg>map({ (.projectName): .vulnerabilities | map(select( .socialTrendAlert )) | map({(.id):
                    {severity,exploit,socialTrendAlert} }) | add }) | add | del(.. | nulls) | stderr
                  </arg>
                </args>
                <success>0</success>
                <logLevel>INFO</logLevel>
                <endAction>EXIT_MISMATCH</endAction>
              </step>
              <step>
                <cmd>jq</cmd>
                <name>check empty</name>
                <args>
                  <arg>-e</arg>
                  <arg>length==0</arg>
                </args>
                <success>0</success>
                <endAction>EXIT_MISMATCH</endAction>
              </step>
            </steps>
            <wait>5m</wait>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```
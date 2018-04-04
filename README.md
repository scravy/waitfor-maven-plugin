# waitfor-maven-plugin [![Build Status](https://travis-ci.org/simplaex/waitfor-maven-plugin.svg?branch=master)](https://travis-ci.org/simplaex/waitfor-maven-plugin)

Maven Coordinates:
    
    <groupId>com.simplaex</groupId>
    <artifactId>waitfor-maven-plugin</artifactId>
    <version>1.0</version>

## Minimal Configuration Example

      <plugin>
        <groupId>com.simplaex</groupId>
        <artifactId>waitfor-maven-plugin</artifactId>
        <version>1.0-SNAPSHOT</version>
        <executions>
          <execution>
            <id>wait-for-environment-to-be-up</id>
            <phase>pre-integration-test</phase>
            <goals>
              <goal>waitfor</goal>
            </goals>
            <configuration>
              <checks>
                <check>
                  <url>http://localhost:8080/health</url>
                </check>
              </checks>
            </configuration>
          </execution>
        </executions>
      </plugin>

## Full Configuration Example

      <plugin>
        <groupId>com.simplaex</groupId>
        <artifactId>waitfor-maven-plugin</artifactId>
        <version>1.0</version>
        <executions>
          <execution>
            <id>wait-for-environment-to-be-up</id>
            <phase>pre-integration-test</phase>
            <goals>
              <goal>waitfor</goal>
            </goals>
            <configuration>
              <chatty>false</chatty><!-- this is the default -->
              <quiet>false</quiet><!-- this is the default -->
              <timeoutSeconds>30</timeoutSeconds><!-- this is the default -->
              <checkEveryMillis>500</checkEveryMillis><!-- this is the default -->
              <checks>
                <check>
                  <url>http://localhost:9090/health</url>
                  <method>GET</method><!-- this is the default -->
                  <statusCode>200</statusCode><!-- this is the default -->
                  <headers>
                    <header>
                      <name>Authorization</name>
                      <value>Bearer SOMETOKEN</value>
                    </header>
                  </headers>
                </check>
                <check>
                  <url>http://localhost:9090/resource</url>
                  <method>POST</method>
                  <statusCode>201</statusCode>
                  <body>
                  {
                    "some": "thing"
                  }
                  </body>
                  <headers>
                    <header>
                      <name>Content-Type</name>
                      <value>application/json</value>
                    </header>
                  </headers>
                </check>
              </checks>
            </configuration>
          </execution>
        </executions>
      </plugin>

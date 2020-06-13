!!! note
    This module is INCUBATING. While it is ready for use and operational in the current version of Testcontainers, it is possible that it may receive breaking changes in the future. See [our contributing guidelines](/contributing/#incubating-modules) for more information on our incubating modules policy.

# ONgDB Module

This module helps running [ONgDB](https://graphfoundation.org/projects/ongdb/) using Testcontainers.

Note that it's based on the [official Docker image](https://hub.docker.com/r/graphfoundation/ongdb) provided by Graph Foundation, Inc.

## Usage example

Declare your Testcontainer as a `@ClassRule` or `@Rule` in a JUnit 4 test or as static or member attribute of a JUnit 5 test annotated with `@Container` as you would with other Testcontainers.
You can either use call `getHttpUrl()` or `getBoltUrl()` on the ONgDB container.
`getHttpUrl()` gives you the HTTP-address of the transactional HTTP endpoint while `getBoltUrl()` is meant to be used with one of the [official Bolt drivers](https://neo4j.com/developer/language-guides/).
On the JVM you would most likely use the [Java driver](https://github.com/neo4j/neo4j-java-driver).

The following example uses the JUnit 5 extension `@Testcontainers` and demonstrates both the usage of the Java Driver and the REST endpoint:

```java tab="JUnit 5 example"
@Testcontainers
public class ExampleTest {

    @Container
    private static OngdbContainer ongdbContainer = new OngdbContainer()
        .withAdminPassword(null); // Disable password

    @Test
    void testSomethingUsingBolt() {

        // Retrieve the Bolt URL from the container
        String boltUrl = ongdbContainer.getBoltUrl();
        try (
            Driver driver = GraphDatabase.driver(boltUrl, AuthTokens.none());
            Session session = driver.session()
        ) {
            long one = session.run("RETURN 1", Collections.emptyMap()).next().get(0).asLong();
            assertThat(one, is(1L));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void testSomethingUsingHttp() throws IOException {

        // Retrieve the HTTP URL from the container
        String httpUrl = ongdbContainer.getHttpUrl();

        URL url = new URL(httpUrl + "/db/data/transaction/commit");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        try (Writer out = new OutputStreamWriter(con.getOutputStream())) {
            out.write("{\"statements\":[{\"statement\":\"RETURN 1\"}]}");
            out.flush();
        }

        assertThat(con.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String expectedResponse = 
                "{\"results\":[{\"columns\":[\"1\"],\"data\":[{\"row\":[1],\"meta\":[null]}]}],\"errors\":[]}";
            String response = buffer.lines().collect(Collectors.joining("\n"));
            assertThat(response, is(expectedResponse));
        }
    }
}
```

You are not limited to Unit tests and can of course use an instance of the ONgDB Testcontainer in vanilla Java code as well.

## Additional features

### Disable authentication

Authentication can be disabled:

```java
@Testcontainers
public class ExampleTest {

    @Container
    OngdbContainer ongdbContainer = new OngdbContainer()
        .withoutAuthentication();
}
```

### ONgDB-Configuration

ONgDB's Docker image needs ONgDB configuration options in a dedicated format.
The container takes care of that. You can configure the database with standard options like the following:

```java
@Testcontainers
public class ExampleTest {

    @Container
    OngdbContainer ongdbContainer = new OngdbContainer()
        .withOngdbConfig("dbms.security.procedures.unrestricted", "apoc.*,algo.*");
}
```

### Add custom plugins

Custom plugins, like APOC, can be copied over to the container from any classpath or host resource like this:

```java
@Testcontainers
public class ExampleTest {

    @Container
    OngdbContainer ongdbContainer = new OngdbContainer()
        .withPlugins(MountableFile.forClasspathResource("/apoc-3.6.0.0-all.jar"));
}
```

Whole directories work as well:

```java
@Testcontainers
public class ExampleTest {

    @Container
    OngdbContainer ongdbContainer = new OngdbContainer()
        .withPlugins(MountableFile.forClasspathResource("/my-plugins"));
}
```

### Start the container with a predefined database

If you have an existing database (`graph.db`) you want to work with, copy it over to the container like this:

```java
@Testcontainers
public class ExampleTest {

    @Container
    OngdbContainer ongdbContainer = new OngdbContainer()
        .withDatabase(MountableFile.forClasspathResource("/test-graph.db"));
}
```

## Adding this module to your project dependencies

Add the following dependency to your `pom.xml`/`build.gradle` file:

```groovy tab='Gradle'
testCompile "org.testcontainers:ongdb:{{latest_version}}"
```

```xml tab='Maven'
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>ongdb</artifactId>
    <version>{{latest_version}}</version>
    <scope>test</scope>
</dependency>
```

!!! hint
    Add the Neo4j Java driver if you plan to access the Testcontainer via Bolt:
    
    ```groovy tab='Gradle'
    compile "org.neo4j.driver:neo4j-java-driver:1.7.1"
    ```
    
    ```xml tab='Maven'
    <dependency>
        <groupId>org.neo4j.driver</groupId>
        <artifactId>neo4j-java-driver</artifactId>
        <version>1.7.1</version>
    </dependency>
    ```
    



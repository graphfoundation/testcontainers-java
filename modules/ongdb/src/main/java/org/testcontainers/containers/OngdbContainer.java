package org.testcontainers.containers;

import static java.net.HttpURLConnection.*;
import static java.util.stream.Collectors.*;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Stream;

import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.MountableFile;

/**
 * Testcontainer for ONgDB.
 *
 * @param <S> "SELF" to be used in the <code>withXXX</code> methods.
 * @author bradnussbaum
 */
public class OngdbContainer<S extends OngdbContainer<S>> extends GenericContainer<S> {

    /**
     * The image defaults to the official ONgDB image: <a href="https://graphfoundation.org/projects/ongdb/">ONgDB</a>.
     */
    private static final String DEFAULT_IMAGE_NAME = "graphfoundation/ongdb";

    /**
     * The default tag (version) to use.
     */
    private static final String DEFAULT_TAG = "3.6.0";

    private static final String DOCKER_IMAGE_NAME = DEFAULT_IMAGE_NAME + ":" + DEFAULT_TAG;

    /**
     * Default port for the binary Bolt protocol.
     */
    private static final int DEFAULT_BOLT_PORT = 7687;

    /**
     * The port of the transactional HTTPS endpoint: <a href="https://neo4j.com/docs/rest-docs/current/">Neo4j REST API</a>.
     */
    private static final int DEFAULT_HTTPS_PORT = 7473;

    /**
     * The port of the transactional HTTP endpoint: <a href="https://neo4j.com/docs/rest-docs/current/">Neo4j REST API</a>.
     */
    private static final int DEFAULT_HTTP_PORT = 7474;

    /**
     * The official image requires a change of password by default from "neo4j" to something else. This defaults to "password".
     */
    private static final String DEFAULT_ADMIN_PASSWORD = "password";

    private static final String AUTH_FORMAT = "neo4j/%s";

    private String adminPassword = DEFAULT_ADMIN_PASSWORD;

    private boolean defaultImage = false;

    /**
     * Creates a Testcontainer using the official ONgDB docker image.
     */
    public OngdbContainer() {
        this(DOCKER_IMAGE_NAME);

        this.defaultImage = true;
    }

    /**
     * Creates a Testcontainer using a specific docker image.
     *
     * @param dockerImageName The docker image to use.
     */
    public OngdbContainer(String dockerImageName) {
        super(dockerImageName);

        WaitStrategy waitForBolt = new LogMessageWaitStrategy()
            .withRegEx(String.format(".*Bolt enabled on 0\\.0\\.0\\.0:%d\\.\n", DEFAULT_BOLT_PORT));
        WaitStrategy waitForHttp = new HttpWaitStrategy()
            .forPort(DEFAULT_HTTP_PORT)
            .forStatusCodeMatching(response -> response == HTTP_OK);

        this.waitStrategy = new WaitAllStrategy()
            .withStrategy(waitForBolt)
            .withStrategy(waitForHttp)
            .withStartupTimeout(Duration.ofMinutes(2));

        addExposedPorts(DEFAULT_BOLT_PORT, DEFAULT_HTTP_PORT, DEFAULT_HTTPS_PORT);
    }

    @Override
    public Set<Integer> getLivenessCheckPortNumbers() {

        return Stream.of(DEFAULT_BOLT_PORT, DEFAULT_HTTP_PORT, DEFAULT_HTTPS_PORT)
            .map(this::getMappedPort)
            .collect(toSet());
    }

    @Override
    protected void configure() {

        boolean emptyAdminPassword = this.adminPassword == null || this.adminPassword.isEmpty();
        String neo4jAuth = emptyAdminPassword ? "none" : String.format(AUTH_FORMAT, this.adminPassword);
        addEnv("NEO4J_AUTH", neo4jAuth);
    }

    /**
     * @return Bolt URL for use with Neo4j's Java-Driver.
     */
    public String getBoltUrl() {
        return String.format("bolt://" + getHost() + ":" + getMappedPort(DEFAULT_BOLT_PORT));
    }

    /**
     * @return URL of the transactional HTTP endpoint.
     */
    public String getHttpUrl() {
        return String.format("http://" + getHost() + ":" + getMappedPort(DEFAULT_HTTP_PORT));
    }

    /**
     * @return URL of the transactional HTTPS endpoint.
     */
    public String getHttpsUrl() {
        return String.format("https://" + getHost() + ":" + getMappedPort(DEFAULT_HTTPS_PORT));
    }

    /**
     * Sets the admin password for the default account (which is <pre>neo4j</pre>). A null value or an empty string
     * disables authentication.
     *
     * @param adminPassword The admin password for the default database account.
     * @return This container.
     */
    public S withAdminPassword(final String adminPassword) {

        this.adminPassword = adminPassword;
        return self();
    }

    /**
     * Disables authentication.
     *
     * @return This container.
     */
    public S withoutAuthentication() {
        return withAdminPassword(null);
    }

    /**
     * Copies an existing {@code graph.db} folder into the container. This can either be a classpath resource or a
     * host resource. Please have a look at the factory methods in {@link MountableFile}.
     * <br>
     * If you want to map your database into the container instead of copying them, please use {@code #withClasspathResourceMapping},
     * but this will only work when your test does not run in a container itself.
     * <br>
     * Mapping would work like this:
     * <pre>
     *      &#64;Container
     *      private static final OngdbContainer databaseServer = new OngdbContainer&lt;&gt;()
     *          .withClasspathResourceMapping("/test-graph.db", "/data/databases/graph.db", BindMode.READ_WRITE);
     * </pre>
     *
     * @param graphDb The graph.db folder to copy into the container
     * @return This container.
     */
    public S withDatabase(MountableFile graphDb) {
        return withCopyFileToContainer(graphDb, "/data/databases/graph.db");
    }

    /**
     * Adds plugins to the given directory to the container. If {@code plugins} denotes a directory, than all of that
     * directory is mapped to ONgDB's plugins. Otherwise, single resources are copied over.
     * <br>
     * If you want to map your plugins into the container instead of copying them, please use {@code #withClasspathResourceMapping},
     * but this will only work when your test does not run in a container itself.
     *
     * @param plugins The plugins folder to copy into the container
     * @return This container.
     */
    public S withPlugins(MountableFile plugins) {
        return withCopyFileToContainer(plugins, "/var/lib/neo4j/plugins/");
    }

    /**
     * Adds ONgDB configuration properties to the container. The properties can be added as in the official ONgDB
     * configuration, the method automatically translate them into the format required by the ONgDB container.
     *
     * @param key   The key to configure, i.e. {@code dbms.security.procedures.unrestricted}
     * @param value The value to set
     * @return This container.
     */
    public S withConfig(String key, String value) {

        addEnv(formatConfigurationKey(key), value);
        return self();
    }

    /**
     * @return The admin password for the <code>neo4j</code> account or literal <code>null</code> if auth is disabled.
     */
    public String getAdminPassword() {
        return adminPassword;
    }

    private static String formatConfigurationKey(String plainConfigKey) {
        final String prefix = "NEO4J_";

        return String.format("%s%s", prefix, plainConfigKey
            .replaceAll("_", "__")
            .replaceAll("\\.", "_"));
    }
}
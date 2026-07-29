package pokemmo;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class Client {
    private static final Logger LOGGER = createLogger();

    private final List<ClientPlugin> plugins = new ArrayList<ClientPlugin>();
    private final File gamePath;

    private URLClassLoader injectionLoader;
    private File injectedJar;

    public Client(File gamePath) {
        this.gamePath = gamePath != null ? gamePath : defaultGamePath();
    }

    private static Logger createLogger() {
        Logger logger = Logger.getLogger("pokelite.client");
        logger.setUseParentHandlers(false);

        if (logger.getHandlers().length == 0) {
            Handler handler = new ConsoleHandler();
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);
        }

        logger.setLevel(Level.INFO);
        return logger;
    }

    private static File defaultGamePath() {
        File root = new File("").getAbsoluteFile();
        File windowsExecutable = new File(root, "PokeMMO.exe");

        if (windowsExecutable.isFile()) {
            return windowsExecutable;
        }

        return new File(root, "PokeMMO.sh");
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public void loadPlugins(File directory) {
        File pluginDirectory = directory != null ? directory : new File("plugins");

        if (!pluginDirectory.isDirectory()) {
            LOGGER.fine("Plugin directory does not exist: " + pluginDirectory);
            return;
        }

        ClassLoader classLoader = injectionLoader;
        if (classLoader == null) {
            try {
                classLoader = new URLClassLoader(
                    new URL[] { pluginDirectory.toURI().toURL() },
                    Client.class.getClassLoader()
                );
            } catch (MalformedURLException exception) {
                LOGGER.log(Level.WARNING, "Failed to create plugin class loader", exception);
                return;
            }
        }

        List<File> classFiles = new ArrayList<File>();
        collectClassFiles(pluginDirectory, classFiles);

        for (File classFile : classFiles) {
            String relativePath = pluginDirectory.toURI().relativize(classFile.toURI()).getPath();
            String className = relativePath
                .replace('/', '.')
                .replaceAll("\\.class$", "");

            try {
                Class<?> pluginClass = Class.forName(className, true, classLoader);
                if (!ClientPlugin.class.isAssignableFrom(pluginClass)) {
                    continue;
                }

                ClientPlugin plugin = (ClientPlugin) pluginClass.getDeclaredConstructor().newInstance();
                plugins.add(plugin);
                LOGGER.info("Loaded plugin: " + className);
            } catch (ReflectiveOperationException | LinkageError exception) {
                LOGGER.log(Level.WARNING, "Failed to load plugin: " + className, exception);
            }
        }
    }

    private static void collectClassFiles(File currentDirectory, List<File> output) {
        File[] children = currentDirectory.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                collectClassFiles(child, output);
            } else if (child.getName().endsWith(".class")) {
                output.add(child);
            }
        }
    }

    public void prepareInjection(File pluginDirectory) {
        File gameJar = findGameJar();
        if (gameJar == null) {
            LOGGER.fine("No candidate game JAR found; external launch will be used");
            return;
        }

        List<URL> urls = new ArrayList<URL>();

        try {
            urls.add(gameJar.toURI().toURL());

            File directory = pluginDirectory != null ? pluginDirectory : new File("plugins");
            if (directory.isDirectory()) {
                urls.add(directory.toURI().toURL());
            }

            injectionLoader = new URLClassLoader(
                urls.toArray(new URL[0]),
                Client.class.getClassLoader()
            );
            injectedJar = gameJar;
        } catch (MalformedURLException exception) {
            LOGGER.log(Level.WARNING, "Failed to prepare experimental client loading", exception);
        }
    }

    private File findGameJar() {
        File parentDirectory = gamePath.getAbsoluteFile().getParentFile();
        File gameJar = searchJar(parentDirectory);

        if (gameJar == null && parentDirectory != null) {
            gameJar = searchJar(new File(parentDirectory, "libs"));
        }

        return gameJar;
    }

    private static File searchJar(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isFile()
                && file.getName().endsWith(".jar")
                && !"jrt-fs.jar".equals(file.getName())) {
                return file;
            }
        }

        return null;
    }

    public void run() throws IOException {
        if (injectionLoader != null && injectedJar != null) {
            try {
                runFromJar();
                return;
            } catch (Exception exception) {
                LOGGER.log(
                    Level.WARNING,
                    "Experimental same-JVM loading failed; falling back to the official launcher",
                    exception
                );
            }
        }

        launchExternalClient();
    }

    private void runFromJar() throws Exception {
        LOGGER.info("Attempting experimental same-JVM launch from: " + injectedJar);
        Thread.currentThread().setContextClassLoader(injectionLoader);

        try (JarFile jarFile = new JarFile(injectedJar)) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                throw new IllegalStateException("JAR manifest is missing");
            }

            String mainClassName = manifest.getMainAttributes().getValue("Main-Class");
            if (mainClassName == null || mainClassName.isBlank()) {
                throw new IllegalStateException("Main-Class is missing from the JAR manifest");
            }

            Class<?> mainClass = Class.forName(mainClassName, true, injectionLoader);
            Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) new String[0]);
        }
    }

    private void launchExternalClient() throws IOException {
        if (!gamePath.isFile()) {
            throw new IOException("PokeMMO launcher was not found: " + gamePath.getAbsolutePath());
        }

        LOGGER.info("Launching official PokeMMO client from: " + gamePath);

        ProcessBuilder processBuilder;
        if (gamePath.getName().endsWith(".sh")) {
            processBuilder = new ProcessBuilder("bash", gamePath.getAbsolutePath());
        } else {
            processBuilder = new ProcessBuilder(gamePath.getAbsolutePath());
        }

        processBuilder.directory(gamePath.getAbsoluteFile().getParentFile());
        processBuilder.start();
    }

    private void runPlugins() {
        for (ClientPlugin plugin : plugins) {
            try {
                plugin.run(this);
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "Plugin execution failed", exception);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Client client = new Client(null);
        client.prepareInjection(null);
        client.loadPlugins(null);
        client.runPlugins();
        client.run();
    }
}

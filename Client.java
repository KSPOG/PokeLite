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

public class Client {
    private static final Logger LOGGER = createLogger();

    private static final String GAME_DIRECTORY_NAME = "poke";
    private static final String WINDOWS_CLIENT_NAME = "PokeMMO.exe";
    private static final String UNIX_LAUNCHER_NAME = "PokeMMO.sh";
    private static final String POKEMMO_MAIN_CLASS = "com.pokeemu.client.Client";

    private final List<ClientPlugin> plugins = new ArrayList<ClientPlugin>();
    private final File gamePath;

    private URLClassLoader injectionLoader;
    private File injectedArchive;

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
        File projectRoot = new File("").getAbsoluteFile();
        File gameDirectory = new File(projectRoot, GAME_DIRECTORY_NAME);
        File windowsExecutable = new File(gameDirectory, WINDOWS_CLIENT_NAME);
        File unixLauncher = new File(gameDirectory, UNIX_LAUNCHER_NAME);

        if (windowsExecutable.isFile()) {
            return windowsExecutable;
        }

        if (unixLauncher.isFile()) {
            return unixLauncher;
        }

        return isWindows() ? windowsExecutable : unixLauncher;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
            .toLowerCase()
            .contains("win");
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
        File gameArchive = findGameArchive();
        if (gameArchive == null) {
            LOGGER.fine("PokeMMO.exe was not found; external launch will be used");
            return;
        }

        List<URL> urls = new ArrayList<URL>();

        try {
            urls.add(gameArchive.toURI().toURL());

            File directory = pluginDirectory != null ? pluginDirectory : new File("plugins");
            if (directory.isDirectory()) {
                urls.add(directory.toURI().toURL());
            }

            injectionLoader = new URLClassLoader(
                urls.toArray(new URL[0]),
                Client.class.getClassLoader()
            );
            injectedArchive = gameArchive;
        } catch (MalformedURLException exception) {
            LOGGER.log(Level.WARNING, "Failed to prepare experimental client loading", exception);
        }
    }

    private File findGameArchive() {
        if (gamePath.isFile() && WINDOWS_CLIENT_NAME.equalsIgnoreCase(gamePath.getName())) {
            return gamePath;
        }

        File installationDirectory = gamePath.getAbsoluteFile().getParentFile();
        if (installationDirectory == null) {
            return null;
        }

        File siblingExecutable = new File(installationDirectory, WINDOWS_CLIENT_NAME);
        return siblingExecutable.isFile() ? siblingExecutable : null;
    }

    public void run() throws IOException {
        if (injectionLoader != null && injectedArchive != null) {
            try {
                runFromArchive();
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

    private void runFromArchive() throws Exception {
        LOGGER.info("Attempting experimental same-JVM launch from: " + injectedArchive);
        Thread.currentThread().setContextClassLoader(injectionLoader);

        Class<?> mainClass = Class.forName(POKEMMO_MAIN_CLASS, true, injectionLoader);
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) new String[0]);
    }

    private void launchExternalClient() throws IOException {
        if (!gamePath.isFile()) {
            throw new IOException(
                "PokeMMO launcher was not found: "
                    + gamePath.getAbsolutePath()
                    + ". Place the PokeMMO installation in the '"
                    + GAME_DIRECTORY_NAME
                    + "' directory."
            );
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

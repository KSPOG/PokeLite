package pokemmo;

/**
 * Minimal plugin contract used by the initial PokeLite prototype.
 */
public interface ClientPlugin {
    void run(Client client) throws Exception;
}

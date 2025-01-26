package example;

import com.fasterxml.jackson.databind.ObjectMapper;
import example.domain.Request;
import example.domain.Response;
import example.domain.game.Cave;
import example.domain.game.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Collectors;

public class Client {
    private static final String HOST = "35.208.184.138";
//    private static final String HOST = "localhost";
    private static final int PORT = 8080;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    public static void main(String[] args) throws IOException {
        final var credentials = objectMapper.readValue(Files.readAllBytes(Path.of("./credentials.json")), Credentials.class);
        new Client().startClient(credentials);
    }

    public void startClient(Credentials credentials) {
        try (final var socket = new Socket(HOST, PORT);
             final var is = socket.getInputStream();
             final var isr = new InputStreamReader(is);
             final var reader = new BufferedReader(isr);
             final var os = socket.getOutputStream();
             final var osr = new OutputStreamWriter(os);
             final var writer = new BufferedWriter(osr)) {
            logger.info("Connected to server at {}:{}", HOST, PORT);

            {
                final var json = objectMapper.writeValueAsString(new Request.Authorize(credentials.key()));
                writer.write(json);
                writer.newLine();
                writer.flush();
                logger.info("Sent command: {}", json);
            }

            Cave cave = null;
            Player player = null;
            Collection<Response.StateLocations.ItemLocation> itemLocations;
            Collection<Response.StateLocations.PlayerLocation> playerLocations;

            while (!Thread.currentThread().isInterrupted()) {
                final var line = reader.readLine();
                if (line == null) {
                    break;
                }

                final var response = objectMapper.readValue(line, Response.class);
                Player finalPlayer = player;
                switch (response) {
                    case Response.Authorized authorized -> {
                        player = authorized.humanPlayer();
                        logger.info("authorized: {}", authorized);
                    }
                    case Response.Unauthorized unauthorized -> {
                        logger.error("unauthorized: {}", unauthorized);
                        return;
                    }
                    case Response.StateCave stateCave -> {
                        cave = stateCave.cave();
                        logger.info("cave: {}", cave);
                    }
                    case Response.StateLocations stateLocations -> {
                        itemLocations = stateLocations.itemLocations();
                        playerLocations = stateLocations.playerLocations();
                        logger.info("gold: {}", stateLocations.gold());
                        logger.info("health: {}", stateLocations.health());
//                        logger.info("playerLocations: {}", playerLocations);

                        final var locationItems = itemLocations.stream()
                                .collect(Collectors.groupingBy(Response.StateLocations.ItemLocation::location));
                        final var locationPLayers = playerLocations.stream()
                                .filter(pl -> !pl.entity().equals(finalPlayer))
                                .collect(Collectors.groupingBy(Response.StateLocations.PlayerLocation::location));

                        final var dijkstra = new Dijkstra(cave, locationItems, locationPLayers);
                        final var start = playerLocations.stream().filter(pl -> pl.entity().equals(finalPlayer)).findAny().get().location();
                        logger.info("position: {}", start);
                        final var direction = dijkstra.find(start);
                        final var cmd = new Request.Command(direction);
                        final var cmdJson = objectMapper.writeValueAsString(cmd);
                        writer.write(cmdJson);
                        writer.newLine();
                        writer.flush();
                        logger.info("Sent command: {}", cmd);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error in client operation", e);
        } finally {
            logger.info("Client exiting");
        }
    }
}